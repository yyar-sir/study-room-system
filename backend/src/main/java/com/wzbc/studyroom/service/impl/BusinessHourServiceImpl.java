package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.BusinessHour;
import com.wzbc.studyroom.mapper.BusinessHourMapper;
import com.wzbc.studyroom.service.BusinessHourService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessHourServiceImpl implements BusinessHourService {

    private final BusinessHourMapper businessHourMapper;

    private static final int STATUS_ON = 1;
    private static final int BOOKABLE_YES = 1;

    @Override
    public List<BusinessHour> listByStore(Long storeId, boolean includeDisabled) {
        LambdaQueryWrapper<BusinessHour> w = new LambdaQueryWrapper<BusinessHour>()
                .eq(storeId != null, BusinessHour::getStoreId, storeId)
                .orderByAsc(BusinessHour::getSort)
                .orderByAsc(BusinessHour::getStartTime);
        if (!includeDisabled) {
            w.eq(BusinessHour::getStatus, STATUS_ON);
        }
        return businessHourMapper.selectList(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessHour save(BusinessHour bh) {
        if (bh.getStoreId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先指定门店");
        }
        if (bh.getName() == null || bh.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时段名称不能为空");
        }
        if (bh.getStartTime() == null || bh.getEndTime() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写时段的开始与结束时间");
        }
        if (!bh.getEndTime().isAfter(bh.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        if (bh.getBookable() == null) {
            bh.setBookable(BOOKABLE_YES);
        }
        if (bh.getStatus() == null) {
            bh.setStatus(STATUS_ON);
        }
        if (bh.getSort() == null) {
            bh.setSort(0);
        }

        // 同门店内「启用中」的时段不允许重叠，否则「这个点能不能约」会取决于
        // 遍历顺序，变成不可解释的行为。判定用严格不等号，因此 08:00-12:00 与
        // 12:00-18:00 这种首尾相接是允许的（与预约冲突判定同一套语义）。
        //
        // 只和启用中的时段比对（includeDisabled=false）：停用的时段既不参与
        // 可预约并集，就不该再占用时间段——否则「先停用 A 再把 B 扩到 A 的范围」
        // 这种正常操作会被自己已经停用的记录挡住。
        for (BusinessHour other : listByStore(bh.getStoreId(), false)) {
            if (other.getId() != null && other.getId().equals(bh.getId())) {
                continue;
            }
            if (bh.getStatus() != null && bh.getStatus() != STATUS_ON) {
                continue;   // 本次保存的是停用状态，不参与占用
            }
            boolean overlap = bh.getStartTime().isBefore(other.getEndTime())
                    && bh.getEndTime().isAfter(other.getStartTime());
            if (overlap) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "与已有时段「" + other.getName() + "」(" + other.getStartTime()
                                + "-" + other.getEndTime() + ") 重叠");
            }
        }

        if (bh.getId() == null) {
            businessHourMapper.insert(bh);
        } else {
            businessHourMapper.updateById(bh);
        }
        return businessHourMapper.selectById(bh.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null || businessHourMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时段不存在");
        }
        businessHourMapper.deleteById(id);
    }

    @Override
    public void assertReservable(Long storeId, LocalTime start, LocalTime end) {
        List<BusinessHour> slots = listByStore(storeId, false);
        if (slots.isEmpty()) {
            return;     // 未配置 → 回落到门店营业时间，由调用方校验
        }

        // 只取可预约的时段，按开始时间排序后合并成若干「不相交区间」。
        //
        // 判定用的是「预约区间被并集完整覆盖」，而不是「落在某一个时段内」。
        // 原因：门店常把一天切成 08-12 / 12-18 / 18-22 三段，此时 11:00-13:00
        // 这种跨段预约是完全正常的；若要求落在单个时段内会被误拒。
        // 而 bookable=0 的闭馆段不参与并集，于是自然留下空洞——
        // 「午休 12:00-13:00 不可预约」就是这样表达的。
        List<BusinessHour> bookable = slots.stream()
                .filter(s -> s.getBookable() != null && s.getBookable() == BOOKABLE_YES)
                .sorted(Comparator.comparing(BusinessHour::getStartTime))
                .toList();
        if (bookable.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店当前没有可预约的营业时段");
        }

        LocalTime coverStart = null;
        LocalTime coverEnd = null;
        for (BusinessHour s : bookable) {
            if (coverStart == null) {
                coverStart = s.getStartTime();
                coverEnd = s.getEndTime();
                continue;
            }
            if (!s.getStartTime().isAfter(coverEnd)) {
                // 与当前合并段相接或重叠 → 并入
                if (s.getEndTime().isAfter(coverEnd)) {
                    coverEnd = s.getEndTime();
                }
                continue;
            }
            // 出现断档：先看预约是否完全落在已经合并好的这一段里
            if (covered(start, end, coverStart, coverEnd)) {
                return;
            }
            coverStart = s.getStartTime();
            coverEnd = s.getEndTime();
        }
        if (covered(start, end, coverStart, coverEnd)) {
            return;
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "所选时间不在门店可预约时段内（可能跨越了闭馆时段）");
    }

    /** 闭区间包含判断：[start, end] 是否完全落在 [coverStart, coverEnd] 内 */
    private boolean covered(LocalTime start, LocalTime end, LocalTime coverStart, LocalTime coverEnd) {
        return !start.isBefore(coverStart) && !end.isAfter(coverEnd);
    }
}
