package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 座位。rowIndex/colIndex 供前端渲染可视化选座网格。 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private String seatNo;
    private Long typeId;
    private Integer rowIndex;
    private Integer colIndex;
    /** 0可用 1维修中 2已停用 */
    private Integer status;
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
