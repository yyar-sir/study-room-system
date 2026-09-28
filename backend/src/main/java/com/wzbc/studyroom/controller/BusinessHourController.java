package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.BusinessHour;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.service.BusinessHourService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店营业时段配置（仅 role = 0）。
 *
 * <p>与「门店营业时间」（store.open_time/close_time）的区别：
 * 那两个字段是一天的总边界，这里配的是边界内部的分段与可预约开关。
 * 未配置时预约校验自动回落到总边界，所以本页是「可选的精细化配置」而非必填。
 */
@RestController
@RequestMapping("/api/owner/business-hours")
@RequireRole(0)
@RequiredArgsConstructor
public class BusinessHourController {

    private final BusinessHourService businessHourService;

    @GetMapping
    public Result<List<BusinessHour>> list(@RequestParam Long storeId,
                                           @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.ok(businessHourService.listByStore(storeId, includeDisabled));
    }

    @PostMapping
    public Result<BusinessHour> save(@RequestBody BusinessHour businessHour) {
        return Result.ok(businessHourService.save(businessHour));
    }

    @PostMapping("/{id}/remove")
    public Result<Void> remove(@PathVariable Long id) {
        businessHourService.remove(id);
        return Result.ok();
    }
}
