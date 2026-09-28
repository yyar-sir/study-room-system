package com.wzbc.studyroom.service;

import com.wzbc.studyroom.dto.ChangePasswordDTO;
import com.wzbc.studyroom.dto.LoginDTO;
import com.wzbc.studyroom.dto.RegisterDTO;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.vo.LoginVO;

public interface UserService {

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    User getById(Long id);

    /**
     * 修改密码。
     *
     * <p>成功后写入 {@code password_changed_at}，AuthInterceptor 会据此拒绝此前签发的 token，
     * 等价于「把所有旧设备踢下线」。这是 JWT 这类无状态令牌的必要补偿手段 ——
     * 无状态令牌本身无法撤销。
     */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /** 维护个人资料（昵称/姓名/性别/头像）。手机号与角色不允许自行修改。 */
    User updateProfile(Long userId, com.wzbc.studyroom.dto.ProfileDTO dto);

    /**
     * 忘记密码·发送重置验证码。
     *
     * <p>毕设环境没有接短信通道，验证码在服务端生成并临时保存，
     * <b>同时回传给接口调用方以便演示</b>。生产环境必须去掉回传、改由短信下发 ——
     * 否则等于把重置密码的钥匙直接给了请求方。
     *
     * @return 6 位验证码（仅演示用）
     */
    String sendResetCode(String phone);

    /** 忘记密码·用验证码重置密码；成功后旧 token 全部失效 */
    void resetPassword(com.wzbc.studyroom.dto.ResetPasswordDTO dto);
}
