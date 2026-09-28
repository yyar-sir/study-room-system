package com.wzbc.studyroom.service;

import com.wzbc.studyroom.dto.PurchaseDTO;
import com.wzbc.studyroom.entity.ConsumptionRecord;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.entity.UserMembership;
import com.wzbc.studyroom.vo.UserMembershipVO;

import java.util.List;

public interface MembershipService {

    /** 上架中的套餐列表（公开，用于套餐页展示） */
    List<MembershipPlan> listAvailablePlans();

    /**
     * 购买 / 续费套餐。
     * <p>若该用户已有同套餐且未过期的权益，则<b>顺延有效期并累加次数</b>（续费），
     * 而不是新建一条 —— 否则用户的权益会被拆成多条，到期提醒和剩余次数都会算错。
     */
    UserMembershipVO purchase(Long userId, PurchaseDTO dto);

    /** 我的套餐列表（含已失效，用于个人中心展示历史） */
    List<UserMembershipVO> myMemberships(Long userId);

    /**
     * 找出当前可用于预约的权益：状态生效、未过期、次数未用完。
     * <p>多个可用时返回<b>最先到期</b>的那个 —— 先消耗快过期的权益，
     * 对用户更有利（避免权益白白过期）。
     *
     * @return 无可用权益时返回 null，由调用方决定抛什么错误
     */
    UserMembership findUsable(Long userId, Long storeId);

    /** 我的消费/权益流水（账本明细）—— 扣费必须可解释，否则用户会认为系统在乱扣 </p> */
    List<ConsumptionRecord> myRecords(Long userId);

    /** 全部套餐（含已下架，供经营端配置页面管理） */
    List<MembershipPlan> listAllPlans();

    /**
     * 新增或更新套餐（经营端配置）。
     * <p>id 为空则新建，否则更新；下架用 status=0，而不是删除记录 ——
     * 历史订单与用户权益都引用了套餐，删掉会让历史数据失去依据。
     */
    MembershipPlan savePlan(MembershipPlan plan);

    /** 代客开通/续费（前台为到店用户办理，与用户自助购买走同一段口径） */
    UserMembershipVO grant(Long userId, Long planId, Integer payType);
}
