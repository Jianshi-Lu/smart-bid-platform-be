package io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 政策资讯采集入口。 */
@RestController
@RequestMapping("/knowledge-ingestion-agent")
public class KnowledgeIngestionAgentController {

    /**
     * 手动触发一次政策资讯采集任务。
     *
     * <p>当前仅作为接口展位，后续接入政策采集服务或调度服务。</p>
     */
    @PostMapping("/run")
    public Result<Void> run() {
        return Result.success(null, "政策资讯采集接口占位");
    }
}
