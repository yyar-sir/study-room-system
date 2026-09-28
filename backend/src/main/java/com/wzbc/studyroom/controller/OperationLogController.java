package com.wzbc.studyroom.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.OperationLog;
import com.wzbc.studyroom.mapper.OperationLogMapper;
import com.wzbc.studyroom.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志查询（仅 role = 0）。
 *
 * <p>写入由 {@code OperationLogAspect} 自动完成，这里只负责读。
 * 之所以必须先有「读」的出口再谈「写」：只有写没有读的日志表和没有日志
 * 没什么区别——数据躺在库里没人看得见，等于没做。
 */
@RestController
@RequestMapping("/api/owner/operation-logs")
@RequireRole(0)
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogMapper operationLogMapper;

    /** 一次最多返回 500 条，避免前端一次性拉全表 */
    private static final int MAX_LIMIT = 500;

    @GetMapping
    public Result<List<OperationLog>> list(@RequestParam(required = false) String module,
                                           @RequestParam(required = false) Integer success,
                                           @RequestParam(defaultValue = "100") int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<OperationLog>()
                .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
                .eq(success != null, OperationLog::getSuccess, success)
                .orderByDesc(OperationLog::getId)
                // size 已经夹在 [1, 500]，不存在拼接注入
                .last("LIMIT " + size);
        return Result.ok(operationLogMapper.selectList(w));
    }
}
