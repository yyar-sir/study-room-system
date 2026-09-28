package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.schedule.ScheduledTasks;
import com.wzbc.studyroom.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营端运维操作（仅经营者）。
 *
 * <p>提供「立即执行」的手工入口。定时任务按 cron 跑，但演示与排查时
 * 需要能立刻触发一次，否则要等到下一个调度点——尤其是「预约开始前提醒」
 * 这类窗口只有几十分钟的任务，等一个周期可能就错过窗口了。
 */
@RestController
@RequestMapping("/api/owner/ops")
@RequireRole(0)
@RequiredArgsConstructor
public class OwnerOpsController {

    private final ScheduledTasks scheduledTasks;

    /** 立即执行一次爽约判定（幂等：已扣过的不会重复扣） */
    @PostMapping("/run-noshow-check")
    public Result<String> runNoShowCheck() {
        scheduledTasks.runNoShowCheckNow();
        return Result.ok("爽约判定已执行");
    }

    /** 立即执行一次「预约即将开始」提醒检查（幂等：同一笔预约只提醒一次） */
    @PostMapping("/run-reserve-remind")
    public Result<String> runReserveRemind() {
        int sent = scheduledTasks.remindUpcomingReservationsNow();
        return Result.ok(sent > 0 ? "已发送 " + sent + " 条预约提醒" : "当前没有需要提醒的预约");
    }
}
