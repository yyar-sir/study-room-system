package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.BizTime;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.SeatType;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.SeatTypeMapper;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.service.SeatService;
import com.wzbc.studyroom.vo.SeatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatMapper seatMapper;
    private final SeatTypeMapper seatTypeMapper;
    private final ReservationMapper reservationMapper;
    private final MembershipService membershipService;

    /** 仍会占用座位的预约状态：0待使用、1使用中 */
    private static final List<Integer> ACTIVE_STATUS = List.of(0, 1);

    @Override
    public List<SeatVO> seatMap(Long storeId, String date, String startTime, String endTime, Long userId) {
        if (storeId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门店不能为空");
        }

        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getStoreId, storeId)
                .orderByAsc(Seat::getRowIndex)
                .orderByAsc(Seat::getColIndex));

        Map<Long, SeatType> typeMap = new HashMap<>();
        for (SeatType t : seatTypeMapper.selectList(null)) {
            typeMap.put(t.getId(), t);
        }

        // 是否给了完整时段；给了才算占用
        boolean withSlot = date != null && !date.isBlank()
                && startTime != null && !startTime.isBlank()
                && endTime != null && !endTime.isBlank();

        Set<Long> occupiedSeatIds = new HashSet<>();
        if (withSlot) {
            LocalDate d = BizTime.parseDate(date);
            LocalTime s = BizTime.parseTime(startTime);
            LocalTime e = BizTime.parseTime(endTime);
            if (!e.isAfter(s)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
            }
            // 先在 SQL 里按「同门店 + 同日期 + 生效状态」缩小范围，
            // 再在内存里做时间段重叠判断 —— 重叠的边界条件集中在这一处定义。
            List<Reservation> sameDay = reservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getStoreId, storeId)
                            .eq(Reservation::getReserveDate, d)
                            .in(Reservation::getStatus, ACTIVE_STATUS));
            for (Reservation r : sameDay) {
                if (overlaps(r.getStartTime(), r.getEndTime(), s, e)) {
                    occupiedSeatIds.add(r.getSeatId());
                }
            }
        }

        // 没有可用套餐的用户，前端应把座位显示为不可预约并提示去购买
        boolean hasMembership = userId != null && membershipService.findUsable(userId, storeId) != null;

        List<SeatVO> result = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            SeatType type = typeMap.get(seat.getTypeId());
            boolean occupied = occupiedSeatIds.contains(seat.getId());
            boolean physicallyUsable = seat.getStatus() != null && seat.getStatus() == 0;

            String reason = null;
            if (!physicallyUsable) {
                reason = seat.getStatus() != null && seat.getStatus() == 1 ? "维修中" : "已停用";
            } else if (occupied) {
                reason = "该时段已被预约";
            } else if (!hasMembership && withSlot) {
                reason = "暂无可用套餐";
            }

            result.add(SeatVO.builder()
                    .id(seat.getId())
                    .seatNo(seat.getSeatNo())
                    .typeId(seat.getTypeId())
                    .typeName(type == null ? null : type.getName())
                    .color(type == null ? null : type.getColor())
                    .rowIndex(seat.getRowIndex())
                    .colIndex(seat.getColIndex())
                    .status(seat.getStatus())
                    .occupied(occupied)
                    .bookable(physicallyUsable && !occupied && (!withSlot || hasMembership))
                    .reason(reason)
                    .build());
        }
        return result;
    }

    /**
     * 两个时段是否重叠。
     *
     * <p>用严格不等号：{@code aStart < bEnd && aEnd > bStart}。
     * 这样「09:00-10:00」与「10:00-11:00」<b>不算</b>冲突 ——
     * 前一场结束的瞬间后一场开始，是正常且必须允许的排期；
     * 若写成 {@code <=}，整点接续的预约会被系统全部拒掉。
     */
    static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
