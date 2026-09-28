package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员套餐购买订单。表名避开 MySQL 保留字 ORDER。 */
@Data
@TableName("member_order")
public class MemberOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long planId;
    private Long storeId;
    private BigDecimal amount;
    /** 1模拟支付 2微信 3支付宝 */
    private Integer payType;
    /** 0待支付 1已支付 2已取消 3已退款 */
    private Integer payStatus;
    private LocalDateTime payTime;
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
