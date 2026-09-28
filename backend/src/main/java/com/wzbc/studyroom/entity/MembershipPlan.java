package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员套餐（次卡 / 月卡 / 季卡 / 年卡） */
@Data
@TableName("membership_plan")
public class MembershipPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** 1次卡 2月卡 3季卡 4年卡 */
    private Integer planType;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer durationDays;
    /** 总可用次数，-1 表示不限次 */
    private Integer totalTimes;
    /** 每日可预约次数上限 */
    private Integer dailyLimit;
    private String benefits;
    private String description;
    /** 1上架 0下架 */
    private Integer status;
    private Integer sort;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
