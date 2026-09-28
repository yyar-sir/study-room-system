package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.mapper.StoreMapper;
import com.wzbc.studyroom.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreMapper storeMapper;

    @Override
    public List<Store> listAll() {
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .orderByAsc(Store::getId));
    }

    @Override
    public List<Store> listActive() {
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .eq(Store::getStatus, 1)
                .orderByAsc(Store::getId));
    }

    @Override
    public Store getById(Long id) {
        Store store = id == null ? null : storeMapper.selectById(id);
        if (store == null) {
            throw new BizException(ErrorCode.STORE_UNAVAILABLE, "门店不存在");
        }
        return store;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Store create(Store store) {
        if (store.getName() == null || store.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店名称不能为空");
        }
        if (store.getOpenTime() == null || store.getCloseTime() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请设置营业开始与结束时间");
        }
        if (!store.getCloseTime().isAfter(store.getOpenTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束营业时间必须晚于开始时间");
        }
        store.setId(null);                      // 防止前端传入 id 造成覆盖已有门店
        if (store.getStatus() == null) {
            store.setStatus(1);
        }
        if (store.getSeatTotal() == null) {
            store.setSeatTotal(0);
        }
        storeMapper.insert(store);
        return storeMapper.selectById(store.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Store update(Long id, Store patch) {
        Store exist = getById(id);              // 不存在会抛异常
        // MyBatis-Plus 默认只更新非 null 字段，因此 patch 里没传的字段会自动保持原值
        if (patch.getCloseTime() != null && patch.getOpenTime() != null
                && !patch.getCloseTime().isAfter(patch.getOpenTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束营业时间必须晚于开始时间");
        }
        patch.setId(exist.getId());
        storeMapper.updateById(patch);
        return storeMapper.selectById(id);
    }
}
