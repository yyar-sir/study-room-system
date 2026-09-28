package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到签退记录。
 *
 * <p>{@code reservation_id} 上有唯一索引：一条预约只能签到一次。
 * 这是防止「重复签到重复扣次」的最后一道防线 —— 即便应用层的判断被绕过，
 * 数据库层也拦得住。
 */
@Data
@TableName("checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reservationId;
    private Long userId;
    private Long seatId;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    /** 1用户扫码签到 2前台协助签到 */
    private Integer checkinType;
    private Long operatorId;
    /** 本次自习时长（分钟，签退时结算） */
    private Integer durationMinutes;

    private LocalDateTime createdAt;
}
