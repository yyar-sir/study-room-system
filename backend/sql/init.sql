-- ============================================================================
--  共享自习室预约与管理系统 · 数据库初始化脚本
--  题目：基于SpringBoot的共享自习室预约与管理系统设计与实现
--
--  字符集：utf8mb4 / utf8mb4_0900_ai_ci
--    ⚠️ 刻意与 MySQL 8.4 的服务器默认排序规则保持一致。
--       若库用 utf8mb4_unicode_ci 而某些表继承服务器默认的 0900_ai_ci，
--       跨表 JOIN 会抛 "Illegal mix of collations"。此处全部显式写死，杜绝该问题。
--
--  角色编码（沿用整数编码，0 为最高权限）：
--    0 = 自习室经营者   1 = 前台管理员   2 = 自习室用户
--
--  保留字规避（踩过的坑，勿改）：
--    ✗ order  → 用 member_order（ORDER 是 MySQL 保留字）
--    ✗ key    → 用 config_key
--    ✗ read   → 用 is_read
--
--  执行：mysql -u root -p --default-character-set=utf8mb4 < init.sql
--  ⚠️ 本脚本不含任何 DROP DATABASE，不会影响机器上已有的其它库。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS study_room
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE study_room;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================================
--  一、门店与用户权限域
-- ============================================================================

-- 门店：支持多门店，前台管理员的「数据访问范围」即绑定到某个门店
CREATE TABLE store (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name          VARCHAR(64)  NOT NULL                COMMENT '门店名称',
  address       VARCHAR(255) DEFAULT NULL            COMMENT '门店地址',
  phone         VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
  open_time     TIME         NOT NULL DEFAULT '08:00:00' COMMENT '每日开始营业时间',
  close_time    TIME         NOT NULL DEFAULT '22:00:00' COMMENT '每日结束营业时间',
  seat_total    INT          NOT NULL DEFAULT 0      COMMENT '座位总数（冗余，便于看板展示）',
  description   VARCHAR(500) DEFAULT NULL            COMMENT '门店简介',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1营业 0停业',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_store_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';

-- 账号：三类角色共用一张表，靠 role 区分（登录入口不同，账号体系统一）
CREATE TABLE `user` (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  phone               VARCHAR(20)  NOT NULL                COMMENT '手机号（登录账号）',
  password            VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码散列（严禁明文/MD5）',
  real_name           VARCHAR(32)  DEFAULT NULL            COMMENT '真实姓名',
  nickname            VARCHAR(32)  DEFAULT NULL            COMMENT '昵称',
  avatar              VARCHAR(255) DEFAULT NULL            COMMENT '头像地址',
  gender              TINYINT      NOT NULL DEFAULT 0      COMMENT '0未知 1男 2女',
  role                TINYINT      NOT NULL DEFAULT 2      COMMENT '0经营者 1前台管理员 2自习室用户',
  store_id            BIGINT       DEFAULT NULL            COMMENT '所属门店（前台管理员/经营者的数据访问范围；用户为常用门店）',
  status              TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0禁用',
  notify_enabled      TINYINT      NOT NULL DEFAULT 1      COMMENT '1接收站内消息 0不接收',
  remind_enabled      TINYINT      NOT NULL DEFAULT 1      COMMENT '1开启到期/预约提醒 0关闭',
  total_study_minutes INT          NOT NULL DEFAULT 0      COMMENT '累计学习时长（分钟，签退时累加）',
  last_login_at       DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  -- JWT 无状态令牌无法撤销：改密后写入本字段，拦截器拒绝签发时间早于它的 token
  -- 效果：改密 = 踢下线所有旧设备
  password_changed_at DATETIME     DEFAULT NULL            COMMENT '密码最后修改时间（用于作废旧 token）',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_phone (phone),
  KEY idx_user_role (role),
  KEY idx_user_store (store_id),
  CONSTRAINT fk_user_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号（三角色合一）';

-- 前台管理员岗位权限：经营者可给不同前台分配不同操作权限
-- 权限键示例：seat:manage / member:handle / checkin:assist / record:query / announcement:publish
CREATE TABLE staff_permission (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL COMMENT '前台管理员账号 id',
  perm_key   VARCHAR(64) NOT NULL COMMENT '权限键',
  granted_by BIGINT      DEFAULT NULL COMMENT '授权人（经营者）id',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_staff_perm (user_id, perm_key),
  CONSTRAINT fk_perm_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台管理员岗位权限';


-- ============================================================================
--  二、座位资源域
-- ============================================================================

-- 座位类型：普通/靠窗/独立隔间/双人座，可配价格系数
CREATE TABLE seat_type (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  name         VARCHAR(32)  NOT NULL COMMENT '类型名称',
  description  VARCHAR(255) DEFAULT NULL,
  price_factor DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '价格系数（用于套餐计价）',
  icon         VARCHAR(64)  DEFAULT NULL COMMENT '图标标识，前端可视化选座用',
  color        VARCHAR(16)  DEFAULT NULL COMMENT '前端座位图配色（如 #1e4fd8）',
  sort         INT          NOT NULL DEFAULT 0,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (id),
  KEY idx_seat_type_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='座位类型';

-- 座位：row_index/col_index 支撑前端「座位可视化选座」的网格布局
CREATE TABLE seat (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  store_id   BIGINT      NOT NULL COMMENT '所属门店',
  seat_no    VARCHAR(16) NOT NULL COMMENT '座位编号（如 A-01）',
  type_id    BIGINT      NOT NULL COMMENT '座位类型',
  row_index  INT         NOT NULL DEFAULT 0 COMMENT '网格行号（从 0 开始）',
  col_index  INT         NOT NULL DEFAULT 0 COMMENT '网格列号（从 0 开始）',
  status     TINYINT     NOT NULL DEFAULT 0 COMMENT '0可用 1维修中 2已停用',
  remark     VARCHAR(255) DEFAULT NULL COMMENT '备注（如故障原因）',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seat_store_no (store_id, seat_no),
  KEY idx_seat_status (store_id, status),
  CONSTRAINT fk_seat_store FOREIGN KEY (store_id) REFERENCES store (id),
  CONSTRAINT fk_seat_type  FOREIGN KEY (type_id)  REFERENCES seat_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='座位';

-- 运营时段：可预约的时间段划分（如 上午场/下午场/晚场）
CREATE TABLE business_hour (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  store_id   BIGINT      NOT NULL,
  name       VARCHAR(32) NOT NULL COMMENT '时段名称',
  start_time TIME        NOT NULL COMMENT '开始时间',
  end_time   TIME        NOT NULL COMMENT '结束时间',
  bookable   TINYINT     NOT NULL DEFAULT 1 COMMENT '1可预约 0不可预约',
  sort       INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_bh_store (store_id, status),
  CONSTRAINT fk_bh_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运营时段';


-- ============================================================================
--  三、会员套餐与计费域
-- ============================================================================

-- 会员套餐：次卡/月卡/季卡
CREATE TABLE membership_plan (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  name           VARCHAR(64)   NOT NULL COMMENT '套餐名称',
  plan_type      TINYINT       NOT NULL COMMENT '1次卡 2月卡 3季卡 4年卡',
  price          DECIMAL(10,2) NOT NULL COMMENT '售价',
  original_price DECIMAL(10,2) DEFAULT NULL COMMENT '原价（划线价）',
  duration_days  INT           NOT NULL DEFAULT 0 COMMENT '有效天数（次卡可为 0，按天数不限制则填 9999）',
  total_times    INT           NOT NULL DEFAULT -1 COMMENT '总可用次数，-1 表示不限次',
  daily_limit    INT           NOT NULL DEFAULT 1 COMMENT '每日可预约次数上限',
  benefits       VARCHAR(500)  DEFAULT NULL COMMENT '权益说明（JSON 或分隔文本）',
  description    VARCHAR(500)  DEFAULT NULL,
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  sort           INT           NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员套餐';

-- 购买订单（表名避开保留字 ORDER）
CREATE TABLE member_order (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(32)   NOT NULL COMMENT '订单号',
  user_id     BIGINT        NOT NULL,
  plan_id     BIGINT        NOT NULL,
  store_id    BIGINT        DEFAULT NULL,
  amount      DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  pay_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '1模拟支付 2微信 3支付宝',
  pay_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已退款',
  pay_time    DATETIME      DEFAULT NULL,
  remark      VARCHAR(255)  DEFAULT NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_user (user_id, pay_status),
  KEY idx_order_time (created_at),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_order_plan FOREIGN KEY (plan_id) REFERENCES membership_plan (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员套餐订单';

-- 用户已购套餐（权益实例）
CREATE TABLE user_membership (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  user_id      BIGINT      NOT NULL,
  plan_id      BIGINT      NOT NULL,
  order_id     BIGINT      DEFAULT NULL COMMENT '来源订单',
  plan_name    VARCHAR(64) NOT NULL COMMENT '套餐名快照（套餐改名不影响历史记录）',
  start_time   DATETIME    NOT NULL COMMENT '生效时间',
  expire_time  DATETIME    NOT NULL COMMENT '到期时间',
  total_times  INT         NOT NULL DEFAULT -1 COMMENT '总次数，-1 不限次',
  remain_times INT         NOT NULL DEFAULT -1 COMMENT '剩余次数，-1 不限次',
  status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1生效 0已失效 2已用完 3已退款',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_um_user (user_id, status),
  KEY idx_um_expire (expire_time, status),
  CONSTRAINT fk_um_user  FOREIGN KEY (user_id)  REFERENCES `user` (id),
  CONSTRAINT fk_um_plan  FOREIGN KEY (plan_id)  REFERENCES membership_plan (id),
  CONSTRAINT fk_um_order FOREIGN KEY (order_id) REFERENCES member_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已购套餐';

-- ★★★ 权益流水账本（全系统最关键的防刷设计）★★★
--  设计要点来自实战教训：权益余额【绝不】按当前状态实时推导，而是以本流水表为唯一真相。
--  ① 幂等：uk_consume_source (user_id, source_key) 唯一约束。
--     同一件事重复触发只记一次——重复提交签到、重复回调都不会重复扣次。
--     把幂等交给数据库，比在 Service 里写 if 判断可靠得多。
--  ② 可解释：用户可查看自己的消费明细，扣费不透明会引发投诉。
--  ③ 可追溯：删除预约不会让已扣次数"倒退"，退款走新增一条反向流水。
CREATE TABLE consumption_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  user_id       BIGINT        NOT NULL,
  membership_id BIGINT        DEFAULT NULL COMMENT '关联的用户套餐',
  store_id      BIGINT        DEFAULT NULL,
  biz_type      TINYINT       NOT NULL COMMENT '1购买 2预约扣次 3取消返还 4退款 5爽约扣减',
  amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '金额变动（正为收入，负为支出）',
  times_delta   INT           NOT NULL DEFAULT 0 COMMENT '次数变动（负数表示扣减）',
  balance_times INT           NOT NULL DEFAULT -1 COMMENT '变动后剩余次数快照',
  source_key    VARCHAR(64)   NOT NULL COMMENT '幂等键，如 checkin:1024 / order:20261012001',
  remark        VARCHAR(255)  DEFAULT NULL COMMENT '流水说明',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_consume_source (user_id, source_key),
  KEY idx_consume_user_time (user_id, created_at),
  KEY idx_consume_type (biz_type),
  CONSTRAINT fk_consume_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费与权益流水账本';


-- ============================================================================
--  四、预约与签到域
-- ============================================================================

-- 预约记录
CREATE TABLE reservation (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  reservation_no VARCHAR(32) NOT NULL COMMENT '预约单号',
  user_id        BIGINT      NOT NULL,
  store_id       BIGINT      NOT NULL,
  seat_id        BIGINT      NOT NULL,
  membership_id  BIGINT      DEFAULT NULL COMMENT '本次预约使用的套餐',
  reserve_date   DATE        NOT NULL COMMENT '预约日期',
  start_time     TIME        NOT NULL COMMENT '开始时间',
  end_time       TIME        NOT NULL COMMENT '结束时间',
  status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0待使用 1使用中 2已完成 3已取消 4爽约',
  checkin_code   VARCHAR(16) NOT NULL COMMENT '签到码（扫码签到用，前台屏幕/座位二维码携带）',
  source         TINYINT     NOT NULL DEFAULT 1 COMMENT '1用户自助预约 2前台代约',
  cancel_reason  VARCHAR(255) DEFAULT NULL,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_no (reservation_no),
  UNIQUE KEY uk_checkin_code (checkin_code),
  -- ★ 同一座位/日期/开始时间只允许一条【生效中】的预约。
  --
  -- 这里刻意用「生成列 + 唯一索引」，而不是直接对 (seat_id, reserve_date, start_time) 建唯一索引。
  -- 原因（实测踩到的坑）：直接建唯一索引时，用户取消预约后 status=3 的那一行
  -- 仍然永久占着这个组合，导致「取消后无法重新预约同一时段」；
  -- 更糟的是座位图按 status 判断会显示该座位"可预约"，下单却报冲突，前后自相矛盾。
  -- 生成列在非生效状态下取 NULL，而 MySQL 唯一索引把多个 NULL 视为互不相同，
  -- 于是已取消/已完成/爽约的记录不再阻塞新预约，生效中的记录依然互斥。
  active_slot VARCHAR(64) GENERATED ALWAYS AS (
      CASE WHEN status IN (0, 1)
           THEN CONCAT(seat_id, '|', reserve_date, '|', start_time)
           ELSE NULL END
  ) STORED,
  UNIQUE KEY uk_res_active_slot (active_slot),
  -- 外键 fk_res_seat 需要一个以 seat_id 起头的索引来支撑；
  -- 原先这个角色由 uk_res_seat_slot 兼任，改成生成列唯一索引后必须显式补一个，
  -- 否则 DROP 旧索引会报 "Cannot drop index: needed in a foreign key constraint"。
  KEY idx_res_seat (seat_id),
  KEY idx_res_user (user_id, status),
  KEY idx_res_date (store_id, reserve_date, status),
  CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_res_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
  CONSTRAINT fk_res_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='座位预约记录';

-- 签到签退记录
CREATE TABLE checkin_record (
  id               BIGINT   NOT NULL AUTO_INCREMENT,
  reservation_id   BIGINT   NOT NULL,
  user_id          BIGINT   NOT NULL,
  seat_id          BIGINT   NOT NULL,
  checkin_time     DATETIME NOT NULL COMMENT '签到时间',
  checkout_time    DATETIME DEFAULT NULL COMMENT '签退时间（未签退为 NULL）',
  checkin_type     TINYINT  NOT NULL DEFAULT 1 COMMENT '1用户扫码签到 2前台协助签到',
  operator_id      BIGINT   DEFAULT NULL COMMENT '操作人（前台协助时记录）',
  duration_minutes INT      NOT NULL DEFAULT 0 COMMENT '本次自习时长（分钟，签退时结算）',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 一条预约只能有一条签到记录：防止重复签到重复扣次
  UNIQUE KEY uk_checkin_reservation (reservation_id),
  KEY idx_checkin_user (user_id, checkin_time),
  CONSTRAINT fk_checkin_res   FOREIGN KEY (reservation_id) REFERENCES reservation (id),
  CONSTRAINT fk_checkin_user  FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_checkin_seat  FOREIGN KEY (seat_id) REFERENCES seat (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到签退记录';

-- 违规占座记录
CREATE TABLE violation_record (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  reservation_id BIGINT       DEFAULT NULL,
  store_id       BIGINT       NOT NULL,
  violation_type TINYINT      NOT NULL COMMENT '1超时未签退 2预约未到(爽约) 3占用他人座位 4其他',
  description    VARCHAR(500) DEFAULT NULL,
  penalty        VARCHAR(255) DEFAULT NULL COMMENT '处罚措施（如扣除1次权益/禁止预约3天）',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已申诉',
  handler_id     BIGINT       DEFAULT NULL COMMENT '处理人',
  handle_remark  VARCHAR(500) DEFAULT NULL,
  handled_at     DATETIME     DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_violation_user (user_id, status),
  KEY idx_violation_store (store_id, created_at),
  CONSTRAINT fk_violation_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_violation_res  FOREIGN KEY (reservation_id) REFERENCES reservation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='违规占座记录';


-- ============================================================================
--  五、运营与系统域
-- ============================================================================

CREATE TABLE announcement (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  store_id     BIGINT       DEFAULT NULL COMMENT 'NULL 表示全平台公告',
  title        VARCHAR(128) NOT NULL,
  content      TEXT         NOT NULL,
  publisher_id BIGINT       NOT NULL COMMENT '发布人',
  scope        TINYINT      NOT NULL DEFAULT 1 COMMENT '1全平台 2本门店',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1已发布 0已下架',
  publish_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ann_scope (scope, status, publish_time),
  CONSTRAINT fk_ann_publisher FOREIGN KEY (publisher_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告';

-- 站内消息 / 到期与预约提醒
-- ⚠️ 字段名用 is_read 而非 read —— read 是 MySQL 保留字
CREATE TABLE notification (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  type       VARCHAR(32)  NOT NULL COMMENT 'plan_expire 套餐到期 / reserve_remind 预约提醒 / violation 违规 / announce 公告 / system 系统',
  title      VARCHAR(128) NOT NULL,
  content    VARCHAR(500) DEFAULT NULL,
  link       VARCHAR(255) DEFAULT NULL COMMENT '点击跳转的前端路由',
  is_read    TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notify_user (user_id, is_read, created_at),
  CONSTRAINT fk_notify_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息与提醒';

-- 系统配置（含运营规则：占座判定标准、违规处罚、提醒规则等）
-- ⚠️ 字段名用 config_key 而非 key —— key 是 MySQL 保留字
CREATE TABLE system_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  -- ⚠️ 用 0 表示"全局配置"而【不是】NULL。原因：MySQL 的唯一索引把多个 NULL 视为互不相同，
  --    若这里允许 NULL，uk_config 就形同虚设 —— (NULL,'max_book_per_day') 能重复插入任意多次，
  --    配置项会出现多条互相矛盾的记录。改成 NOT NULL DEFAULT 0 后唯一约束才真正生效。
  store_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '0 表示全局配置，其余为门店 id',
  config_group VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT 'rule 运营规则 / remind 提醒 / system 系统参数',
  config_key   VARCHAR(64)  NOT NULL,
  config_value VARCHAR(500) NOT NULL,
  description  VARCHAR(255) DEFAULT NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config (store_id, config_key),
  KEY idx_config_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统与运营规则配置';

CREATE TABLE operation_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       DEFAULT NULL,
  username   VARCHAR(32)  DEFAULT NULL COMMENT '冗余用户名（账号删除后日志仍可读）',
  module     VARCHAR(32)  DEFAULT NULL COMMENT '模块',
  action     VARCHAR(64)  DEFAULT NULL COMMENT '操作描述',
  method     VARCHAR(255) DEFAULT NULL COMMENT '请求方法与路径',
  params     TEXT         DEFAULT NULL COMMENT '请求参数',
  ip         VARCHAR(64)  DEFAULT NULL,
  success    TINYINT      NOT NULL DEFAULT 1,
  cost_ms    INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oplog_user (user_id, created_at),
  KEY idx_oplog_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

CREATE TABLE backup_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  file_name   VARCHAR(128) NOT NULL COMMENT '备份文件名',
  file_path   VARCHAR(500) DEFAULT NULL COMMENT '备份文件路径',
  file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  backup_type TINYINT      NOT NULL DEFAULT 1 COMMENT '1手动 2定时',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  operator_id BIGINT       DEFAULT NULL,
  remark      VARCHAR(255) DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_backup_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据备份记录';


-- ============================================================================
--  六、AI 智能自习助手域
-- ============================================================================

-- 大模型配置。api_key 仅后端读取，任何查询接口返回前必须脱敏（见 REFERENCE-ai-integration.md）
CREATE TABLE ai_config (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  provider           VARCHAR(32)  NOT NULL DEFAULT 'deepseek' COMMENT 'deepseek / openai / qwen ...',
  base_url           VARCHAR(255) NOT NULL DEFAULT 'https://api.deepseek.com',
  model              VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  api_key            VARCHAR(255) DEFAULT NULL COMMENT 'API Key（仅后端读取，禁止下发前端）',
  temperature        DECIMAL(3,2) NOT NULL DEFAULT 0.70,
  timeout_ms         INT          NOT NULL DEFAULT 20000,
  rate_limit_per_min INT          NOT NULL DEFAULT 10 COMMENT '单用户每分钟请求上限（限流）',
  content_safety     TINYINT      NOT NULL DEFAULT 1 COMMENT '1开启内容安全过滤',
  fallback_enabled   TINYINT      NOT NULL DEFAULT 1 COMMENT '1开启三级降级',
  system_prompt      TEXT         DEFAULT NULL COMMENT '系统提示词（自习室助手人设与边界）',
  status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型配置';

-- 自习室知识库：AI 助手回答套餐规则/座位分布/运营政策的事实来源（RAG 或意图匹配）
CREATE TABLE ai_knowledge (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  category   VARCHAR(32)  NOT NULL COMMENT '分类：套餐 / 座位 / 规则 / 门店 / 其它',
  question   VARCHAR(255) NOT NULL COMMENT '标准问法',
  answer     TEXT         NOT NULL COMMENT '标准答案',
  keywords   VARCHAR(255) DEFAULT NULL COMMENT '关键词，逗号分隔，用于召回',
  hit_count  INT          NOT NULL DEFAULT 0 COMMENT '命中次数',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ak_category (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 自习室知识库';

-- AI 对话记录
CREATE TABLE ai_chat_record (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  session_id VARCHAR(64)  NOT NULL COMMENT '会话 id（前端生成，多轮对话归组）',
  role       VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
  content    TEXT         NOT NULL,
  intent     VARCHAR(32)  DEFAULT NULL COMMENT '识别出的意图：plan_query / seat_recommend / study_plan / policy ...',
  degraded   TINYINT      NOT NULL DEFAULT 0 COMMENT '1表示本次为降级响应（大模型不可用时返回知识库兜底答案）',
  tokens     INT          NOT NULL DEFAULT 0,
  cost_ms    INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_aichat_session (session_id, created_at),
  KEY idx_aichat_user (user_id, created_at),
  CONSTRAINT fk_aichat_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对话记录';
