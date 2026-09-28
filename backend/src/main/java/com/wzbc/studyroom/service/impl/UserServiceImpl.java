package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.dto.ChangePasswordDTO;
import com.wzbc.studyroom.dto.LoginDTO;
import com.wzbc.studyroom.dto.ProfileDTO;
import com.wzbc.studyroom.dto.RegisterDTO;
import com.wzbc.studyroom.dto.ResetPasswordDTO;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.security.JwtUtil;
import com.wzbc.studyroom.service.UserService;
import com.wzbc.studyroom.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * BCrypt 是无状态工具，做成静态常量复用。
     * 每次 new 一个会重复做强度初始化，没有必要。
     */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ------------------------------------------------------------------
    //  登录失败锁定（防在线暴力破解）
    //  说明：用进程内 Map 实现，单机部署足够；若将来多实例部署，
    //  应换成 Redis 的 INCR + EXPIRE，否则锁定状态不共享。
    // ------------------------------------------------------------------
    private static final int MAX_FAIL = 5;
    private static final long LOCK_WINDOW_MS = 15 * 60 * 1000L;
    /** key = userId, value = [失败次数, 首次失败时间戳] */
    private static final Map<Long, long[]> LOGIN_FAILS = new ConcurrentHashMap<>();

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));

        // 账号不存在与密码错误返回同一文案，避免账号枚举
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "手机号或密码错误");
        }
        if (isLocked(user.getId())) {
            throw new BizException(ErrorCode.LOGIN_LOCKED,
                    "密码错误次数过多，账号已临时锁定，请 15 分钟后再试");
        }
        if (!ENCODER.matches(dto.getPassword(), user.getPassword())) {
            recordFail(user.getId());
            throw new BizException(ErrorCode.LOGIN_FAILED, "手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，请联系门店管理员");
        }

        LOGIN_FAILS.remove(user.getId());
        User touch = new User();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);

        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PHONE_EXISTS, "该手机号已注册");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        // 只存散列，永不存明文。也刻意不用 MD5：MD5 无盐且速度极快，彩虹表可秒破。
        user.setPassword(ENCODER.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRealName(dto.getRealName() == null || dto.getRealName().isBlank()
                ? dto.getNickname() : dto.getRealName());
        user.setGender(0);
        user.setRole(2);        // 注册即自习室用户，禁止通过入参提权
        user.setStatus(1);
        user.setNotifyEnabled(1);
        user.setRemindEnabled(1);
        user.setTotalStudyMinutes(0);
        userMapper.insert(user);

        return toVO(user);
    }

    @Override
    public User getById(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "账号不存在");
        }
        if (!ENCODER.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG, "原密码不正确");
        }
        if (ENCODER.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_SAME, "新密码不能与原密码相同");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(ENCODER.encode(dto.getNewPassword()));
        // 关键：写入改密时间，让此前签发的所有 token 立即失效
        update.setPasswordChangedAt(LocalDateTime.now());
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Long userId, ProfileDTO dto) {
        User exist = userMapper.selectById(userId);
        if (exist == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "账号不存在");
        }
        User patch = new User();
        patch.setId(userId);
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            patch.setNickname(dto.getNickname().trim());
        }
        if (dto.getRealName() != null) {
            patch.setRealName(dto.getRealName().trim());
        }
        if (dto.getGender() != null) {
            if (dto.getGender() < 0 || dto.getGender() > 2) {
                throw new BizException(ErrorCode.PARAM_INVALID, "性别取值只能是 0未知 / 1男 / 2女");
            }
            patch.setGender(dto.getGender());
        }
        if (dto.getAvatar() != null) {
            patch.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(patch);
        return userMapper.selectById(userId);
    }

    // ------------------------------------------------------------------
    //  忘记密码（验证码重置）
    //
    //  局限说明：验证码存在进程内 Map，单机部署够用；
    //  多实例部署必须换成 Redis，否则用户在 A 实例拿码、到 B 实例验就验不过。
    // ------------------------------------------------------------------
    private static final Map<String, ResetCode> RESET_CODES = new ConcurrentHashMap<>();
    private static final long RESET_CODE_TTL_MS = 5 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record ResetCode(String code, long expireAt) {
    }

    @Override
    public String sendResetCode(String phone) {
        String p = phone == null ? "" : phone.trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, p).last("LIMIT 1"));
        // 权衡说明：严格的安全实践是「无论账号是否存在都返回相同提示」以防账号枚举，
        // 但毕设演示场景下用户需要明确知道是不是手机号填错了，因此这里给出明确提示。
        if (user == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该手机号尚未注册");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        RESET_CODES.put(p, new ResetCode(code, System.currentTimeMillis() + RESET_CODE_TTL_MS));
        return code;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        String p = dto.getPhone() == null ? "" : dto.getPhone().trim();
        ResetCode rc = RESET_CODES.get(p);
        if (rc == null || rc.expireAt() < System.currentTimeMillis()) {
            RESET_CODES.remove(p);
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码已过期，请重新获取");
        }
        if (!rc.code().equals(dto.getCode().trim())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码不正确");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, p).last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号不存在");
        }
        if (ENCODER.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_SAME, "新密码不能与原密码相同");
        }

        User patch = new User();
        patch.setId(user.getId());
        patch.setPassword(ENCODER.encode(dto.getNewPassword()));
        // 与改密一致：重置密码也要踢掉所有旧 token
        patch.setPasswordChangedAt(LocalDateTime.now());
        userMapper.updateById(patch);

        RESET_CODES.remove(p);          // 验证码一次性使用
        LOGIN_FAILS.remove(user.getId()); // 顺带解除登录失败锁定，避免用户刚重置完又被锁
    }

    // ------------------------------------------------------------------

    private boolean isLocked(Long userId) {
        long[] v = LOGIN_FAILS.get(userId);
        if (v == null) {
            return false;
        }
        if (System.currentTimeMillis() - v[1] > LOCK_WINDOW_MS) {
            LOGIN_FAILS.remove(userId);
            return false;
        }
        return v[0] >= MAX_FAIL;
    }

    private void recordFail(Long userId) {
        long now = System.currentTimeMillis();
        LOGIN_FAILS.compute(userId, (k, v) -> {
            if (v == null || now - v[1] > LOCK_WINDOW_MS) {
                return new long[]{1, now};
            }
            v[0]++;
            return v;
        });
    }

    private LoginVO toVO(User user) {
        return LoginVO.builder()
                .token(jwtUtil.createToken(user.getId(), user.getPhone(), user.getRole()))
                .id(user.getId())
                .phone(user.getPhone())
                .realName(user.getRealName())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .storeId(user.getStoreId())
                .build();
    }
}
