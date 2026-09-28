package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 座位类型（普通座 / 靠窗座 / 独立隔间 / 双人座） */
@Data
@TableName("seat_type")
public class SeatType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    /** 价格系数，用于套餐计价时的差异化定价 */
    private BigDecimal priceFactor;
    /** 前端可视化选座用的图标标识与配色 */
    private String icon;
    private String color;
    private Integer sort;
    /** 1启用 0停用 */
    private Integer status;
}
