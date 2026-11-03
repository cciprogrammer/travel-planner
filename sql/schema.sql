-- ============================================================
-- 基于大模型的旅行规划助手 Web 系统 —— 数据库脚本
-- MySQL 5.7+ / 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS travel_planner
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE travel_planner;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username     VARCHAR(50)  NOT NULL COMMENT '用户名（唯一）',
  password     VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
  nickname     VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  avatar       VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  role         VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN-管理员，USER-普通用户',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-已禁用',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 旅行方案表
CREATE TABLE IF NOT EXISTS t_travel_plan (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id        BIGINT        NOT NULL COMMENT '所属用户',
  title          VARCHAR(100)  DEFAULT NULL COMMENT '方案标题',
  origin         VARCHAR(100)  DEFAULT NULL COMMENT '出发地',
  destination    VARCHAR(100)  DEFAULT NULL COMMENT '目的地',
  days           INT           DEFAULT NULL COMMENT '出行天数',
  budget         DECIMAL(10, 2) DEFAULT NULL COMMENT '总预算（元）',
  hobbies        VARCHAR(255)  DEFAULT NULL COMMENT '兴趣爱好',
  preferences    VARCHAR(500)  DEFAULT NULL COMMENT '其他偏好',
  summary        TEXT          COMMENT '行程总览',
  itinerary_json LONGTEXT      COMMENT '结构化行程（JSON 字符串）',
  model          VARCHAR(50)   DEFAULT NULL COMMENT '生成模型',
  create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '旅行方案表';

-- 地点收藏表
CREATE TABLE IF NOT EXISTS t_favorite_place (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL COMMENT '所属用户',
  plan_id      BIGINT       DEFAULT NULL COMMENT '来源方案（可为空）',
  name         VARCHAR(100) NOT NULL COMMENT '地点名称',
  city         VARCHAR(50)  DEFAULT NULL COMMENT '所在城市',
  category     VARCHAR(50)  DEFAULT NULL COMMENT '分类',
  address      VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  description  VARCHAR(500) DEFAULT NULL COMMENT '说明',
  rating       VARCHAR(20)  DEFAULT NULL COMMENT '评分',
  image_url    VARCHAR(255) DEFAULT NULL COMMENT '图片地址',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_plan_id (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '地点收藏表';

-- 酒店表（管理员在后台维护）
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
  tags            VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
  description     VARCHAR(500) DEFAULT NULL COMMENT '简介 / 推荐理由',
  image_url       VARCHAR(255) DEFAULT NULL COMMENT '图片地址',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-上架，0-下架',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_city (city),
  KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '酒店表';

-- 已有旧库升级请执行 sql/upgrade-admin.sql（幂等，可重复运行）
