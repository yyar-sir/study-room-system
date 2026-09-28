package com.wzbc.studyroom.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 我的套餐（权益）视图 */
@Data
@Builder
public class UserMembershipVO {

    private Long id;
    private Long planId;
    /** 购买时的套餐名快照 */
    private String planName;

    private LocalDateTime startTime;
    private LocalDateTime expireTime;

    /** 剩余次数，-1 表示不限次 */
    private Integer remainTimes;
    private Integer totalTimes;
    /** 剩余天数，负数表示已过期 */
    private Long remainDays;

    /** 1生效 0已失效 2已用完 3已退款 */
    private Integer status;
    private String statusText;

    /** 是否为当前可用于预约的有效套餐 */
    private Boolean usable;
}
