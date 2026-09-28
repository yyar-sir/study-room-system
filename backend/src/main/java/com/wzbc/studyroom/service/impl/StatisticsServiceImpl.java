package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wzbc.studyroom.entity.CheckinRecord;
import com.wzbc.studyroom.entity.MemberOrder;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.SeatType;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.CheckinRecordMapper;
import com.wzbc.studyroom.mapper.MemberOrderMapper;
import com.wzbc.studyroom.mapper.MembershipPlanMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.SeatTypeMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ReservationMapper reservationMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final SeatMapper seatMapper;
    private final SeatTypeMapper seatTypeMapper;
    private final MemberOrderMapper orderMapper;
    private final MembershipPlanMapper planMapper;
    private final UserMapper userMapper;

    /** 已支付 */
    private static final int PAID = 1;

    @Override
    public Map<String, Object> overview(Long storeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today.toString());

        long todayReservations = nz(reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, today)
                .eq(storeId != null, Reservation::getStoreId, storeId)));

        long todayCheckins = nz(checkinRecordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .ge(CheckinRecord::getCheckinTime, dayStart)
                .lt(CheckinRecord::getCheckinTime, dayEnd)));

        long inUse = nz(reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, today)
                .eq(Reservation::getStatus, 1)
                .eq(storeId != null, Reservation::getStoreId, storeId)));

        long todayNoShow = nz(reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, today)
                .eq(Reservation::getStatus, 4)
                .eq(storeId != null, Reservation::getStoreId, storeId)));

        long seatTotal = nz(seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .ne(Seat::getStatus, 2)
                .eq(storeId != null, Seat::getStoreId, storeId)));

        BigDecimal todayRevenue = sumAmount(new QueryWrapper<MemberOrder>()
                .select("IFNULL(SUM(amount),0) AS total")
                .eq("pay_status", PAID)
                .ge("pay_time", dayStart)
                .lt("pay_time", dayEnd)
                .eq(storeId != null, "store_id", storeId));

        BigDecimal totalRevenue = sumAmount(new QueryWrapper<MemberOrder>()
                .select("IFNULL(SUM(amount),0) AS total")
                .eq("pay_status", PAID)
                .eq(storeId != null, "store_id", storeId));

        long memberTotal = nz(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, 2)));

        m.put("todayReservations", todayReservations);
        m.put("todayCheckins", todayCheckins);
        m.put("inUseSeats", inUse);
        m.put("seatTotal", seatTotal);
        m.put("occupancyRate", seatTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(inUse * 100.0 / seatTotal).setScale(1, RoundingMode.HALF_UP));
        m.put("todayNoShow", todayNoShow);
        m.put("todayRevenue", todayRevenue);
        m.put("totalRevenue", totalRevenue);
        m.put("memberTotal", memberTotal);
        return m;
    }

    @Override
    public List<Map<String, Object>> hourlyTraffic(Long storeId, String date) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        QueryWrapper<Reservation> w = new QueryWrapper<>();
        w.select("HOUR(start_time) AS hour", "COUNT(*) AS count")
                .eq("reserve_date", d)
                .groupBy("HOUR(start_time)")
                .orderByAsc("HOUR(start_time)");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        List<Map<String, Object>> rows = reservationMapper.selectMaps(w);

        // 补齐没有预约的小时，否则 ECharts 折线会跳点、看起来像数据缺失
        Map<Integer, Long> byHour = new HashMap<>();
        for (Map<String, Object> r : rows) {
            byHour.put(toInt(r.get("hour")), toLong(r.get("count")));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int h = 8; h <= 21; h++) {     // 营业时间 08:00-22:00
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("hour", String.format("%02d:00", h));
            p.put("count", byHour.getOrDefault(h, 0L));
            out.add(p);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> reservationTrend(Long storeId, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        QueryWrapper<Reservation> w = new QueryWrapper<>();
        w.select("reserve_date AS date", "COUNT(*) AS count")
                .ge("reserve_date", from)
                .groupBy("reserve_date")
                .orderByAsc("reserve_date");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        return fillDates(reservationMapper.selectMaps(w), from, days, "date", "count");
    }

    @Override
    public List<Map<String, Object>> revenueTrend(Long storeId, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        QueryWrapper<MemberOrder> w = new QueryWrapper<>();
        w.select("DATE(pay_time) AS date", "IFNULL(SUM(amount),0) AS amount")
                .eq("pay_status", PAID)
                .ge("pay_time", from.atStartOfDay())
                .groupBy("DATE(pay_time)")
                .orderByAsc("DATE(pay_time)");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        return fillDates(orderMapper.selectMaps(w), from, days, "date", "amount");
    }

    @Override
    public List<Map<String, Object>> memberGrowth(Long storeId, int days) {
        // 以「已支付订单」计新增会员：member_order 带 store_id，可按门店归集
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        QueryWrapper<MemberOrder> w = new QueryWrapper<>();
        w.select("DATE(pay_time) AS date", "COUNT(*) AS count")
                .eq("pay_status", PAID)
                .ge("pay_time", from.atStartOfDay())
                .groupBy("DATE(pay_time)")
                .orderByAsc("DATE(pay_time)");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        return fillDates(orderMapper.selectMaps(w), from, days, "date", "count");
    }

    @Override
    public List<Map<String, Object>> planDistribution(Long storeId) {
        QueryWrapper<MemberOrder> w = new QueryWrapper<>();
        w.select("plan_id AS planId", "COUNT(*) AS count")
                .eq("pay_status", PAID)
                .groupBy("plan_id");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        List<Map<String, Object>> rows = orderMapper.selectMaps(w);

        // 套餐表很小，直接在内存里映射名称，避免为了一个饼图写联表 SQL
        Map<Long, String> planNames = new HashMap<>();
        for (MembershipPlan p : planMapper.selectList(null)) {
            planNames.put(p.getId(), p.getName());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long pid = toLong(r.get("planId"));
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", planNames.getOrDefault(pid, "套餐#" + pid));
            p.put("value", toLong(r.get("count")));
            out.add(p);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> seatTypeUsage(Long storeId) {
        QueryWrapper<Reservation> w = new QueryWrapper<>();
        w.select("seat_id AS seatId", "COUNT(*) AS count").groupBy("seat_id");
        if (storeId != null) {
            w.eq("store_id", storeId);
        }
        List<Map<String, Object>> rows = reservationMapper.selectMaps(w);

        Map<Long, Long> seatToType = new HashMap<>();
        for (Seat s : seatMapper.selectList(null)) {
            seatToType.put(s.getId(), s.getTypeId());
        }
        Map<Long, String> typeNames = new HashMap<>();
        for (SeatType t : seatTypeMapper.selectList(null)) {
            typeNames.put(t.getId(), t.getName());
        }
        Map<String, Long> agg = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Long typeId = seatToType.get(toLong(r.get("seatId")));
            String name = typeNames.getOrDefault(typeId, "未知类型");
            agg.merge(name, toLong(r.get("count")), Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((k, v) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", k);
            p.put("value", v);
            out.add(p);
        });
        return out;
    }

    // ------------------------------------------------------------------

    /** 把 GROUP BY 的结果补齐成连续日期序列（值为 0 的日期也要出现），ECharts 才不会断线 */
    private List<Map<String, Object>> fillDates(List<Map<String, Object>> rows, LocalDate from,
                                                int days, String dateKey, String valueKey) {
        Map<String, Object> byDate = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object d = r.get(dateKey);
            if (d != null) {
                byDate.put(d.toString(), r.get(valueKey));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String d = from.plusDays(i).toString();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d);
            p.put(valueKey, byDate.getOrDefault(d, 0));
            out.add(p);
        }
        return out;
    }

    private BigDecimal sumAmount(QueryWrapper<MemberOrder> w) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(w);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Object v = rows.get(0).get("total");
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        return o instanceof Number n ? n.longValue() : Long.parseLong(o.toString());
    }

    private static int toInt(Object o) {
        return (int) toLong(o);
    }
}
