package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 门店营业时段（可多个）。
 *
 * <p>与 {@code store.open_time / close_time} 的分工：
 * 后者描述「一天从几点开到几点」这一条总边界；本表描述边界<b>内部</b>的分段，
 * 例如「上午场 08:00-12:00 可预约 / 午休 12:00-13:00 不可预约 / 晚场 13:00-22:00 可预约」。
 * 没有这张表就只能整段营业时间一起开放，午休闭馆这类场景无法表达。
 *
 * <p>{@code bookable=0} 的时段是「闭馆但仍在营业时间内」的区间，
 * 预约必须完整落在某个 {@code bookable=1} 的时段里。
 */
@Data
@TableName("business_hour")
public class BusinessHour {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    /** 时段名称，如「上午场」 */
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 1 可预约 0 不可预约（闭馆/清洁/维护） */
    private Integer bookable;
    private Integer sort;
    /** 1 启用 0 停用 */
    private Integer status;
}
