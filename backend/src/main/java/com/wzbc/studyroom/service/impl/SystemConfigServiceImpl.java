package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.SystemConfig;
import com.wzbc.studyroom.mapper.SystemConfigMapper;
import com.wzbc.studyroom.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /** 全局配置的 storeId 约定值 */
    private static final long GLOBAL = 0L;

    @Override
    public String get(Long storeId, String key, String defaultValue) {
        String v = find(storeId, key);
        if (v == null) {
            v = find(GLOBAL, key);
        }
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    @Override
    public int getInt(Long storeId, String key, int defaultValue) {
        String v = get(storeId, key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;   // 配置写错也不该让功能崩掉
        }
    }

    private String find(Long storeId, String key) {
        if (storeId == null) {
            return null;
        }
        SystemConfig c = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getStoreId, storeId)
                .eq(SystemConfig::getConfigKey, key)
                .last("LIMIT 1"));
        return c == null ? null : c.getConfigValue();
    }

    @Override
    public List<SystemConfig> list(Long storeId, String group) {
        LambdaQueryWrapper<SystemConfig> w = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getStoreId, storeId == null ? GLOBAL : storeId)
                .orderByAsc(SystemConfig::getConfigGroup)
                .orderByAsc(SystemConfig::getConfigKey);
        if (group != null && !group.isBlank()) {
            w.eq(SystemConfig::getConfigGroup, group);
        }
        return systemConfigMapper.selectList(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long storeId, String configKey, String configValue, String configGroup, String description) {
        if (configKey == null || configKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "配置键不能为空");
        }
        if (configValue == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "配置值不能为空");
        }
        long sid = storeId == null ? GLOBAL : storeId;

        SystemConfig exist = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getStoreId, sid)
                .eq(SystemConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (exist == null) {
            SystemConfig c = new SystemConfig();
            c.setStoreId(sid);
            c.setConfigKey(configKey.trim());
            c.setConfigValue(configValue);
            c.setConfigGroup(configGroup == null || configGroup.isBlank() ? "system" : configGroup);
            c.setDescription(description);
            systemConfigMapper.insert(c);
        } else {
            SystemConfig patch = new SystemConfig();
            patch.setId(exist.getId());
            patch.setConfigValue(configValue);
            if (configGroup != null && !configGroup.isBlank()) {
                patch.setConfigGroup(configGroup);
            }
            if (description != null) {
                patch.setDescription(description);
            }
            systemConfigMapper.updateById(patch);
        }
    }
}
