package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.service.StoreService;
import com.wzbc.studyroom.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开接口（无需登录）。
 *
 * <p>统一收敛到 {@code /api/public/**} 前缀，这样 WebConfig 的白名单只需要维护一条规则，
 * 不必每加一个公开接口就回去改配置。
 *
 * <p>⚠️ 放进这个包/前缀的接口等于对全世界开放，**不要**在这里返回任何用户数据。
 * 门店名称/地址/营业时间属于对外公示信息，可以公开；座位实时占用不行（见 /api/seats，需登录）。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final StoreService storeService;
    private final SystemConfigService configService;

    /** 连通性自检：前端首页的「调用一次公开接口」按钮打这个 */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("time", LocalDateTime.now().toString());
        return Result.ok(data);
    }

    /**
     * 营业中的门店列表。
     * 用户端选座前必须先有门店可挑，而门店列表只有经营者接口是不够用的。
     */
    @GetMapping("/stores")
    public Result<List<Store>> stores() {
        return Result.ok(storeService.listActive());
    }

    /**
     * 面向用户公示的预约规则。
     *
     * <p>为什么要单独开这个接口：前端选座页需要告诉用户"最多提前几天、单次最长几小时、
     * 开始前多久可取消"。这些值都由经营者在后台配置、后端实时读取。
     * 如果前端自己硬编码一份，改了后台配置前端就不同步了 ——
     * 用户会看到"页面说能约 7 天，提交却被拒"这类自相矛盾的表现。
     * 规则只保留一个来源：配置表 → 本接口 → 前端展示与预校验。
     */
    @GetMapping("/rules")
    public Result<Map<String, Object>> rules(@RequestParam(required = false) Long storeId) {
        Map<String, Object> m = new LinkedHashMap<>();
        // ⚠️ 响应字段名用 camelCase，与 /seats、/stats/** 等接口保持一致。
        //    SystemConfigService 里那几个常量是 system_config.config_key（snake_case），
        //    只能用来查库，不能直接当字段名 —— 早期就是直接把常量 map 成了响应键，
        //    而前端按 camelCase 读，三个值全是 undefined：模板渲染成空白，
        //    而前端 `|| 默认值` 的兜底又恰好等于库里的配置，表面上「看起来正常」，
        //    实际这个接口形同虚设，违背了「规则只有一个来源」的设计初衷。
        m.put("advanceBookDays",
                configService.getInt(storeId, SystemConfigService.ADVANCE_BOOK_DAYS, 7));
        m.put("maxReserveHours",
                configService.getInt(storeId, SystemConfigService.MAX_RESERVE_HOURS, 4));
        m.put("cancelBeforeMinutes",
                configService.getInt(storeId, SystemConfigService.CANCEL_BEFORE_MINUTES, 30));
        m.put("checkinEarlyMinutes",
                configService.getInt(storeId, SystemConfigService.CHECKIN_EARLY_MINUTES, 15));
        m.put("checkinLateMinutes",
                configService.getInt(storeId, SystemConfigService.CHECKIN_LATE_MINUTES, 30));
        return Result.ok(m);
    }
}
