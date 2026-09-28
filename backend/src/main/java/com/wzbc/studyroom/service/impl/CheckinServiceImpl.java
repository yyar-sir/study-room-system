package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.CheckinRecord;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.entity.UserMembership;
import com.wzbc.studyroom.mapper.CheckinRecordMapper;
import com.wzbc.studyroom.mapper.ConsumptionRecordMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.mapper.UserMembershipMapper;
import com.wzbc.studyroom.service.CheckinService;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.service.ReservationAssembler;
import com.wzbc.studyroom.service.SystemConfigService;
import com.wzbc.studyroom.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final ReservationMapper reservationMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final ConsumptionRecordMapper consumptionMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final UserMapper userMapper;
    private final MembershipService membershipService;
    private final SystemConfigService configService;
    private final ReservationAssembler assembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO checkin(Long operatorId, String code, boolean asStaff) {
        Reservation r = findByCode(code);
        if (r == null) {
            throw new BizException(ErrorCode.CHECKIN_CODE_INVALID, "签到码无效，请核对二维码或联系前台");
        }
        // 自助签到只能签自己的预约；前台协助可以代签
        if (!asStaff && !r.getUserId().equals(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己的预约签到");
        }
        if (r.getStatus() != null && (r.getStatus() == 1 || r.getStatus() == 2)) {
            throw new BizException(ErrorCode.ALREADY_CHECKED_IN, "该预约已签到，请勿重复操作");
        }
        if (r.getStatus() == null || r.getStatus() != 0) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID,
                    "当前状态（" + ReservationAssembler.statusText(r.getStatus()) + "）不可签到");
        }

        // 签到时间窗：太早或太晚都不允许。两个阈值都来自经营者可配的运营规则。
        int early = configService.getInt(r.getStoreId(), SystemConfigService.CHECKIN_EARLY_MINUTES, 15);
        int late = configService.getInt(r.getStoreId(), SystemConfigService.CHECKIN_LATE_MINUTES, 30);
        LocalDateTime startAt = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt.minusMinutes(early))) {
            throw new BizException(ErrorCode.CHECKIN_TIME_INVALID,
                    "还未到签到时间，最早可在开始前 " + early + " 分钟签到");
        }
        if (now.isAfter(startAt.plusMinutes(late))) {
            throw new BizException(ErrorCode.CHECKIN_TIME_INVALID,
                    "已超过签到时间窗（开始后 " + late + " 分钟内需签到），请联系前台处理");
        }

        // ① 签到记录。reservation_id 上有唯一索引，重复签到会在这里被数据库拦下。
        CheckinRecord cr = new CheckinRecord();
        cr.setReservationId(r.getId());
        cr.setUserId(r.getUserId());
        cr.setSeatId(r.getSeatId());
        cr.setCheckinTime(now);
        cr.setCheckinType(asStaff ? 2 : 1);
        cr.setOperatorId(asStaff ? operatorId : null);
        cr.setDurationMinutes(0);
        try {
            checkinRecordMapper.insert(cr);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ALREADY_CHECKED_IN, "该预约已签到，请勿重复操作");
        }

        // ② 预约进入「使用中」
        r.setStatus(1);
        reservationMapper.updateById(r);

        // ③ 扣减套餐权益 —— 任务书规定「签到…并扣减对应套餐权益」
        deductOnCheckin(r);

        return assembler.toVO(r);
    }

    /**
     * 签到扣次。
     *
     * <p>幂等由 {@code (user_id, source_key)} 唯一索引保证：source_key 用
     * {@code checkin:{预约id}}，同一笔预约无论触发多少次都只记一条流水、只扣一次次数。
     * 把幂等交给数据库，比在应用层写 if 判断可靠得多。
     */
    private void deductOnCheckin(Reservation r) {
        UserMembership membership = membershipService.findUsable(r.getUserId(), r.getStoreId());
        if (membership == null) {
            throw new BizException(ErrorCode.NO_AVAILABLE_MEMBERSHIP,
                    "没有可用的套餐权益，无法完成扣次；请联系前台办理续费");
        }

        boolean unlimited = membership.getRemainTimes() != null && membership.getRemainTimes() == -1;
        int delta = unlimited ? 0 : -1;
        int remain = membership.getRemainTimes() == null ? 0 : membership.getRemainTimes();
        int balanceAfter = unlimited ? -1 : remain + delta;

        // ★ 账本先行：先插流水，把 (user_id, source_key) 唯一约束当作"闸门"。
        //   插入冲突说明本次是重复触发 → 直接返回，余额不动。
        //   顺序很关键：若先改余额再插流水，一旦流水重复导致插入失败，
        //   余额已经被多扣了一次，而且这个多扣在事务里补不回来。
        ConsumptionRecord record = new ConsumptionRecord();
        record.setUserId(r.getUserId());
        record.setMembershipId(membership.getId());
        record.setStoreId(r.getStoreId());
        record.setBizType(2);                       // 2 预约扣次
        record.setAmount(BigDecimal.ZERO);
        record.setTimesDelta(delta);
        record.setBalanceTimes(balanceAfter);
        record.setSourceKey("checkin:" + r.getId());
        record.setRemark(unlimited
                ? "签到（不限次套餐）"
                : "签到扣次（" + membership.getPlanName() + "）");
        try {
            consumptionMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 流水已存在 → 本次是重复触发，扣次已完成，直接忽略
            return;
        }

        if (!unlimited) {
            UserMembership patch = new UserMembership();
            patch.setId(membership.getId());
            patch.setRemainTimes(balanceAfter);
            if (balanceAfter <= 0) {
                patch.setStatus(2);                 // 2 已用完
            }
            userMembershipMapper.updateById(patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO checkout(Long operatorId, Long reservationId, boolean asStaff) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "预约不存在");
        }
        if (!asStaff && !r.getUserId().equals(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己的预约签退");
        }
        if (r.getStatus() == null || r.getStatus() != 1) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID,
                    "当前状态（" + ReservationAssembler.statusText(r.getStatus()) + "）不可签退，请先签到");
        }

        List<CheckinRecord> list = checkinRecordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getReservationId, reservationId)
                .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "尚未签到，无法签退");
        }
        CheckinRecord cr = list.get(0);
        if (cr.getCheckoutTime() != null) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "该预约已签退");
        }

        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(cr.getCheckinTime(), now);
        if (minutes < 0) {
            minutes = 0;
        }
        cr.setCheckoutTime(now);
        cr.setDurationMinutes((int) minutes);
        checkinRecordMapper.updateById(cr);

        r.setStatus(2);                             // 2 已完成
        reservationMapper.updateById(r);

        // 累计学习时长（个人中心的「累计学习时长」由此而来）
        User user = userMapper.selectById(r.getUserId());
        if (user != null) {
            int total = user.getTotalStudyMinutes() == null ? 0 : user.getTotalStudyMinutes();
            User patch = new User();
            patch.setId(user.getId());
            patch.setTotalStudyMinutes(total + (int) minutes);
            userMapper.updateById(patch);
        }
        return assembler.toVO(r);
    }

    private Reservation findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getCheckinCode, code.trim().toUpperCase())
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
