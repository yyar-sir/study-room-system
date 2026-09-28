package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** 门店 */
@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String address;
    private String phone;
    /** 每日开始营业时间 */
    private LocalTime openTime;
    /** 每日结束营业时间 */
    private LocalTime closeTime;
    /** 座位总数（冗余字段，便于列表与看板直接展示） */
    private Integer seatTotal;
    private String description;
    /** 1营业 0停业 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
