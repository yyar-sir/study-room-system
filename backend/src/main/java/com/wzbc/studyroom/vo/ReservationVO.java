package com.wzbc.studyroom.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 预约记录视图 */
@Data
@Builder
public class ReservationVO {

    private Long id;
    private String reservationNo;

    private Long storeId;
    private String storeName;

    private Long seatId;
    private String seatNo;
    private String seatTypeName;

    private LocalDate reserveDate;
    private LocalTime startTime;
    private LocalTime endTime;

    /** 0待使用 1使用中 2已完成 3已取消 4爽约 */
    private Integer status;
    /** 状态中文，前端直接用，避免每端各写一份映射 */
    private String statusText;

    /** 签到码（仅本人可见，用于生成二维码） */
    private String checkinCode;

    /**
     * 取消原因。
     *
     * <p>此前只落在数据库的 cancel_reason 里，任何接口都不返回——于是
     * 「预约怎么没了」用户无从得知。前台代取消（异常订单处理）时这一条尤其重要：
     * 预约是被别人取消的，不给出原因用户只会到店后才发现。
     */
    private String cancelReason;

    /** 当前状态下是否允许取消 */
    private Boolean canCancel;
    /** 当前状态下是否允许签到 */
    private Boolean canCheckin;
    /** 当前状态下是否允许签退 */
    private Boolean canCheckout;
}
