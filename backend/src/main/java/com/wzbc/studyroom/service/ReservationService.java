package com.wzbc.studyroom.service;

import com.wzbc.studyroom.dto.ReserveDTO;
import com.wzbc.studyroom.vo.ReservationVO;

import java.util.List;

public interface ReservationService {

    /** 创建预约（含时段冲突、营业时间、套餐权益、每日上限等全套校验） */
    ReservationVO create(Long userId, ReserveDTO dto);

    /** 我的预约列表；status 为 null 表示全部 */
    List<ReservationVO> myReservations(Long userId, Integer status);

    /** 预约详情（仅本人） */
    ReservationVO detail(Long userId, Long id);

    /** 取消预约（仅本人，且需在允许取消的时间窗内） */
    void cancel(Long userId, Long id, String reason);

    /**
     * 前台/经营者代用户取消预约（用于处理异常订单）。
     *
     * <p>与 {@link #cancel} 的三点区别，也是本方法存在的理由：
     * <ol>
     *   <li><b>不受「距开始 N 分钟」限制</b>。那条限制是防用户临开场放鸽子；
     *       而前台处理的恰恰是异常场景（用户电话说来不了、座位故障要换位），
     *       此时还按用户规则拦着，前台就只能干瞪眼。</li>
     *   <li><b>受门店数据范围约束</b>：{@code scopeStoreId} 非空时只能取消该门店的预约；
     *       传 null 表示不限制（经营者）。越范围抛 403。</li>
     *   <li><b>必须填原因</b>，并连同操作人一起写进 {@code cancel_reason}，留痕可追溯。</li>
     * </ol>
     * 状态仍要求「待使用」：已签到的走签退流程，已完成/爽约/已取消都不能再动。
     *
     * @param scopeStoreId 操作者可见的门店；null 表示不限（经营者）
     * @param operatorName 操作人标识（手机号），用于留痕
     */
    void cancelByStaff(Long id, String reason, Long scopeStoreId, String operatorName);
}
