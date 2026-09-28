package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.BizTime;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.dto.PurchaseDTO;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.MemberOrder;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.entity.UserMembership;
import com.wzbc.studyroom.mapper.ConsumptionRecordMapper;
import com.wzbc.studyroom.mapper.MemberOrderMapper;
import com.wzbc.studyroom.mapper.MembershipPlanMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.mapper.UserMembershipMapper;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.vo.UserMembershipVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final MembershipPlanMapper planMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final MemberOrderMapper orderMapper;
    private final ConsumptionRecordMapper consumptionMapper;
    private final UserMapper userMapper;

    @Override
    public List<MembershipPlan> listAvailablePlans() {
        return planMapper.selectList(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getStatus, 1)
                .orderByAsc(MembershipPlan::getSort));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserMembershipVO purchase(Long userId, PurchaseDTO dto) {
        return purchaseInternal(userId, dto.getPlanId(), dto.getPayType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserMembershipVO grant(Long userId, Long planId, Integer payType) {
        return purchaseInternal(userId, planId, payType);
    }

    @Override
    public List<MembershipPlan> listAllPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<MembershipPlan>()
                .orderByAsc(MembershipPlan::getSort)
                .orderByAsc(MembershipPlan::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MembershipPlan savePlan(MembershipPlan plan) {
        if (plan.getName() == null || plan.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "套餐名称不能为空");
        }
        if (plan.getPrice() == null || plan.getPrice().signum() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "套餐价格不能为空且不能为负数");
        }
        if (plan.getId() == null) {
            if (plan.getStatus() == null) {
                plan.setStatus(1);
            }
            if (plan.getDurationDays() == null) {
                plan.setDurationDays(30);
            }
            if (plan.getTotalTimes() == null) {
                plan.setTotalTimes(-1);
            }
            if (plan.getDailyLimit() == null) {
                plan.setDailyLimit(1);
            }
            planMapper.insert(plan);
        } else {
            if (planMapper.selectById(plan.getId()) == null) {
                throw new BizException(ErrorCode.PLAN_UNAVAILABLE, "套餐不存在");
            }
            planMapper.updateById(plan);
        }
        return planMapper.selectById(plan.getId());
    }

    /**
     * 购买与「前台代客开通」共用同一段逻辑。
     *
     * <p>刻意抽成 private 方法而<b>不</b>在本方法上加 {@code @Transactional}：
     * Spring 的声明式事务基于代理，同一个 Bean 内部自调用不会经过代理，
     * 加在这里的事务注解【不会生效】。因此事务边界放在上面两个 public 入口
     * （purchase / grant）上，它们才是从外部进入的调用点。
     *
     * <p>两条路径共用本方法也保证了口径一致：都要落订单、落权益、记账本流水，
     * 否则前台代开的卡就不会出现在用户的消费明细里。
     */
    private UserMembershipVO purchaseInternal(Long userId, Long planId, Integer payType) {
        MembershipPlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getStatus() == null || plan.getStatus() != 1) {
            throw new BizException(ErrorCode.PLAN_UNAVAILABLE, "套餐不存在或已下架");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "账号不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        String orderNo = BizTime.serialNo("SO");

        // ① 订单。毕设不接真实支付渠道，payType 默认模拟支付，下单即视为支付成功。
        MemberOrder order = new MemberOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPlanId(plan.getId());
        order.setStoreId(user.getStoreId());
        order.setAmount(plan.getPrice());
        order.setPayType(payType == null ? 1 : payType);
        order.setPayStatus(1);
        order.setPayTime(now);
        orderMapper.insert(order);

        // ② 权益：同套餐未过期则续费顺延，否则新建
        UserMembership membership = findRenewable(userId, plan.getId(), now);
        int timesDelta;
        if (membership != null) {
            LocalDateTime base = membership.getExpireTime().isAfter(now) ? membership.getExpireTime() : now;
            membership.setExpireTime(base.plusDays(plan.getDurationDays()));
            if (plan.getTotalTimes() != null && plan.getTotalTimes() > 0) {
                int remain = membership.getRemainTimes() == null ? 0 : membership.getRemainTimes();
                membership.setRemainTimes(remain + plan.getTotalTimes());
                membership.setTotalTimes((membership.getTotalTimes() == null ? 0 : membership.getTotalTimes())
                        + plan.getTotalTimes());
            }
            membership.setStatus(1);
            userMembershipMapper.updateById(membership);
            timesDelta = plan.getTotalTimes() == null ? 0 : Math.max(plan.getTotalTimes(), 0);
        } else {
            membership = new UserMembership();
            membership.setUserId(userId);
            membership.setPlanId(plan.getId());
            membership.setOrderId(order.getId());
            // 套餐名快照：套餐将来改名/下架，历史权益仍显示购买时的名称
            membership.setPlanName(plan.getName());
            membership.setStartTime(now);
            membership.setExpireTime(now.plusDays(plan.getDurationDays()));
            membership.setTotalTimes(plan.getTotalTimes());
            membership.setRemainTimes(plan.getTotalTimes());
            membership.setStatus(1);
            userMembershipMapper.insert(membership);
            timesDelta = plan.getTotalTimes() == null ? 0 : Math.max(plan.getTotalTimes(), 0);
        }

        // ③ 账本流水。source_key 用订单号，天然唯一 → 重复提交不会重复记账。
        ConsumptionRecord record = new ConsumptionRecord();
        record.setUserId(userId);
        record.setMembershipId(membership.getId());
        record.setStoreId(user.getStoreId());
        record.setBizType(1);                       // 1购买
        record.setAmount(plan.getPrice());
        record.setTimesDelta(timesDelta);
        record.setBalanceTimes(membership.getRemainTimes());
        record.setSourceKey("order:" + orderNo);
        record.setRemark("购买套餐：" + plan.getName());
        consumptionMapper.insert(record);

        return toVO(membership);
    }

    @Override
    public List<UserMembershipVO> myMemberships(Long userId) {
        List<UserMembership> list = userMembershipMapper.selectList(
                new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getUserId, userId)
                        .orderByDesc(UserMembership::getCreatedAt));
        List<UserMembershipVO> result = new ArrayList<>(list.size());
        for (UserMembership m : list) {
            result.add(toVO(m));
        }
        return result;
    }

    @Override
    public UserMembership findUsable(Long userId, Long storeId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserMembership> list = userMembershipMapper.selectList(
                new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getUserId, userId)
                        .eq(UserMembership::getStatus, 1)
                        .gt(UserMembership::getExpireTime, now)
                        // remain_times = -1 表示不限次，同样可用
                        .and(w -> w.gt(UserMembership::getRemainTimes, 0)
                                .or().eq(UserMembership::getRemainTimes, -1))
                        // 先消耗最先到期的权益，避免权益被放到过期
                        .orderByAsc(UserMembership::getExpireTime)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private UserMembership findRenewable(Long userId, Long planId, LocalDateTime now) {
        List<UserMembership> list = userMembershipMapper.selectList(
                new LambdaQueryWrapper<UserMembership>()
                        .eq(UserMembership::getUserId, userId)
                        .eq(UserMembership::getPlanId, planId)
                        .eq(UserMembership::getStatus, 1)
                        .gt(UserMembership::getExpireTime, now)
                        .orderByDesc(UserMembership::getExpireTime)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<ConsumptionRecord> myRecords(Long userId) {
        return consumptionMapper.selectList(new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getUserId, userId)
                .orderByDesc(ConsumptionRecord::getCreatedAt)
                .last("LIMIT 200"));
    }

    private UserMembershipVO toVO(UserMembership m) {
        LocalDateTime now = LocalDateTime.now();
        long remainDays = ChronoUnit.DAYS.between(now, m.getExpireTime());
        boolean usable = m.getStatus() != null && m.getStatus() == 1
                && m.getExpireTime() != null && m.getExpireTime().isAfter(now)
                && (m.getRemainTimes() == null || m.getRemainTimes() != 0);
        return UserMembershipVO.builder()
                .id(m.getId())
                .planId(m.getPlanId())
                .planName(m.getPlanName())
                .startTime(m.getStartTime())
                .expireTime(m.getExpireTime())
                .remainTimes(m.getRemainTimes())
                .totalTimes(m.getTotalTimes())
                .remainDays(remainDays)
                .status(m.getStatus())
                .statusText(statusText(m, now))
                .usable(usable)
                .build();
    }

    private String statusText(UserMembership m, LocalDateTime now) {
        if (m.getStatus() != null && m.getStatus() == 2) {
            return "已用完";
        }
        if (m.getStatus() != null && m.getStatus() == 3) {
            return "已退款";
        }
        if (m.getExpireTime() == null || !m.getExpireTime().isAfter(now)) {
            return "已过期";
        }
        if (m.getStatus() != null && m.getStatus() == 0) {
            return "已失效";
        }
        return "生效中";
    }
}
