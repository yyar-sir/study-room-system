package com.wzbc.studyroom.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.entity.AiChatRecord;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.Notification;
import com.wzbc.studyroom.entity.OperationLog;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.UserMembership;
import com.wzbc.studyroom.entity.ViolationRecord;
import com.wzbc.studyroom.mapper.AiChatRecordMapper;
import com.wzbc.studyroom.mapper.ConsumptionRecordMapper;
import com.wzbc.studyroom.mapper.NotificationMapper;
import com.wzbc.studyroom.mapper.OperationLogMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.UserMembershipMapper;
import com.wzbc.studyroom.mapper.ViolationRecordMapper;
import com.wzbc.studyroom.service.NotificationService;
import com.wzbc.studyroom.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 定时任务：爽约判定、套餐到期提醒、权益过期清理。
 *
 * <p>这些任务的共同要求是<b>幂等</b>——定时任务可能重复触发（多实例部署、手动补跑），
 * 绝不能因此重复扣用户权益。这里的做法统一是：<b>先用账本流水表的
 * {@code (user_id, source_key)} 唯一约束当"闸门"，插入成功才执行余额变更</b>。
 * 顺序很关键：如果先改余额再插流水，一旦流水插入失败（重复），
 * 余额就已经被多扣了一次，而且事务里补不回来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ReservationMapper reservationMapper;
    private final UserMembershipMapper membershipMapper;
    private final ConsumptionRecordMapper consumptionMapper;
    private final ViolationRecordMapper violationMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final SystemConfigService configService;
    private final AiChatRecordMapper aiChatRecordMapper;
    private final OperationLogMapper operationLogMapper;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_NO_SHOW = 4;

    /**
     * 爽约判定：每 5 分钟扫描一次。
     *
     * <p>判定条件：待使用 + 预约日期不晚于今天 + 已超过「开始时间 + 占座判定分钟数」。
     * 命中后：预约置为爽约 → 扣 1 次权益 → 记一条违规 → 发通知给用户。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void markNoShow() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> pending = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, STATUS_PENDING)
                .le(Reservation::getReserveDate, today));
        if (pending.isEmpty()) {
            return;
        }

        int handled = 0;
        for (Reservation r : pending) {
            int timeout = configService.getInt(r.getStoreId(),
                    SystemConfigService.OCCUPY_TIMEOUT_MINUTES, 30);
            LocalDateTime startAt = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
            if (now.isBefore(startAt.plusMinutes(timeout))) {
                continue;       // 仍在宽限期内，不算爽约
            }

            r.setStatus(STATUS_NO_SHOW);
            reservationMapper.updateById(r);

            int deducted = deductForNoShow(r);
            insertViolation(r, deducted);
            notificationService.send(r.getUserId(), NotificationService.TYPE_VIOLATION,
                    "预约爽约提醒",
                    "你的预约（" + r.getReserveDate() + " " + r.getStartTime()
                            + "）因超时未签到被判定为爽约"
                            + (deducted > 0 ? "，已扣除 1 次套餐权益。" : "。")
                            + "如有疑问请联系前台。",
                    "/user/reservations");
            handled++;
        }
        if (handled > 0) {
            log.info("爽约判定完成，处理 {} 条", handled);
        }
    }

    /**
     * 扣减爽约权益。返回实际扣减次数（0 表示不限次套餐或已扣过）。
     *
     * <p>账本先行：先插流水（唯一键当闸门），插入成功才动余额。
     */
    private int deductForNoShow(Reservation r) {
        UserMembership um = r.getMembershipId() == null ? null : membershipMapper.selectById(r.getMembershipId());
        if (um == null) {
            return 0;
        }
        boolean unlimited = um.getRemainTimes() != null && um.getRemainTimes() == -1;
        int delta = unlimited ? 0 : -1;
        int remain = um.getRemainTimes() == null ? 0 : um.getRemainTimes();
        int balanceAfter = unlimited ? -1 : remain + delta;

        ConsumptionRecord rec = new ConsumptionRecord();
        rec.setUserId(r.getUserId());
        rec.setMembershipId(um.getId());
        rec.setStoreId(r.getStoreId());
        rec.setBizType(5);                       // 5 爽约扣减
        rec.setAmount(BigDecimal.ZERO);
        rec.setTimesDelta(delta);
        rec.setBalanceTimes(balanceAfter);
        rec.setSourceKey("noshow:" + r.getId()); // 幂等键：同一笔预约只扣一次
        rec.setRemark("爽约扣减（" + um.getPlanName() + "）");
        try {
            consumptionMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            return 0;                            // 已扣过，直接返回
        }

        if (!unlimited) {
            UserMembership patch = new UserMembership();
            patch.setId(um.getId());
            patch.setRemainTimes(balanceAfter);
            if (balanceAfter <= 0) {
                patch.setStatus(2);              // 已用完
            }
            membershipMapper.updateById(patch);
        }
        return unlimited ? 0 : 1;
    }

    private void insertViolation(Reservation r, int deducted) {
        ViolationRecord v = new ViolationRecord();
        v.setUserId(r.getUserId());
        v.setReservationId(r.getId());
        v.setStoreId(r.getStoreId());
        v.setViolationType(2);                   // 2 预约未到（爽约）
        v.setDescription("预约 " + r.getReserveDate() + " " + r.getStartTime()
                + "-" + r.getEndTime() + " 超时未签到");
        v.setPenalty(deducted > 0 ? "扣除 1 次套餐权益" : "仅记录，未扣减权益");
        v.setStatus(0);                          // 待前台处理
        v.setCreatedAt(LocalDateTime.now());
        violationMapper.insert(v);
    }

    /**
     * 预约即将开始提醒：每 5 分钟扫描一次。
     *
     * <p>范围是「待使用 + 今天 + 开始时间落在未来 N 分钟内」的预约，
     * N 由运营规则 {@code reserve_remind_minutes} 决定（默认 30 分钟，
     * 配成 0 或负数即关闭该提醒）。
     *
     * <p><b>幂等靠 link 精确匹配</b>：本任务每 5 分钟跑一次，而提醒窗口有 30 分钟，
     * 同一笔预约会被扫到大约 6 次。这里把预约 id 编进 link，发送前按
     * 「同一用户 + 同类型 + 同一 link」查一次，从而一笔预约只提醒一次。
     * 之所以不用「24 小时内是否提醒过」那种时间窗判断：用户一天可能有多笔预约，
     * 时间窗会把第二笔的提醒一起吞掉。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void remindUpcomingReservations() {
        remindUpcomingReservationsNow();
    }

    /**
     * 提醒逻辑本体，返回本次实际发送的条数。
     *
     * <p>拆出这个返回值版本是为了让经营端能手工触发一次并看到结果
     * （{@code POST /api/owner/ops/run-reserve-remind}）——
     * 该任务的提醒窗口只有几十分钟，等一个调度周期可能就错过窗口了，
     * 演示和排查时必须有立即执行的入口。
     */
    public int remindUpcomingReservationsNow() {
        int minutes = configService.getInt(0L, SystemConfigService.RESERVE_REMIND_MINUTES, 30);
        if (minutes <= 0) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(minutes);

        List<Reservation> candidates = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, STATUS_PENDING)
                .eq(Reservation::getReserveDate, today));
        if (candidates.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (Reservation r : candidates) {
            LocalDateTime startAt = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
            if (startAt.isBefore(now) || startAt.isAfter(deadline)) {
                continue;   // 已经开始（归爽约判定管），或者还没到提醒窗口
            }
            String link = "/user/reservations?focus=" + r.getId();
            Long already = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getUserId, r.getUserId())
                    .eq(Notification::getType, NotificationService.TYPE_RESERVE_REMIND)
                    .eq(Notification::getLink, link));
            if (already != null && already > 0) {
                continue;
            }

            notificationService.send(r.getUserId(), NotificationService.TYPE_RESERVE_REMIND,
                    "预约即将开始",
                    "你的预约（" + r.getReserveDate() + " " + r.getStartTime() + "-"
                            + r.getEndTime() + "）即将开始，请准时到店并扫码签到。"
                            + "开场后超过占座判定时间未签到会被记为爽约。",
                    link);
            sent++;
        }
        if (sent > 0) {
            log.info("预约开始提醒已发送 {} 条", sent);
        }
        return sent;
    }

    /**
     * 套餐到期提醒：每天 08:00。
     *
     * <p>幂等：同一用户 24 小时内只提醒一次，避免任务重复触发导致消息轰炸。
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void remindExpiringPlans() {
        LocalDateTime now = LocalDateTime.now();
        int days = configService.getInt(0L, SystemConfigService.PLAN_EXPIRE_DAYS, 3);
        LocalDateTime deadline = now.plusDays(days);

        List<UserMembership> expiring = membershipMapper.selectList(
                new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getStatus, 1)
                        .gt(UserMembership::getExpireTime, now)
                        .le(UserMembership::getExpireTime, deadline));
        int sent = 0;
        for (UserMembership um : expiring) {
            Long recent = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getUserId, um.getUserId())
                    .eq(Notification::getType, NotificationService.TYPE_PLAN_EXPIRE)
                    .ge(Notification::getCreatedAt, now.minusHours(24)));
            if (recent != null && recent > 0) {
                continue;                        // 24 小时内已提醒过
            }
            long remainDays = ChronoUnit.DAYS.between(now, um.getExpireTime());
            notificationService.send(um.getUserId(), NotificationService.TYPE_PLAN_EXPIRE,
                    "套餐即将到期",
                    "你的「" + um.getPlanName() + "」将于 " + um.getExpireTime().toLocalDate()
                            + " 到期（剩余 " + Math.max(remainDays, 0) + " 天），"
                            + "为避免影响预约，请及时续费。",
                    "/user/plans");
            sent++;
        }
        if (sent > 0) {
            log.info("套餐到期提醒已发送 {} 条", sent);
        }
    }

    /** 权益过期清理：每天 00:10，把已过期的生效中权益置为失效 */
    @Scheduled(cron = "0 10 0 * * ?")
    public void expireMemberships() {
        List<UserMembership> expired = membershipMapper.selectList(
                new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getStatus, 1)
                        .le(UserMembership::getExpireTime, LocalDateTime.now()));
        for (UserMembership um : expired) {
            UserMembership patch = new UserMembership();
            patch.setId(um.getId());
            patch.setStatus(0);                  // 0 已失效
            membershipMapper.updateById(patch);
        }
        if (!expired.isEmpty()) {
            log.info("权益过期清理完成，处理 {} 条", expired.size());
        }
    }

    /**
     * 记录保留清理：每天 03:30。
     *
     * <p>ai_chat_record 与 operation_log 都是**只增不减**的表：前者每次提问写两条
     * （用户一次、助手一次），后者每一次写操作写一条。不清理的话，演示环境跑几天
     * 就会堆积上万行，还会拖慢备份与日志页。
     *
     * <p>保留天数走运营规则配置，<b>配成 0 或负数表示不清理该表</b>——
     * 审计要求高的场景可以把操作日志设为永久保留，而不必改代码。
     * 两张表的默认值刻意不同：对话记录 30 天（它只是体验功能），
     * 操作日志 90 天（它是审计依据，出了事至少能回溯一个季度）。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanOldRecords() {
        int chatDays = configService.getInt(0L, SystemConfigService.AI_CHAT_RETAIN_DAYS, 30);
        if (chatDays > 0) {
            int n = aiChatRecordMapper.delete(new LambdaQueryWrapper<AiChatRecord>()
                    .lt(AiChatRecord::getCreatedAt, LocalDateTime.now().minusDays(chatDays)));
            if (n > 0) {
                log.info("AI 对话记录清理完成，删除 {} 条（保留 {} 天）", n, chatDays);
            }
        }

        int logDays = configService.getInt(0L, SystemConfigService.OPERATION_LOG_RETAIN_DAYS, 90);
        if (logDays > 0) {
            int n = operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                    .lt(OperationLog::getCreatedAt, LocalDateTime.now().minusDays(logDays)));
            if (n > 0) {
                log.info("操作日志清理完成，删除 {} 条（保留 {} 天）", n, logDays);
            }
        }
    }

    /** 供手工触发/测试使用（不参与定时调度） */
    public void runNoShowCheckNow() {
        try {
            markNoShow();
        } catch (BizException e) {
            log.warn("手动爽约判定失败：{}", e.getMessage());
        }
    }
}
