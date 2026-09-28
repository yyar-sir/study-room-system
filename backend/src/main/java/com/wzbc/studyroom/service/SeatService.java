package com.wzbc.studyroom.service;

import com.wzbc.studyroom.vo.SeatVO;

import java.util.List;

public interface SeatService {

    /**
     * 门店座位图：返回某门店在指定时段内全部座位及其占用状态。
     *
     * <p>{@code date/startTime/endTime} 为空时退化为「只看座位物理状态」，
     * 不做时段占用计算 —— 供前台「座位状态管理」页面使用。
     *
     * @param userId 当前用户 id，可为 null；用于计算「我是否有可用套餐」
     */
    List<SeatVO> seatMap(Long storeId, String date, String startTime, String endTime, Long userId);
}
