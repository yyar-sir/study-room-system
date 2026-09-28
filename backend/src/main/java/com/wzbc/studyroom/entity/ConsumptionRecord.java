package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消费与权益流水账本 —— 全系统防刷设计的核心。
 *
 * <p>权益余额以本流水表为唯一真相，<b>不按当前状态实时推导</b>。
 * {@code (user_id, source_key)} 上的唯一索引保证幂等：
 * 同一件事重复触发只落一条流水，重复提交、重复回调都不会重复扣次。
 */
@Data
@TableName("consumption_record")
public class ConsumptionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long membershipId;
    private Long storeId;
    /** 1购买 2预约扣次 3取消返还 4退款 5爽约扣减 */
    private Integer bizType;
    /** 金额变动（正为收入，负为支出） */
    private BigDecimal amount;
    /** 次数变动（负数表示扣减） */
    private Integer timesDelta;
    /** 变动后剩余次数快照 */
    private Integer balanceTimes;
    /** 幂等键，如 checkin:1024 / order:20261012001 */
    private String sourceKey;
    private String remark;

    private LocalDateTime createdAt;
}
