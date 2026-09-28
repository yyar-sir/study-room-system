package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 购买会员套餐入参 */
@Data
public class PurchaseDTO {

    @NotNull(message = "请选择套餐")
    private Long planId;

    /** 1模拟支付 2微信 3支付宝；不传默认模拟支付（毕设不接真实支付渠道） */
    private Integer payType;
}
