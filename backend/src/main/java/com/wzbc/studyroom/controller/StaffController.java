package com.wzbc.studyroom.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.CheckinRecord;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.entity.ViolationRecord;
import com.wzbc.studyroom.mapper.CheckinRecordMapper;
import com.wzbc.studyroom.mapper.ConsumptionRecordMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.mapper.ViolationRecordMapper;
import com.wzbc.studyroom.security.LoginUser;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.service.NotificationService;
import com.wzbc.studyroom.service.ReservationAssembler;
import com.wzbc.studyroom.service.ReservationService;
import com.wzbc.studyroom.vo.ReservationVO;
import com.wzbc.studyroom.vo.UserMembershipVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台/经营者日常操作：座位状态管理、签到协助、违规处理。
 *
 * <p><b>数据访问范围</b>：前台只能操作自己所属门店的座位与违规记录。
 * 这里不是靠前端隐藏按钮，而是后端每次都用 {@code UserContext.storeId()} 过滤；
 * 经营者（role 0）则可跨门店。
 */
@RestController
@RequestMapping("/api/staff")
@RequireRole({0, 1})
@RequiredArgsConstructor
public class StaffController {

    private final SeatMapper seatMapper;
    private final ViolationRecordMapper violationMapper;
    private final ReservationMapper reservationMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final ConsumptionRecordMapper consumptionRecordMapper;
    private final UserMapper userMapper;
    private final ReservationAssembler assembler;
    private final MembershipService membershipService;
    private final NotificationService notificationService;
    private final ReservationService reservationService;

    // ------------------------------------------------------------------
    //  座位状态管理
    // ------------------------------------------------------------------

    /** 座位状态列表（可按门店过滤；前台强制为自己门店） */
    @GetMapping("/seats")
    public Result<List<Seat>> seats(@RequestParam(required = false) Long storeId) {
        Long scope = scopedStoreId(storeId);
        return Result.ok(seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(scope != null, Seat::getStoreId, scope)
                .orderByAsc(Seat::getRowIndex)
                .orderByAsc(Seat::getColIndex)));
    }

    /**
     * 调整座位状态：0可用 1维修中 2已停用。
     * 前台只能改自己门店的座位，跨门店直接 403。
     */
    @PostMapping("/seats/{id}/status")
    public Result<Void> updateSeatStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Seat seat = seatMapper.selectById(id);
        if (seat == null) {
            throw new BizException(ErrorCode.SEAT_UNAVAILABLE, "座位不存在");
        }
        assertInScope(seat.getStoreId());

        Object s = body.get("status");
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 status 参数");
        }
        int status;
        try {
            status = Integer.parseInt(s.toString());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 必须为数字");
        }
        if (status < 0 || status > 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0可用 / 1维修中 / 2已停用");
        }

        Seat patch = new Seat();
        patch.setId(id);
        patch.setStatus(status);
        Object remark = body.get("remark");
        patch.setRemark(remark == null ? null : remark.toString());
        seatMapper.updateById(patch);
        return Result.ok();
    }

    // ------------------------------------------------------------------
    //  签到协助
    // ------------------------------------------------------------------

    /**
     * 今日到店情况：本门店当天全部预约。
     * 前台需要一眼看到「谁该来、谁已经到、谁还没来」。
     */
    @GetMapping("/reservations/today")
    public Result<List<ReservationVO>> todayReservations(@RequestParam(required = false) Long storeId) {
        Long scope = scopedStoreId(storeId);
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, LocalDate.now())
                .eq(scope != null, Reservation::getStoreId, scope)
                .orderByAsc(Reservation::getStartTime));
        return Result.ok(assembler.toVOList(list));
    }

    /**
     * 按手机号查该用户「使用中」的预约，供前台协助签退。
     *
     * <p>为什么需要这个接口：用户到店签退时报的是手机号或昵称，
     * 而签退接口需要 reservationId。没有本接口，前台就只能靠签到那一步
     * 临时记住的 id —— 用户下次再来（或换个前台）就查不到了，流程断掉。
     */
    @GetMapping("/reservations/active")
    public Result<List<ReservationVO>> activeByPhone(@RequestParam String phone) {
        User user = findByPhone(phone);
        Long scope = scopedStoreId(null);
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, user.getId())
                .eq(Reservation::getStatus, 1)              // 1 使用中
                .eq(scope != null, Reservation::getStoreId, scope));
        return Result.ok(assembler.toVOList(list));
    }

    /**
     * 前台代用户取消预约（处理异常订单）。
     *
     * <p>补上这条链路的原因：任务书要求前台能「处理预约异常订单」，但此前
     * 取消接口只有用户侧一个，且按当前登录用户校验归属——用户约错时段、
     * 座位临时故障需要换位时，前台没有任何手段，只能让用户自己想办法。
     *
     * <p>与用户自助取消的差异（不受取消时限约束、可跨用户操作）见
     * {@link com.wzbc.studyroom.service.ReservationService#cancelByStaff}。
     */
    @PostMapping("/reservations/{id}/cancel")
    public Result<Void> cancelReservation(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("reason");
        String reason = raw == null ? null : String.valueOf(raw);

        // scope 传 null 表示经营者（不限门店）；前台取自己的门店，未分配门店直接拒绝
        Long scope = scopedStoreId(null);
        LoginUser me = UserContext.get();
        reservationService.cancelByStaff(id, reason, scope, me == null ? null : me.getPhone());
        return Result.ok();
    }

    // ------------------------------------------------------------------
    //  违规处理
    // ------------------------------------------------------------------

    /** 违规记录列表（前台只看本门店） */
    @GetMapping("/violations")
    public Result<List<ViolationRecord>> violations(@RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Long storeId) {
        Long scope = scopedStoreId(storeId);
        LambdaQueryWrapper<ViolationRecord> w = new LambdaQueryWrapper<ViolationRecord>()
                .eq(scope != null, ViolationRecord::getStoreId, scope)
                .orderByDesc(ViolationRecord::getCreatedAt)
                .last("LIMIT 200");
        if (status != null) {
            w.eq(ViolationRecord::getStatus, status);
        }
        return Result.ok(violationMapper.selectList(w));
    }

    /** 处理违规（前台核实、登记处理意见并通知用户） */
    @PostMapping("/violations/{id}/handle")
    public Result<Void> handleViolation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ViolationRecord v = violationMapper.selectById(id);
        if (v == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违规记录不存在");
        }
        assertInScope(v.getStoreId());
        if (v.getStatus() != null && v.getStatus() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该违规已处理，请勿重复提交");
        }

        Object remark = body.get("handleRemark");
        Object penalty = body.get("penalty");

        ViolationRecord patch = new ViolationRecord();
        patch.setId(id);
        patch.setStatus(1);
        patch.setHandlerId(UserContext.userId());
        patch.setHandleRemark(remark == null ? null : remark.toString());
        if (penalty != null) {
            patch.setPenalty(penalty.toString());
        }
        patch.setHandledAt(LocalDateTime.now());
        violationMapper.updateById(patch);

        notificationService.send(v.getUserId(), NotificationService.TYPE_VIOLATION,
                "违规处理结果通知",
                "你的违规记录已由前台核实处理："
                        + (patch.getHandleRemark() == null ? "无补充说明" : patch.getHandleRemark()),
                "/user/reservations");
        return Result.ok();
    }

    // ------------------------------------------------------------------
    //  会员业务办理
    // ------------------------------------------------------------------

    /**
     * 按手机号查用户及其套餐权益，供前台接待时核对。
     */
    @GetMapping("/members/by-phone")
    public Result<Map<String, Object>> memberByPhone(@RequestParam String phone) {
        User user = findByPhone(phone);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", user.getId());
        m.put("phone", user.getPhone());
        m.put("nickname", user.getNickname());
        m.put("realName", user.getRealName());
        m.put("totalStudyMinutes", user.getTotalStudyMinutes());
        m.put("memberships", membershipService.myMemberships(user.getId()));
        return Result.ok(m);
    }

    /**
     * 前台代客开通 / 续费套餐。
     *
     * <p>刻意复用 {@code MembershipService.grant}（内部与用户自助购买同一段逻辑），
     * 保证代开同样会落订单、落权益、记账本流水 —— 否则前台开的卡
     * 不会出现在用户的消费明细里，对账时会对不上。
     */
    @PostMapping("/members/grant")
    public Result<UserMembershipVO> grantMembership(@RequestBody Map<String, Object> body) {
        User user = findByPhone(str(body.get("phone")));
        Object planIdObj = body.get("planId");
        if (planIdObj == null || "".equals(planIdObj)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要办理的套餐");
        }
        Long planId = Long.valueOf(planIdObj.toString());

        Integer payType = null;
        Object pt = body.get("payType");
        if (pt != null && !"".equals(pt)) {
            payType = Integer.valueOf(pt.toString());
        }

        UserMembershipVO vo = membershipService.grant(user.getId(), planId, payType);
        notificationService.send(user.getId(), NotificationService.TYPE_SYSTEM,
                "会员业务办理成功",
                "前台已为你办理「" + vo.getPlanName() + "」，有效期至 "
                        + (vo.getExpireTime() == null ? "-" : vo.getExpireTime().toLocalDate())
                        + "，剩余次数：" + (vo.getRemainTimes() == null || vo.getRemainTimes() == -1
                        ? "不限次" : vo.getRemainTimes()),
                "/user/plans");
        return Result.ok(vo);
    }

    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  记录查询（前台核对当日到店与营收）
    // ------------------------------------------------------------------

    /**
     * 某天的预约记录。
     * 返回里带上手机号，前台核对时要靠它认人 —— 只给 userId 是没法用的。
     */
    @GetMapping("/records/reservations")
    public Result<List<Map<String, Object>>> recordReservations(@RequestParam(required = false) String date,
                                                                @RequestParam(required = false) Long storeId) {
        LocalDate d = parseDate(date);
        Long scope = scopedStoreId(storeId);
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, d)
                .eq(scope != null, Reservation::getStoreId, scope)
                .orderByAsc(Reservation::getStartTime));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Reservation r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("reservationNo", r.getReservationNo());
            m.put("phone", phoneOf(r.getUserId()));
            m.put("seatId", r.getSeatId());
            m.put("startTime", r.getStartTime());
            m.put("endTime", r.getEndTime());
            m.put("status", r.getStatus());
            m.put("statusText", ReservationAssembler.statusText(r.getStatus()));
            m.put("checkinCode", r.getCheckinCode());
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 某天的签到日志（含时长），前台据此核对当日到店人次 */
    @GetMapping("/records/checkins")
    public Result<List<Map<String, Object>>> recordCheckins(@RequestParam(required = false) String date,
                                                            @RequestParam(required = false) Long storeId) {
        LocalDate d = parseDate(date);
        Long scope = scopedStoreId(storeId);
        List<CheckinRecord> list = checkinRecordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                .ge(CheckinRecord::getCheckinTime, d.atStartOfDay())
                .lt(CheckinRecord::getCheckinTime, d.plusDays(1).atStartOfDay())
                .orderByAsc(CheckinRecord::getCheckinTime));
        List<Map<String, Object>> out = new ArrayList<>();
        for (CheckinRecord c : list) {
            // 签到记录本身没有 store_id，通过预约归属门店来限定范围
            Reservation r = reservationMapper.selectById(c.getReservationId());
            if (scope != null && (r == null || !scope.equals(r.getStoreId()))) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("phone", phoneOf(c.getUserId()));
            m.put("seatId", c.getSeatId());
            m.put("checkinTime", c.getCheckinTime());
            m.put("checkoutTime", c.getCheckoutTime());
            m.put("checkinType", c.getCheckinType());
            m.put("durationMinutes", c.getDurationMinutes());
            m.put("checkedOut", c.getCheckoutTime() != null);
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 某天的消费流水（会员消费与权益扣减），前台据此核对当日营收 */
    @GetMapping("/records/consumptions")
    public Result<List<Map<String, Object>>> recordConsumptions(@RequestParam(required = false) String date,
                                                                @RequestParam(required = false) Long storeId) {
        LocalDate d = parseDate(date);
        Long scope = scopedStoreId(storeId);
        List<ConsumptionRecord> list = consumptionRecordMapper.selectList(
                new LambdaQueryWrapper<ConsumptionRecord>()
                        .ge(ConsumptionRecord::getCreatedAt, d.atStartOfDay())
                        .lt(ConsumptionRecord::getCreatedAt, d.plusDays(1).atStartOfDay())
                        .orderByDesc(ConsumptionRecord::getCreatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConsumptionRecord c : list) {
            if (scope != null && c.getStoreId() != null && !scope.equals(c.getStoreId())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("phone", phoneOf(c.getUserId()));
            m.put("bizType", c.getBizType());
            m.put("amount", c.getAmount());
            m.put("timesDelta", c.getTimesDelta());
            m.put("balanceTimes", c.getBalanceTimes());
            m.put("remark", c.getRemark());
            m.put("createdAt", c.getCreatedAt());
            out.add(m);
        }
        return Result.ok(out);
    }

    // ------------------------------------------------------------------

    private LocalDate parseDate(String date) {
        return (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
    }

    private String phoneOf(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getPhone();
    }

    /** 按手机号定位账号；找不到时给出明确业务提示而不是空指针 */
    private User findByPhone(String phone) {
        String p = phone == null ? "" : phone.trim();
        if (p.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入手机号");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, p).last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未找到该手机号对应的账号");
        }
        return user;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** 前台强制限定为自己门店；经营者可选任意门店 */
    private Long scopedStoreId(Long requested) {
        if (UserContext.isOwner()) {
            return requested;
        }
        Long own = UserContext.storeId();
        if (own == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号未分配门店，无法操作门店数据");
        }
        return own;
    }

    private void assertInScope(Long storeId) {
        if (UserContext.isOwner()) {
            return;
        }
        Long own = UserContext.storeId();
        if (own == null || !own.equals(storeId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作本门店的数据");
        }
    }
}
