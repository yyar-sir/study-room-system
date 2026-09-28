package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 自习室知识库：AI 助手回答「套餐规则 / 座位分布 / 预约政策」的事实来源 */
@Data
@TableName("ai_knowledge")
public class AiKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    private String question;
    private String answer;
    /** 关键词，逗号分隔，用于关键词召回 */
    private String keywords;
    private Integer hitCount;
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
