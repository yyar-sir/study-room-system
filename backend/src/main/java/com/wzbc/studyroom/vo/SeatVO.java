package com.wzbc.studyroom.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 座位视图（可视化选座用）。
 *
 * <p>{@code occupied} 由「该座位在目标时段是否已有生效预约」实时算出，
 * 前端直接按它上色，不需要自己判断时段重叠 —— 重叠判定的边界条件
 * （相邻时段算不算冲突）必须只在一个地方定义，否则前后端各写一套必然不一致。
 */
@Data
@Builder
public class SeatVO {

    private Long id;
    private String seatNo;
    private Long typeId;
    private String typeName;
    private String color;

    /** 网格坐标，前端按 row/col 摆放 */
    private Integer rowIndex;
    private Integer colIndex;

    /** 座位物理状态：0可用 1维修中 2已停用 */
    private Integer status;

    /** 目标时段内是否已被占用 */
    private Boolean occupied;

    /** 是否可被当前用户预约（综合座位状态、占用情况、套餐权益后给出） */
    private Boolean bookable;

    /** 占用原因提示，如「已被预约」「维修中」 */
    private String reason;
}
