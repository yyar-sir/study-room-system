package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 座位预约记录 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reservationNo;
    private Long userId;
    private Long storeId;
    private Long seatId;
    private Long membershipId;
    private LocalDate reserveDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 0待使用 1使用中 2已完成 3已取消 4爽约 */
    private Integer status;
    /** 签到码（扫码签到用） */
    private String checkinCode;
    /** 1用户自助预约 2前台代约 */
    private Integer source;
    private String cancelReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
