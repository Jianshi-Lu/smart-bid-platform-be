package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.service.InvitationForBidService;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidderAccountService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 投标方登录后浏览招标公告的只读入口。 */
@RestController
@RequestMapping("/bidder/invitations")
public class InvitationController {

    @Resource
    private InvitationForBidService invitationForBidService;

    @Resource
    private BidderAccountService bidderAccountService;

    /** 查询投标方可浏览的公告列表。 */
    @GetMapping
    public Result<PageInfo<InvitationForBid>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "invitation_id desc") String sort,
            HttpServletRequest request) {
        authenticate(request);
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<InvitationForBid> invitations = invitationForBidService.listAll();
            return Result.success(new PageInfo<>(invitations), "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /** 按 ID 查询投标方准备投标的公告。 */
    @GetMapping("/{id}")
    public Result<InvitationForBid> get(
            @PathVariable Integer id,
            HttpServletRequest request) {
        authenticate(request);
        return Result.success(
                invitationForBidService.getById(id),
                "查询成功");
    }

    private void authenticate(HttpServletRequest request) {
        bidderAccountService.authenticateBasic(
                request.getHeader("Authorization"));
    }
}
