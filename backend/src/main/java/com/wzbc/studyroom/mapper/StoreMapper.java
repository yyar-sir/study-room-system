package com.wzbc.studyroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzbc.studyroom.entity.Store;

/**
 * 门店 Mapper。
 * 继承 BaseMapper 即获得单表 CRUD，无需写任何 XML。
 * 无需 @Mapper 注解：启动类上的 @MapperScan("com.wzbc.studyroom.mapper") 已统一扫描本包。
 */
public interface StoreMapper extends BaseMapper<Store> {
}
