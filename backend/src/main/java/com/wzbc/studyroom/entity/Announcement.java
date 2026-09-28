package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 公告 */
@Data
@TableName("announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店 id；scope=1（全平台）时可为空 */
    private Long storeId;
    private String title;
    private String content;
    private Long publisherId;
    /** 1全平台 2本门店 */
    private Integer scope;
    /** 1已发布 0已下架 */
    private Integer status;
    private LocalDateTime publishTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
