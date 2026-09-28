package com.wzbc.studyroom.service;

import java.util.List;
import java.util.Map;

/**
 * 经营数据统计（供 ECharts 前端消费）。
 *
 * <p>返回结构刻意设计成「ECharts 直接能用」的形状：
 * 概览卡片返回 Map，图表返回 {@code [{name, value}]} 或 {@code [{date, count}]} 列表，
 * 避免前端再做一次 pivot。
 *
 * <p>聚合统一走数据库 GROUP BY，而不是把明细捞到内存里循环统计 ——
 * 后者在数据量上来后是典型的性能陷阱。
 */
public interface StatisticsService {

    /** 看板概览卡片；storeId 为 null 表示全部门店（仅经营者） */
    Map<String, Object> overview(Long storeId);

    /** 时段客流分布：某天各小时的预约数 */
    List<Map<String, Object>> hourlyTraffic(Long storeId, String date);

    /** 近 N 天预约量趋势 */
    List<Map<String, Object>> reservationTrend(Long storeId, int days);

    /** 近 N 天营收趋势 */
    List<Map<String, Object>> revenueTrend(Long storeId, int days);

    /** 近 N 天新增会员趋势 */
    List<Map<String, Object>> memberGrowth(Long storeId, int days);

    /** 套餐销量分布（饼图） */
    List<Map<String, Object>> planDistribution(Long storeId);

    /** 各座位类型的预约占比（柱状图） */
    List<Map<String, Object>> seatTypeUsage(Long storeId);
}
