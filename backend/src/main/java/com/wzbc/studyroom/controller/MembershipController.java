package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.dto.PurchaseDTO;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.vo.UserMembershipVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会员套餐：公开套餐列表 + 用户购买/查询自己的权益 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    /** 套餐列表（公开，未登录也能看价格 —— 属于营销信息） */
    @GetMapping("/public/plans")
    public Result<List<MembershipPlan>> plans() {
        return Result.ok(membershipService.listAvailablePlans());
    }

    /** 我的套餐（含已失效历史） */
    @GetMapping("/user/memberships")
    @RequireRole(2)
    public Result<List<UserMembershipVO>> myMemberships() {
        return Result.ok(membershipService.myMemberships(UserContext.userId()));
    }

    /** 购买 / 续费套餐 */
    @PostMapping("/user/memberships/purchase")
    @RequireRole(2)
    public Result<UserMembershipVO> purchase(@Valid @RequestBody PurchaseDTO dto) {
        return Result.ok(membershipService.purchase(UserContext.userId(), dto));
    }

    /** 我的消费与权益流水（账本明细） */
    @GetMapping("/user/consumption-records")
    @RequireRole(2)
    public Result<List<ConsumptionRecord>> records() {
        return Result.ok(membershipService.myRecords(UserContext.userId()));
    }
}
