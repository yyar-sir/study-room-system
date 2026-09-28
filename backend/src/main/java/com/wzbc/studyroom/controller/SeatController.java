package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.SeatService;
import com.wzbc.studyroom.vo.SeatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 座位图（可视化选座）。
 *
 * <p>不限定角色：三类角色都需要看座位状态（用户选座、前台管理、经营者查看）。
 * 但仍要求登录 —— 座位实时占用属于经营数据，不对匿名者开放。
 */
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * @param storeId   门店
     * @param date      预约日期 yyyy-MM-dd；与 startTime/endTime 一起给出时才计算占用
     * @param startTime HH:mm 或 HH:mm:ss
     * @param endTime   HH:mm 或 HH:mm:ss
     */
    @GetMapping
    public Result<List<SeatVO>> seatMap(@RequestParam Long storeId,
                                       @RequestParam(required = false) String date,
                                       @RequestParam(required = false) String startTime,
                                       @RequestParam(required = false) String endTime) {
        return Result.ok(seatService.seatMap(storeId, date, startTime, endTime, UserContext.userId()));
    }
}
