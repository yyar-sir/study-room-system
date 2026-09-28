package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.BizTime;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.dto.ReserveDTO;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.entity.UserMembership;
import com.wzbc.studyroom.mapper.MembershipPlanMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.StoreMapper;
import com.wzbc.studyroom.service.BusinessHourService;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.service.NotificationService;
import com.wzbc.studyroom.service.ReservationAssembler;
import com.wzbc.studyroom.service.ReservationService;
import com.wzbc.studyroom.service.SystemConfigService;
import com.wzbc.studyroom.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final StoreMapper storeMapper;
    private final MembershipPlanMapper planMapper;
    private final MembershipService membershipService;
    private final SystemConfigService configService;
    private final ReservationAssembler assembler;
    private final BusinessHourService businessHourService;
    private final NotificationService notificationService;

    /** 仍占用座位的状态：0待使用、1使用中 */
    private static final List<Integer> ACTIVE = List.of(0, 1);
    /** 计入「当日已预约次数」的状态：待使用、使用中、已完成 */
    private static final List<Integer> COUNTED = List.of(0, 1, 2);

    /** 签到码字符集：刻意剔除 0/O、1/I/L 等易混淆字符，扫码失败时用户要能口述 */
    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO create(Long userId, ReserveDTO dto) {
        LocalDate date = BizTime.parseDate(dto.getReserveDate());
        LocalTime start = BizTime.parseTime(dto.getStartTime());
        LocalTime end = BizTime.parseTime(dto.getEndTime());
        if (!end.isAfter(start)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }

        // ① 座位可用性。
        //    这里用「悲观锁」锁住座位行，把同一座位的并发预约串行化。
        //    必要性：下面的重叠校验是「先查后写」，两个并发请求可能同时查到"无冲突"
        //    然后双双插入成功（超卖）。数据库的 uk_res_active_slot 只能拦住
        //    「开始时间完全相同」的情况，拦不住 09:00-11:00 与 10:00-12:00 这类部分重叠。
        Seat seat = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getId, dto.getSeatId())
                .last("FOR UPDATE"));
        if (seat == null) {
            throw new BizException(ErrorCode.SEAT_UNAVAILABLE, "座位不存在");
        }
        if (seat.getStatus() == null || seat.getStatus() != 0) {
            throw new BizException(ErrorCode.SEAT_UNAVAILABLE,
                    seat.getStatus() != null && seat.getStatus() == 1 ? "该座位维修中，暂不可预约" : "该座位已停用");
        }

        // ② 门店可用性
        Store store = storeMapper.selectById(seat.getStoreId());
        if (store == null || store.getStatus() == null || store.getStatus() != 1) {
            throw new BizException(ErrorCode.STORE_UNAVAILABLE, "门店不存在或已停业");
        }

        // ③ 日期窗口
        LocalDate today = LocalDate.now();
        int advanceDays = configService.getInt(store.getId(), SystemConfigService.ADVANCE_BOOK_DAYS, 7);
        if (date.isBefore(today)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能预约已过去的日期");
        }
        if (date.isAfter(today.plusDays(advanceDays))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最多可提前 " + advanceDays + " 天预约");
        }

        // ④ 营业时间
        if (start.isBefore(store.getOpenTime()) || end.isAfter(store.getCloseTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "预约时间需在营业时间内（" + store.getOpenTime() + " - " + store.getCloseTime() + "）");
        }

        // ④a 营业时段细分（可预约/闭馆）。
        //     门店营业时间是「一天从几点开到几点」的总边界，这里再校验边界内部的
        //     分段：经营者可以把午休配成不可预约，预约就必须完整落在某个可预约
        //     时段内，不能跨过闭馆区间。未配置任何时段时该方法直接放行，
        //     所以对没做精细化配置的门店行为完全不变。
        businessHourService.assertReservable(store.getId(), start, end);

        // ④b 不能预约已经过去的时段。
        //     若只校验日期（date < today），晚上 21:00 仍能预约当天 08:00 的座位，
        //     到店即被爽约判定命中 —— 逻辑和体验都不成立。
        if (LocalDateTime.of(date, start).isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该时段已经开始，请选择当前时间之后的时段");
        }

        // ④c 单次预约时长上限。
        //     营业时间跨度是 08:00-22:00，若不限制时长，用户可以一次预约 14 小时
        //     把座位整天锁死，其他用户全天都约不到 —— 这是运营上必须挡住的。
        int maxHours = configService.getInt(store.getId(), SystemConfigService.MAX_RESERVE_HOURS, 4);
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(start, end);
        if (minutes > maxHours * 60L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单次预约最长 " + maxHours + " 小时，当前选择了 "
                            + String.format("%.1f", minutes / 60.0) + " 小时");
        }

        // ⑤ 冲突校验 A：同一座位同一时段只能有一条有效预约
        List<Reservation> sameSeat = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getSeatId, seat.getId())
                .eq(Reservation::getReserveDate, date)
                .in(Reservation::getStatus, ACTIVE));
        for (Reservation r : sameSeat) {
            if (SeatServiceImpl.overlaps(r.getStartTime(), r.getEndTime(), start, end)) {
                throw new BizException(ErrorCode.RESERVE_CONFLICT, "该座位在所选时段已被预约，请另选座位或时段");
            }
        }

        // ⑥ 冲突校验 B：同一用户同一时段不能坐在两个座位上
        List<Reservation> sameUser = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getReserveDate, date)
                .in(Reservation::getStatus, ACTIVE));
        for (Reservation r : sameUser) {
            if (SeatServiceImpl.overlaps(r.getStartTime(), r.getEndTime(), start, end)) {
                throw new BizException(ErrorCode.RESERVE_CONFLICT,
                        "你在该时段已有预约，不能重复预约同一时段");
            }
        }

        // ⑦ 套餐权益（预约时校验可用性，实际扣次发生在签到，见任务书功能定义）
        UserMembership membership = membershipService.findUsable(userId, store.getId());
        if (membership == null) {
            throw new BizException(ErrorCode.NO_AVAILABLE_MEMBERSHIP, "暂无可用套餐，请先购买会员套餐");
        }

        // ⑧ 每日预约次数上限
        MembershipPlan plan = planMapper.selectById(membership.getPlanId());
        int dailyLimit = (plan != null && plan.getDailyLimit() != null) ? plan.getDailyLimit() : 2;
        Long used = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getReserveDate, date)
                .in(Reservation::getStatus, COUNTED));
        if (used != null && used >= dailyLimit) {
            throw new BizException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "当日预约次数已达上限（" + dailyLimit + " 次）");
        }

        // ⑨ 落库
        Reservation r = new Reservation();
        r.setReservationNo(BizTime.serialNo("RS"));
        r.setUserId(userId);
        r.setStoreId(seat.getStoreId());
        r.setSeatId(seat.getId());
        r.setMembershipId(membership.getId());
        r.setReserveDate(date);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(0);
        r.setCheckinCode(newCheckinCode());
        r.setSource(1);
        try {
            reservationMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过上面的检查，最终由 uk_res_seat_slot 唯一索引兜底。
            // 把数据库的完整性错误翻译成用户能看懂的业务提示，而不是抛 500。
            throw new BizException(ErrorCode.RESERVE_CONFLICT, "该座位刚被他人预约，请重新选择");
        }
        return assembler.toVO(r);
    }

    @Override
    public List<ReservationVO> myReservations(Long userId, Integer status) {
        LambdaQueryWrapper<Reservation> w = new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getReserveDate)
                .orderByDesc(Reservation::getStartTime);
        if (status != null) {
            w.eq(Reservation::getStatus, status);
        }
        return assembler.toVOList(reservationMapper.selectList(w));
    }

    @Override
    public ReservationVO detail(Long userId, Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "预约不存在");
        }
        return assembler.toVO(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long id, String reason) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "预约不存在");
        }
        if (!r.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能取消自己的预约");
        }
        if (r.getStatus() == null || r.getStatus() != 0) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID,
                    "当前状态（" + ReservationAssembler.statusText(r.getStatus()) + "）不可取消");
        }

        int cancelBefore = configService.getInt(r.getStoreId(), SystemConfigService.CANCEL_BEFORE_MINUTES, 30);
        LocalDateTime startAt = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
        if (LocalDateTime.now().isAfter(startAt.minusMinutes(cancelBefore))) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID,
                    "距开始不足 " + cancelBefore + " 分钟，无法取消；请到店后正常签到或联系前台");
        }

        r.setStatus(3);
        r.setCancelReason(reason == null || reason.isBlank() ? "用户主动取消" : reason);
        reservationMapper.updateById(r);
        // 注：扣次发生在签到环节，因此这里无需返还次数。
        //     这正是「账本制 + 后扣费」带来的好处——取消逻辑天然不需要补偿。
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByStaff(Long id, String reason, Long scopeStoreId, String operatorName) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID, "预约不存在");
        }

        // 数据范围：前台只能动本门店的预约。这里在服务层再校验一次而不是只靠
        // Controller —— 越权判断放在离数据最近的地方，后续新增调用方也不会漏。
        if (scopeStoreId != null && !scopeStoreId.equals(r.getStoreId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该预约不属于你所在的门店");
        }

        if (r.getStatus() == null || r.getStatus() != 0) {
            throw new BizException(ErrorCode.RESERVE_STATE_INVALID,
                    "当前状态（" + ReservationAssembler.statusText(r.getStatus())
                            + "）不可取消；已签到的请走签退流程");
        }

        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写代取消的原因");
        }

        // 刻意不做「距开始 N 分钟」校验：前台处理的就是用户在最后时刻来不了这类
        // 异常情况，按用户规则拦住反而让前台无计可施。
        r.setStatus(3);
        r.setCancelReason("前台代取消（" + (operatorName == null ? "前台" : operatorName) + "）："
                + reason.trim());
        reservationMapper.updateById(r);

        // 必须通知用户：预约是被别人取消的，不告知会让用户到店后才发现约没了
        notificationService.send(r.getUserId(), NotificationService.TYPE_SYSTEM,
                "预约已被取消",
                "你的预约（" + r.getReserveDate() + " " + r.getStartTime() + "-" + r.getEndTime()
                        + "）已由门店工作人员取消。原因：" + reason.trim()
                        + "。如有疑问请联系前台。",
                "/user/reservations");
    }

    private String newCheckinCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }
}
