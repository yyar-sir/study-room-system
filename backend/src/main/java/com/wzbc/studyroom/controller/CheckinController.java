package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.dto.CheckinDTO;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.CheckinService;
import com.wzbc.studyroom.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到签退。
 *
 * <p>刻意分两组路径，把「自助」与「前台协助」在权限层就分开：
 * <ul>
 *   <li>{@code /api/user/**}  仅自习室用户（role 2）—— 只能操作自己的预约；</li>
 *   <li>{@code /api/staff/**} 前台管理员与经营者（role 0/1）—— 可代到店用户签到签退。</li>
 * </ul>
 * 这样即便前端把按钮藏错了，后端也不会允许用户去调前台接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /** 用户自助扫码签到 */
    @PostMapping("/user/checkin")
    @RequireRole(2)
    public Result<ReservationVO> selfCheckin(@Valid @RequestBody CheckinDTO dto) {
        return Result.ok(checkinService.checkin(UserContext.userId(), dto.getCheckinCode(), false));
    }

    /** 用户自助签退 */
    @PostMapping("/user/checkout/{reservationId}")
    @RequireRole(2)
    public Result<ReservationVO> selfCheckout(@PathVariable Long reservationId) {
        return Result.ok(checkinService.checkout(UserContext.userId(), reservationId, false));
    }

    /** 前台协助签到（代到店用户操作） */
    @PostMapping("/staff/checkin")
    @RequireRole({0, 1})
    public Result<ReservationVO> staffCheckin(@Valid @RequestBody CheckinDTO dto) {
        return Result.ok(checkinService.checkin(UserContext.userId(), dto.getCheckinCode(), true));
    }

    /** 前台协助签退 */
    @PostMapping("/staff/checkout/{reservationId}")
    @RequireRole({0, 1})
    public Result<ReservationVO> staffCheckout(@PathVariable Long reservationId) {
        return Result.ok(checkinService.checkout(UserContext.userId(), reservationId, true));
    }
}
