package com.wzbc.studyroom.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.MembershipPlan;
import com.wzbc.studyroom.entity.Seat;
import com.wzbc.studyroom.entity.SeatType;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.mapper.SeatMapper;
import com.wzbc.studyroom.mapper.SeatTypeMapper;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.service.MembershipService;
import com.wzbc.studyroom.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 经营者的基础资源管理：门店信息、座位布局、套餐配置（仅 role = 0）。
 *
 * <p>为什么单开一个 Controller 而不是继续往 StoreController 里塞：
 * 这里管的是「经营配置」这一类动作（改门店、加座位、配套餐），
 * 而 StoreController 是「读门店」。混在一起后权限注解的粒度会变得含糊。
 */
@RestController
@RequestMapping("/api/owner")
@RequireRole(0)
@RequiredArgsConstructor
public class OwnerResourceController {

    private final StoreService storeService;
    private final SeatMapper seatMapper;
    private final SeatTypeMapper seatTypeMapper;
    private final MembershipService membershipService;

    // ---------------- 门店 ----------------

    @PostMapping("/stores")
    public Result<Store> createStore(@RequestBody Store store) {
        return Result.ok(storeService.create(store));
    }

    @PutMapping("/stores")
    public Result<Store> updateStore(@RequestBody Store store) {
        if (store.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少门店 id");
        }
        return Result.ok(storeService.update(store.getId(), store));
    }

    // ---------------- 座位类型 ----------------

    /** 座位类型列表（含停用），供座位表单选择 */
    @GetMapping("/seat-types")
    public Result<List<SeatType>> seatTypes() {
        return Result.ok(seatTypeMapper.selectList(new LambdaQueryWrapper<SeatType>()
                .orderByAsc(SeatType::getSort)));
    }

    /** 新增或更新座位类型 */
    @PostMapping("/seat-types")
    public Result<SeatType> saveSeatType(@RequestBody SeatType type) {
        if (type.getName() == null || type.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "类型名称不能为空");
        }
        if (type.getId() == null) {
            if (type.getStatus() == null) {
                type.setStatus(1);
            }
            if (type.getSort() == null) {
                type.setSort(0);
            }
            seatTypeMapper.insert(type);
        } else {
            seatTypeMapper.updateById(type);
        }
        return Result.ok(seatTypeMapper.selectById(type.getId()));
    }

    // ---------------- 座位 ----------------

    /** 某门店全部座位（含维修/停用），供布局管理 */
    @GetMapping("/seats")
    public Result<List<Seat>> seats(@RequestParam Long storeId) {
        return Result.ok(seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getStoreId, storeId)
                .orderByAsc(Seat::getRowIndex)
                .orderByAsc(Seat::getColIndex)));
    }

    /**
     * 新增座位。新增后同步刷新门店的「座位总数」冗余字段，
     * 否则看板与门店列表上的座位数会和实际布局不一致。
     */
    @PostMapping("/seats")
    public Result<Seat> createSeat(@RequestBody Seat seat) {
        if (seat.getStoreId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少门店 id");
        }
        if (seat.getSeatNo() == null || seat.getSeatNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "座位编号不能为空");
        }
        if (seat.getTypeId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择座位类型");
        }
        Long dup = seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getStoreId, seat.getStoreId())
                .eq(Seat::getSeatNo, seat.getSeatNo().trim()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该门店下座位编号已存在：" + seat.getSeatNo());
        }
        seat.setId(null);
        if (seat.getStatus() == null) {
            seat.setStatus(0);
        }
        if (seat.getRowIndex() == null) {
            seat.setRowIndex(0);
        }
        if (seat.getColIndex() == null) {
            seat.setColIndex(0);
        }
        seatMapper.insert(seat);
        syncSeatTotal(seat.getStoreId());
        return Result.ok(seatMapper.selectById(seat.getId()));
    }

    /** 更新座位（编号/类型/网格坐标/状态） */
    @PutMapping("/seats")
    public Result<Seat> updateSeat(@RequestBody Seat seat) {
        if (seat.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少座位 id");
        }
        Seat exist = seatMapper.selectById(seat.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.SEAT_UNAVAILABLE, "座位不存在");
        }
        seatMapper.updateById(seat);
        syncSeatTotal(exist.getStoreId());
        return Result.ok(seatMapper.selectById(seat.getId()));
    }

    // ---------------- 套餐配置 ----------------

    /** 全部套餐（含下架） */
    @GetMapping("/plans")
    public Result<List<MembershipPlan>> plans() {
        return Result.ok(membershipService.listAllPlans());
    }

    /** 新增或更新套餐 */
    @PostMapping("/plans")
    public Result<MembershipPlan> savePlan(@RequestBody MembershipPlan plan) {
        return Result.ok(membershipService.savePlan(plan));
    }

    // ------------------------------------------------------------------

    /** 把门店的座位总数冗余字段刷新为「未停用座位数」 */
    private void syncSeatTotal(Long storeId) {
        Long total = seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getStoreId, storeId)
                .ne(Seat::getStatus, 2));
        Store patch = new Store();
        patch.setId(storeId);
        patch.setSeatTotal(total == null ? 0 : total.intValue());
        storeService.update(storeId, patch);
    }
}
