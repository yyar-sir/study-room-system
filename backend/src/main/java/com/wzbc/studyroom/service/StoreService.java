package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.Store;

import java.util.List;

public interface StoreService {

    /** 全部门店（经营者视角；按 id 升序） */
    List<Store> listAll();

    /** 营业中的门店（公开，供用户端选择门店 / 展示门店信息） */
    List<Store> listActive();

    /** 门店详情；不存在时抛 BizException */
    Store getById(Long id);

    /** 新建门店（经营者） */
    Store create(Store store);

    /** 修改门店（经营者）。patch 中为 null 的字段保持原值。 */
    Store update(Long id, Store patch);
}
