-- ============================================================================
--  演示数据重置脚本
--
--  用途：清除测试与日常操作产生的业务数据，把系统恢复到「可直接答辩演示」的干净状态。
--
--  保留：门店、座位、座位类型、会员套餐、AI 知识库、运营规则、三个演示账号
--  清除：预约 / 签到 / 消费流水 / 用户权益 / 订单 / 消息 / 违规 / 公告 / 备份记录，
--        以及自动化测试创建的账号（136、137 开头）
--
--  可重复执行。
--  执行：mysql -u root -p --default-character-set=utf8mb4 study_room < reset-demo.sql
--
--  ⚠️ 本文件必须保存为 **UTF-8 with BOM**，不要去掉 BOM。
--     踩坑记录：无 BOM 时，Windows PowerShell 的 `Get-Content -Raw` 会按系统
--     ANSI（简体中文机器上是 GBK）解码，中文注释被解成乱码字节后会吞掉换行，
--     紧跟在注释后面的语句会被并进注释里从而**静默跳过**——实测本文件有 16 行
--     被吞，`member_order` 与 `operation_log` 的清理整个消失，随后删用户时才报
--     外键错误，而错误信息指向的行号是错的，极难排查。
--     MySQL 8 能正常接受开头的 BOM（已实测 < 重定向 / 管道 / source 三种方式）。
--
--  ⚠️ 本脚本不含任何 DROP DATABASE，不会影响机器上的其它数据库。
--  ⚠️ 若三个演示账号（139 开头）的密码曾被改过，SQL 无法离线算出 BCrypt 散列，
--     请删除这三个账号后重启后端 —— DemoDataInitializer 会用与登录相同的编码器重建它们：
--         DELETE FROM `user` WHERE phone IN ('13900000000','13900000001','13900000002');
-- ============================================================================

USE study_room;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 1. 按外键依赖顺序清理业务数据（先删子表，再删父表）
-- ---------------------------------------------------------------------------
DELETE FROM ai_chat_record;      -- 依赖 user
DELETE FROM checkin_record;      -- 依赖 reservation / user / seat
DELETE FROM violation_record;    -- 依赖 user / reservation
DELETE FROM consumption_record;  -- 依赖 user
DELETE FROM user_membership;     -- 依赖 user / membership_plan / member_order（必须先于 member_order）
DELETE FROM member_order;        -- 依赖 user / membership_plan
DELETE FROM reservation;         -- 依赖 user / seat / store
DELETE FROM notification;        -- 依赖 user
DELETE FROM staff_permission;    -- 依赖 user
DELETE FROM announcement;        -- 依赖 user(publisher)
DELETE FROM backup_record;       -- 无外键，但属于运维痕迹
DELETE FROM operation_log;       -- 无外键

-- ---------------------------------------------------------------------------
-- 2. 删除测试期间注册的账号，只保留三个演示账号
--    这里用「白名单保留」而不是「按号码段删除」：早先写的是
--    `phone LIKE '136%' OR '137%'`，结果一个用 173 号段注册的测试账号
--    留了下来（演示账号列表里多出一个人）。白名单写法则与测试用了什么号段无关。
-- ---------------------------------------------------------------------------
DELETE FROM `user` WHERE phone NOT IN ('13900000000', '13900000001', '13900000002');

-- ---------------------------------------------------------------------------
-- 3. 把三个演示账号的统计字段与状态复原
-- ---------------------------------------------------------------------------
UPDATE `user` SET total_study_minutes = 0, status = 1
WHERE phone IN ('13900000000', '13900000001', '13900000002');

-- ---------------------------------------------------------------------------
-- 4. 座位状态恢复为演示初始值
--    日常操作与测试会把座位改成维修/停用且不会自动改回来，
--    不恢复的话演示时会出现"明明有 30 个座位却约不了"的困惑。
--    初始状态：C-05 与 E-06 维修中，其余全部可用。
-- ---------------------------------------------------------------------------
UPDATE seat SET status = 0, remark = NULL
WHERE status <> 0 AND seat_no NOT IN ('C-05', 'E-06');
UPDATE seat SET status = 1, remark = '隔间门锁故障，维修中' WHERE seat_no = 'C-05';
UPDATE seat SET status = 1, remark = '桌面破损待更换'       WHERE seat_no = 'E-06';

-- ---------------------------------------------------------------------------
-- 5. 门店的「座位总数」是冗余缓存字段，按未停用座位重算，避免与实际布局不一致
-- ---------------------------------------------------------------------------
UPDATE store s
SET s.seat_total = (
    SELECT COUNT(*) FROM seat WHERE seat.store_id = s.id AND seat.status <> 2
);

SELECT '演示数据已重置' AS result;
