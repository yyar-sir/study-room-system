package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 经营数据统计（仅经营者，role = 0）—— 供前端 ECharts 消费。
 *
 * <p>storeId 不传表示全部门店汇总；传了则只看该门店。
 * 前台管理员没有本组接口的权限：门店级营收属于经营数据，不应下放到前台。
 */
@RestController
@RequestMapping("/api/owner/stats")
@RequireRole(0)
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /** 概览卡片：今日预约/签到、在座人数、座位使用率、营收、会员数 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Long storeId) {
        return Result.ok(statisticsService.overview(storeId));
    }

    /** 时段客流分布（某天各小时） */
    @GetMapping("/hourly-traffic")
    public Result<List<Map<String, Object>>> hourlyTraffic(@RequestParam(required = false) Long storeId,
                                                           @RequestParam(required = false) String date) {
        return Result.ok(statisticsService.hourlyTraffic(storeId, date));
    }

    /** 近 N 天预约量趋势 */
    @GetMapping("/reservation-trend")
    public Result<List<Map<String, Object>>> reservationTrend(@RequestParam(required = false) Long storeId,
                                                              @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.reservationTrend(storeId, clamp(days)));
    }

    /** 近 N 天营收趋势 */
    @GetMapping("/revenue-trend")
    public Result<List<Map<String, Object>>> revenueTrend(@RequestParam(required = false) Long storeId,
                                                          @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.revenueTrend(storeId, clamp(days)));
    }

    /** 近 N 天新增会员趋势 */
    @GetMapping("/member-growth")
    public Result<List<Map<String, Object>>> memberGrowth(@RequestParam(required = false) Long storeId,
                                                          @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.memberGrowth(storeId, clamp(days)));
    }

    /** 套餐销量分布（饼图） */
    @GetMapping("/plan-distribution")
    public Result<List<Map<String, Object>>> planDistribution(@RequestParam(required = false) Long storeId) {
        return Result.ok(statisticsService.planDistribution(storeId));
    }

    /** 各座位类型预约占比（柱状图） */
    @GetMapping("/seat-type-usage")
    public Result<List<Map<String, Object>>> seatTypeUsage(@RequestParam(required = false) Long storeId) {
        return Result.ok(statisticsService.seatTypeUsage(storeId));
    }

    /** 限制查询区间，避免前端传个 100000 天把库拖垮 */
    private int clamp(int days) {
        if (days < 1) {
            return 7;
        }
        return Math.min(days, 90);
    }
}
