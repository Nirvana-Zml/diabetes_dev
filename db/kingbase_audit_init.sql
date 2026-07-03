-- =============================================================================
-- DIABETES_AUDIT — 金仓数据库审计库初始化
-- 对应 db/init.sql 中 DIABETES_AUDIT / AUDIT_LOGS 设计
-- =============================================================================

CREATE DATABASE "DIABETES_AUDIT" ENCODING 'UTF8';

\c DIABETES_AUDIT

CREATE TABLE IF NOT EXISTS AUDIT_LOGS (
    LOG_ID            VARCHAR(32)   PRIMARY KEY,
    USER_ID           VARCHAR(32)   NOT NULL,
    ACTION            VARCHAR(100)  NOT NULL,
    RESOURCE          VARCHAR(200)  NOT NULL,
    DETAIL            JSON          DEFAULT NULL,
    IP_ADDRESS        VARCHAR(50)   DEFAULT NULL,
    USER_AGENT        VARCHAR(500)  DEFAULT NULL,
    RESULT            SMALLINT      NOT NULL,
    CREATED_AT        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS IDX_USER_TIME ON AUDIT_LOGS (USER_ID, CREATED_AT DESC);
CREATE INDEX IF NOT EXISTS IDX_ACTION ON AUDIT_LOGS (ACTION);

COMMENT ON TABLE AUDIT_LOGS IS '审计日志表';
COMMENT ON COLUMN AUDIT_LOGS.LOG_ID IS '日志ID';
COMMENT ON COLUMN AUDIT_LOGS.USER_ID IS '操作用户ID';
COMMENT ON COLUMN AUDIT_LOGS.ACTION IS '操作类型';
COMMENT ON COLUMN AUDIT_LOGS.RESOURCE IS '操作资源';
COMMENT ON COLUMN AUDIT_LOGS.DETAIL IS '操作详情';
COMMENT ON COLUMN AUDIT_LOGS.IP_ADDRESS IS '操作IP';
COMMENT ON COLUMN AUDIT_LOGS.USER_AGENT IS 'User-Agent';
COMMENT ON COLUMN AUDIT_LOGS.RESULT IS '结果：1成功 0失败';
COMMENT ON COLUMN AUDIT_LOGS.CREATED_AT IS '操作时间';

-- -----------------------------------------------------------------------------
-- 操作类型定义表：将操作码与中文标签、分类持久化，供管理端筛选与导出使用
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS AUDIT_ACTION_DEFINITIONS (
    ACTION_CODE     VARCHAR(100)  PRIMARY KEY,
    LABEL_ZH        VARCHAR(100)  NOT NULL,
    CATEGORY        VARCHAR(50)   NOT NULL,
    IS_SYSTEM       SMALLINT      NOT NULL DEFAULT 1,
    ENABLED         SMALLINT      NOT NULL DEFAULT 1,
    SORT_ORDER      INTEGER       NOT NULL DEFAULT 0,
    CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS IDX_ACTION_DEF_CATEGORY ON AUDIT_ACTION_DEFINITIONS (CATEGORY, SORT_ORDER);

COMMENT ON TABLE AUDIT_ACTION_DEFINITIONS IS '审计操作类型定义表';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.ACTION_CODE IS '操作码，如 user.login';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.LABEL_ZH IS '中文标签';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.CATEGORY IS '分类：user/admin/article/video/audit/data';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.IS_SYSTEM IS '是否系统内置：1是 0否';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.ENABLED IS '是否启用：1是 0否';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.SORT_ORDER IS '排序权重';
COMMENT ON COLUMN AUDIT_ACTION_DEFINITIONS.CREATED_AT IS '创建时间';

INSERT INTO AUDIT_ACTION_DEFINITIONS (ACTION_CODE, LABEL_ZH, CATEGORY, IS_SYSTEM, ENABLED, SORT_ORDER) VALUES
    ('user.login',              '用户登录',       'user',    1, 1, 10),
    ('user.logout',             '用户登出',       'user',    1, 1, 20),
    ('user.register',           '用户注册',       'user',    1, 1, 30),
    ('user.password.change',    '用户修改密码',   'user',    1, 1, 40),
    ('user.password.reset',     '用户重置密码',   'user',    1, 1, 50),
    ('admin.login',             '管理员登录',     'admin',   1, 1, 110),
    ('admin.logout',            '管理员登出',     'admin',   1, 1, 120),
    ('admin.password.change',   '管理员修改密码', 'admin',   1, 1, 130),
    ('data.export',             '数据导出',       'data',    1, 1, 210),
    ('article.create',          '资讯创建',       'article', 1, 1, 310),
    ('article.update',          '资讯编辑',       'article', 1, 1, 320),
    ('article.delete',          '资讯删除',       'article', 1, 1, 330),
    ('article.cover.upload',    '资讯封面上传',   'article', 1, 1, 340),
    ('article.publish',         '资讯发布',       'article', 1, 1, 350),
    ('article.review',          '资讯审核',       'article', 1, 1, 360),
    ('video.create',            '视频创建',       'video',   1, 1, 410),
    ('video.update',            '视频编辑',       'video',   1, 1, 420),
    ('video.delete',            '视频删除',       'video',   1, 1, 430),
    ('video.cover.upload',      '视频封面上传',   'video',   1, 1, 440),
    ('video.file.upload',       '视频文件上传',   'video',   1, 1, 450),
    ('audit.delete',            '审计日志删除',   'audit',   1, 1, 510),
    ('audit.export',            '审计日志导出',   'audit',   1, 1, 520)
ON CONFLICT (ACTION_CODE) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 归档日志表：删除审计记录前先归档，满足合规留存要求
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS AUDIT_ARCHIVED_LOGS (
    ARCHIVE_ID        VARCHAR(32)   PRIMARY KEY,
    ORIGINAL_LOG_ID   VARCHAR(32)   NOT NULL,
    USER_ID           VARCHAR(32)   NOT NULL,
    ACTION            VARCHAR(100)  NOT NULL,
    RESOURCE          VARCHAR(200)  NOT NULL,
    DETAIL            JSON          DEFAULT NULL,
    IP_ADDRESS        VARCHAR(50)   DEFAULT NULL,
    USER_AGENT        VARCHAR(500)  DEFAULT NULL,
    RESULT            SMALLINT      NOT NULL,
    CREATED_AT        TIMESTAMP     NOT NULL,
    ARCHIVED_AT       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    ARCHIVED_BY       VARCHAR(32)   NOT NULL,
    ARCHIVE_REASON    VARCHAR(200)  DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS IDX_ARCHIVE_ORIGINAL ON AUDIT_ARCHIVED_LOGS (ORIGINAL_LOG_ID);
CREATE INDEX IF NOT EXISTS IDX_ARCHIVE_TIME ON AUDIT_ARCHIVED_LOGS (ARCHIVED_AT DESC);
CREATE INDEX IF NOT EXISTS IDX_ARCHIVE_BY ON AUDIT_ARCHIVED_LOGS (ARCHIVED_BY, ARCHIVED_AT DESC);

COMMENT ON TABLE AUDIT_ARCHIVED_LOGS IS '审计日志归档表';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ARCHIVE_ID IS '归档记录ID';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ORIGINAL_LOG_ID IS '原 AUDIT_LOGS.LOG_ID';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.USER_ID IS '原操作用户ID';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ACTION IS '原操作类型';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.RESOURCE IS '原操作资源';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.DETAIL IS '原操作详情';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.IP_ADDRESS IS '原操作IP';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.USER_AGENT IS '原 User-Agent';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.RESULT IS '原结果：1成功 0失败';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.CREATED_AT IS '原操作时间';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ARCHIVED_AT IS '归档时间';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ARCHIVED_BY IS '执行归档的管理员ID';
COMMENT ON COLUMN AUDIT_ARCHIVED_LOGS.ARCHIVE_REASON IS '归档原因';
