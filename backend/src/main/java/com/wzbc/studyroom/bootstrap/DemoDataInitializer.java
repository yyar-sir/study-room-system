package com.wzbc.studyroom.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.StoreMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示账号初始化（仅 local 环境生效）。
 *
 * <p><b>为什么不把演示账号的口令散列直接写进 demo-data.sql</b>：
 * BCrypt 散列无法离线手写，若在 SQL 里硬编码一个"看起来像 BCrypt"的字符串，
 * 一旦算错，表现是「登录一直提示密码错误」，而排查方向会被误导到密码校验逻辑上。
 * 交给 {@link BCryptPasswordEncoder} 在启动时生成，用的是与登录完全相同的编码器，
 * 从根上避免这类假故障；同时也保证了重复启动的幂等（账号已存在则不动）。
 *
 * <p>若 {@code demo-data.sql} 尚未执行（门店不存在），本组件会跳过并打印提示，
 * 而不是抛外键异常中断启动。
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final StoreMapper storeMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    /** 三个角色的演示口令统一为 123456 */
    private static final String DEMO_PASSWORD = "123456";

    @Override
    public void run(String... args) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .orderByAsc(Store::getId).last("LIMIT 1"));
        if (store == null) {
            log.warn("未找到任何门店，跳过演示账号初始化。请先执行 backend/sql/demo-data.sql");
            return;
        }

        // 手机号                 角色  门店      说明
        ensure("13900000000", 0, null,          "经营者", "门店老板");
        ensure("13900000001", 1, store.getId(), "前台管理员", "前台小张");
        ensure("13900000002", 2, store.getId(), "自习室用户", "同学小李");
    }

    private void ensure(String phone, int role, Long storeId, String realName, String nickname) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
        if (exists != null && exists > 0) {
            return;     // 已存在则不动，保证重复启动幂等
        }
        User u = new User();
        u.setPhone(phone);
        u.setPassword(ENCODER.encode(DEMO_PASSWORD));
        u.setRealName(realName);
        u.setNickname(nickname);
        u.setGender(0);
        u.setRole(role);
        u.setStoreId(storeId);
        u.setStatus(1);
        u.setNotifyEnabled(1);
        u.setRemindEnabled(1);
        u.setTotalStudyMinutes(0);
        userMapper.insert(u);
        log.info("已创建演示账号 {}（{}，角色 {}）", phone, realName, role);
    }
}
