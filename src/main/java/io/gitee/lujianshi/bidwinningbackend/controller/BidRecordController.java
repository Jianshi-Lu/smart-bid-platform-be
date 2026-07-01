package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标记录 Controller。8 端点（6 标准 CRUD + 1 按公告过滤 + 2 AI 风险分析）（V18 重命名自 TendererDetailController）。
 */
@RestController
public class BidRecordController {

    @Resource
    private BidRecordService service;

    @Resource
    private RiskAnalysisService riskAnalysisService;

    /**
     * 创建投标记录。Service 自动计算 {@code bidNo}（同公告+同投标人下递增）和 {@code bidAt}（当前时间）。
     *
     * @param request 创建请求（invitationId / bidderName / quotePrice / bidderContact 必填，credentials 可选）
     * @return 创建的实体（含自增 bidRecordId）
     */
    @PostMapping("/bid-record/add")
    public Result<BidRecord> addBidRecord(@Valid @RequestBody AddRequest request) {
        BidRecord entity = new BidRecord();
        entity.setInvitationId(request.getInvitationId());
        entity.setBidderName(request.getBidderName());
        entity.setQuotePrice(request.getQuotePrice());
        entity.setBidderContact(request.getBidderContact());
        entity.setCredentials(request.getCredentials());
        service.add(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 按 ID 查询投标记录。
     *
     * @param id 投标记录 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/bid-record/{id}")
    public Result<BidRecord> getBidRecord(@PathVariable Integer id) {
        BidRecord entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 部分更新投标记录（PATCH 语义，null 字段不修改）。
     * <p>不允许修改 {@code bidNo} / {@code bidAt}（由 Service 在 add 时自动算）。</p>
     *
     * @param id      投标记录 ID
     * @param request 更新请求（5 字段全可选）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/bid-record/{id}")
    public Result<Diff<BidRecord>> patchBidRecord(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        BidRecord before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        BidRecord after = new BidRecord();
        after.setBidRecordId(id);
        after.setBidderName(request.getBidderName());
        after.setQuotePrice(request.getQuotePrice());
        after.setBidderContact(request.getBidderContact());
        after.setCredentials(request.getCredentials());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按 ID 删除投标记录。
     *
     * @param id 投标记录 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/bid-record/{id}")
    public Result<Void> deleteBidRecord(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 分页查询投标记录列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 bid_record_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/bid-record")
    public Result<PageInfo<BidRecord>> listBidRecord(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "bid_record_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<BidRecord> list = service.listAll();
            PageInfo<BidRecord> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除投标记录。永远返回 200，data 为实际删除的实体列表。
     *
     * @param request 批量删除请求（ids 最多 500 条）
     * @return 实际删除的实体列表
     */
    @PostMapping("/bid-record/batch-delete")
    public Result<List<BidRecord>> batchDeleteBidRecord(@Valid @RequestBody BatchDeleteRequest request) {
        List<BidRecord> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * V18 新增：按招标公告 ID 查询所有投标记录（前端"查看投标人"场景）。
     *
     * @param invitationId 招标公告 ID
     * @return 该公告的所有投标记录（按 bidAt 升序），无记录时返回空列表
     */
    @GetMapping("/bid-record/by-invitation/{invitationId}")
    public Result<List<BidRecord>> listByInvitation(@PathVariable Integer invitationId) {
        return Result.success(service.listByInvitationId(invitationId), "查询成功");
    }

    /**
     * 触发 AI 风险分析。同步调用 DeepSeek，解析 JSON 后落库。
     *
     * @param id 投标记录 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     */
    @PostMapping("/bid-record/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.analyzeRisk(id), "分析完成");
    }

    /**
     * 查询指定投标记录的历史风险分析记录（按时间倒序）。
     *
     * @param id 投标记录 ID
     * @return 历史分析记录列表，无记录时返空列表
     */
    @GetMapping("/bid-record/risk-history/{id}")
    public Result<List<RiskAnalysis>> getRiskHistory(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.getHistory(id), "查询成功");
    }
}
