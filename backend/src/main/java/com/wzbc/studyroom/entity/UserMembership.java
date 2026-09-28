package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户已购套餐（权益实例）。
 *
 * <p>{@code planName} 是购买时的套餐名快照 —— 套餐改名或下架后，
 * 历史权益记录仍应显示当时买的名称，不能跟着套餐表变。
 */
@Data
@TableName("user_membership")
public class UserMembership {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long planId;
    private Long orderId;
    private String planName;
    private LocalDateTime startTime;
    private LocalDateTime expireTime;
    /** 总次数，-1 不限次 */
    private Integer totalTimes;
    /** 剩余次数，-1 不限次 */
    private Integer remainTimes;
    /** 1生效 0已失效 2已用完 3已退款 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
