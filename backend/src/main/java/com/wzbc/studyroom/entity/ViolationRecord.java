package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 违规占座记录 */
@Data
@TableName("violation_record")
public class ViolationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long reservationId;
    private Long storeId;
    /** 1超时未签退 2预约未到(爽约) 3占用他人座位 4其他 */
    private Integer violationType;
    private String description;
    /** 处罚措施描述，如「扣除 1 次套餐权益」 */
    private String penalty;
    /** 0待处理 1已处理 2已申诉 */
    private Integer status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
