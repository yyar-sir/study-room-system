package com.wzbc.studyroom.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间解析工具。
 *
 * <p>为什么要单独收口：前端的 <b>el-time-select 提交的是 {@code "09:00"}</b>，
 * 而 Java 的 {@code LocalTime.parse} 默认只认 ISO 的 {@code "09:00:00"}。
 * 若各处直接 parse，就会出现「有的页面能选座、有的页面报 400」这种
 * 取决于前端控件实现的诡异不一致。这里统一兼容两种写法。
 */
public final class BizTime {

    private BizTime() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 解析 yyyy-MM-dd */
    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日期不能为空");
        }
        try {
            return LocalDate.parse(s.trim(), DATE);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日期格式应为 yyyy-MM-dd：" + s);
        }
    }

    /** 解析 HH:mm 或 HH:mm:ss */
    public static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间不能为空");
        }
        String t = s.trim();
        try {
            if (t.length() == 5) {
                return LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm"));
            }
            return LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间格式应为 HH:mm 或 HH:mm:ss：" + s);
        }
    }

    /** 把「日期 + 时间」合成 LocalDateTime，用于和 now 比较 */
    public static LocalDateTime at(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time);
    }

    /** 生成带前缀的流水号：前缀 + yyyyMMddHHmmss + 4 位随机数 */
    public static String serialNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + rand;
    }
}
