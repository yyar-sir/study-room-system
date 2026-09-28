package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.dto.ReserveDTO;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.ReservationService;
import com.wzbc.studyroom.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 预约选座（仅自习室用户，role = 2） */
@RestController
@RequestMapping("/api/user/reservations")
@RequireRole(2)
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 创建预约 */
    @PostMapping
    public Result<ReservationVO> create(@Valid @RequestBody ReserveDTO dto) {
        return Result.ok(reservationService.create(UserContext.userId(), dto));
    }

    /** 我的预约列表；status 为空表示全部 */
    @GetMapping
    public Result<List<ReservationVO>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(reservationService.myReservations(UserContext.userId(), status));
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public Result<ReservationVO> detail(@PathVariable Long id) {
        return Result.ok(reservationService.detail(UserContext.userId(), id));
    }

    /** 取消预约 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        reservationService.cancel(UserContext.userId(), id, reason);
        return Result.ok();
    }
}
