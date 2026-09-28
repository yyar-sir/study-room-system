package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.BusinessHour;

import java.time.LocalTime;
import java.util.List;

/**
 * 门店营业时段（一天内的分段，含「闭馆不可预约」区间）。
 */
public interface BusinessHourService {

    /** 列出某门店的时段；includeDisabled=false 时只返回启用中的 */
    List<BusinessHour> listByStore(Long storeId, boolean includeDisabled);

    BusinessHour save(BusinessHour bh);

    void remove(Long id);

    /**
     * 校验预约时段是否可预约。
     *
     * <p>规则：预约区间必须被<b>可预约时段的并集完整覆盖</b>。
     * 用并集而不是「落在单个时段内」——门店常把一天切成 08-12 / 12-18 / 18-22 三段，
     * 此时 11:00-13:00 这种跨段预约应当放行；而 {@code bookable=0} 的闭馆段
     * 不参与并集，于是留下空洞，「午休不可预约」就是这样表达的。
     *
     * <p><b>未配置任何时段时直接放行</b>，由调用方继续按门店的
     * {@code open_time/close_time} 校验。这条回退很重要：它让本功能
     * 在经营者没配置之前不影响既有行为。
     */
    void assertReservable(Long storeId, LocalTime start, LocalTime end);
}
