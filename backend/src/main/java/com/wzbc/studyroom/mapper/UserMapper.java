package com.wzbc.studyroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzbc.studyroom.entity.User;

/**
 * 继承 BaseMapper 即获得单表 CRUD，无需写任何 XML。
 * ⚠️ 必须被 @MapperScan 扫描到（见启动类），否则注入时报找不到 Bean。
 */
public interface UserMapper extends BaseMapper<User> {
}
