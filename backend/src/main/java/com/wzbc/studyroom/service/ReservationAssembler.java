package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.CheckinRecord;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.SeatType;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.mapper.CheckinRecordMapper;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.SeatTypeMapper;
import com.wzbc.studyroom.mapper.StoreMapper;
import com.wzbc.studyroom.vo.ReservationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预约 → 视图 的装配器。
 *
 * <p>单独抽成组件而不是塞进某个 Service：预约视图同时被「我的预约」和「签到」
 * 两条链路使用，若两边各写一份转换逻辑，字段和状态文案迟早会不一致。
 */
@Component
@RequiredArgsConstructor
public class ReservationAssembler {

    private final SeatMapper seatMapper;
    private final SeatTypeMapper seatTypeMapper;
    private final StoreMapper storeMapper;
    private final CheckinRecordMapper checkinRecordMapper;

    public List<ReservationVO> toVOList(List<Reservation> list) {
        List<ReservationVO> result = new ArrayList<>(list.size());
        for (Reservation r : list) {
            result.add(toVO(r));
        }
        return result;
    }

    public ReservationVO toVO(Reservation r) {
        Seat seat = seatMapper.selectById(r.getSeatId());
        Store store = storeMapper.selectById(r.getStoreId());
        SeatType type = seat == null ? null : seatTypeMapper.selectById(seat.getTypeId());

        LocalDateTime start = LocalDateTime.of(r.getReserveDate(), r.getStartTime());

        boolean pending = r.getStatus() != null && r.getStatus() == 0;
        boolean inUse = r.getStatus() != null && r.getStatus() == 1;

        // 签到/签退按钮是否可用，由后端按「状态 + 时间窗」判断后下发布尔值，
        // 前端不重复实现这套时间判断（否则前后端规则必然漂移）。
        boolean canCheckin = pending && LocalDateTime.now().isBefore(start.plusMinutes(30));
        boolean canCheckout = inUse;

        return ReservationVO.builder()
                .id(r.getId())
                .reservationNo(r.getReservationNo())
                .storeId(r.getStoreId())
                .storeName(store == null ? null : store.getName())
                .seatId(r.getSeatId())
                .seatNo(seat == null ? null : seat.getSeatNo())
                .seatTypeName(type == null ? null : type.getName())
                .reserveDate(r.getReserveDate())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .status(r.getStatus())
                .statusText(statusText(r.getStatus()))
                .checkinCode(r.getCheckinCode())
                .cancelReason(r.getCancelReason())
                .canCancel(pending)
                .canCheckin(canCheckin)
                .canCheckout(canCheckout)
                .build();
    }

    /** 是否已存在签到记录（用于判断重复签到） */
    public CheckinRecord findCheckin(Long reservationId) {
        List<CheckinRecord> list = checkinRecordMapper.selectList(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getReservationId, reservationId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待使用";
            case 1 -> "使用中";
            case 2 -> "已完成";
            case 3 -> "已取消";
            case 4 -> "爽约";
            default -> "未知";
        };
    }
}
