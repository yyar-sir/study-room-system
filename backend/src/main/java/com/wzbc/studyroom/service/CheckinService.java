package com.wzbc.studyroom.service;

import com.wzbc.studyroom.vo.ReservationVO;

public interface CheckinService {

    /**
     * 扫码签到。
     *
     * @param operatorId 操作人（自助签到时即本人，前台协助时是前台账号）
     * @param code       二维码/前台屏幕携带的签到码
     * @param asStaff    true = 前台协助签到（可代他人操作）；false = 用户自助（只能签自己的）
     */
    ReservationVO checkin(Long operatorId, String code, boolean asStaff);

    /**
     * 签退：结算自习时长、累计到用户的学习总时长、把预约置为已完成。
     *
     * @param asStaff true = 前台协助签退
     */
    ReservationVO checkout(Long operatorId, Long reservationId, boolean asStaff);
}
