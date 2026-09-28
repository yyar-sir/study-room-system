package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预约选座入参。
 *
 * <p>日期与时间刻意用 String 而不是 LocalDate/LocalTime：
 * 前端 el-time-select 提交的是 {@code "09:00"}，而 Jackson 默认要求 ISO 的
 * {@code "09:00:00"}，直接用 LocalTime 会反序列化失败且报错信息很难懂。
 * 这里统一收 String，在 Service 层用宽松的解析器兼容 {@code HH:mm} 与 {@code HH:mm:ss}，
 * 格式错误时抛业务异常给出明确文案。
 */
@Data
public class ReserveDTO {

    @NotNull(message = "请选择座位")
    private Long seatId;

    /** 预约日期，格式 yyyy-MM-dd */
    @NotBlank(message = "请选择预约日期")
    private String reserveDate;

    /** 开始时间，格式 HH:mm 或 HH:mm:ss */
    @NotBlank(message = "请选择开始时间")
    private String startTime;

    /** 结束时间，格式 HH:mm 或 HH:mm:ss */
    @NotBlank(message = "请选择结束时间")
    private String endTime;
}
