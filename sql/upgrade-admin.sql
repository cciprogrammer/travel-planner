-- ============================================================
-- 管理员功能升级脚本（幂等，可重复执行）
-- 作用：
--   1. t_user 增加 role（角色）、status（状态）两列
--   2. 新建 t_hotel 酒店表（管理员在后台维护）
-- 说明：管理员账号本身由后端 AdminInitializer 在启动时自动创建，
--       避免在 SQL 里硬编码 BCrypt 密文（密文写错会直接登录失败）。
-- ============================================================

USE travel_planner;

-- ---------- 1. t_user 增加 role 列 ----------
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'travel_planner' AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role') = 0,
    'ALTER TABLE t_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''USER'' COMMENT ''角色：ADMIN-管理员，USER-普通用户''',
    'SELECT ''role 列已存在，跳过'''
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. t_user 增加 status 列 ----------
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'travel_planner' AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE t_user ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1-正常，0-已禁用''',
    'SELECT ''status 列已存在，跳过'''
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 酒店表 ----------
CREATE TABLE IF NOT EXISTS t_hotel (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name            VARCHAR(100) NOT NULL COMMENT '酒店名称',
  city            VARCHAR(50)  NOT NULL COMMENT '所在城市',
  area            VARCHAR(100) DEFAULT NULL COMMENT '所在区域 / 商圈',
  address         VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  level           VARCHAR(20)  DEFAULT NULL COMMENT '档次：经济型 / 舒适型 / 高档型',
  price_per_night INT          DEFAULT NULL COMMENT '每晚参考价（元）',
  rating          DECIMAL(2,1) DEFAULT NULL COMMENT '评分 0.0-5.0',
  phone           VARCHAR(50)  DEFAULT NULL COMMENT '联系电话',
  tags            VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔，如 近地铁,含早,免费停车',
  description     VARCHAR(500) DEFAULT NULL COMMENT '简介 / 推荐理由',
  image_url       VARCHAR(255) DEFAULT NULL COMMENT '图片地址',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-上架，0-下架',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (id),
  KEY idx_city (city),
  KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '酒店表（后台维护）';

-- ---------- 4. 示例数据（仅在表为空时插入，便于打开后台就能看到列表）----------
INSERT INTO t_hotel (name, city, area, address, level, price_per_night, rating, phone, tags, description, status)
SELECT * FROM (
  SELECT '长沙五一广场亚朵酒店' AS name, '长沙' AS city, '五一广场' AS area,
         '长沙市芙蓉区五一大道 800 号' AS address, '舒适型' AS level, 468 AS price_per_night,
         4.7 AS rating, '0731-8888xxxx' AS phone, '近地铁,含早,有洗衣房' AS tags,
         '位于五一广场商圈核心，步行可达坡子街与太平老街，地铁 1/2 号线换乘方便。' AS description,
         1 AS status
  UNION ALL
  SELECT '长沙橘子洲江景酒店', '长沙', '橘子洲', '长沙市岳麓区橘子洲大桥西侧',
         '高档型', 688, 4.8, '0731-8666xxxx', '江景,含早,健身房',
         '临湘江，房间可看橘子洲与岳麓山夜景，适合看烟花与散步。', 1
  UNION ALL
  SELECT '长沙高铁南站全季酒店', '长沙', '高铁南站', '长沙市雨花区劳动东路 128 号',
         '经济型', 305, 4.5, '0731-8555xxxx', '近高铁站,24小时前台',
         '距长沙南站步行约 800 米，适合早班高铁或深夜抵达的行程。', 1
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM t_hotel);
