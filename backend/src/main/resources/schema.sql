-- DevFusion 全量建表（MySQL 8/9, utf8mb4）
-- 幂等：DROP IF EXISTS 再建，初始化数据由 DataSeeder 在应用启动时写入

SET NAMES utf8mb4;

DROP TABLE IF EXISTS deployment;
DROP TABLE IF EXISTS artifact;
DROP TABLE IF EXISTS run_job;
DROP TABLE IF EXISTS run_stage;
DROP TABLE IF EXISTS pipeline_run;
DROP TABLE IF EXISTS pipeline_job;
DROP TABLE IF EXISTS pipeline_stage;
DROP TABLE IF EXISTS pipeline;
DROP TABLE IF EXISTS environment;
DROP TABLE IF EXISTS application;
DROP TABLE IF EXISTS test_case;
DROP TABLE IF EXISTS work_item_activity;
DROP TABLE IF EXISTS work_item_comment;
DROP TABLE IF EXISTS work_item;
DROP TABLE IF EXISTS iteration;
DROP TABLE IF EXISTS work_item_status;
DROP TABLE IF EXISTS project_member;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    email       VARCHAR(100) NULL,
    phone       VARCHAR(20)  NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `key`       VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    color       VARCHAR(10)  NULL,
    start_date  DATE         NULL,
    end_date    DATE         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE project_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role_code  VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_user (project_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE work_item_status (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    type       VARCHAR(10) NOT NULL,
    code       VARCHAR(30) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    category   VARCHAR(20) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    is_initial TINYINT     NOT NULL DEFAULT 0,
    is_final   TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status_project (project_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE iteration (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    goal       VARCHAR(500) NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date DATE         NULL,
    end_date   DATE         NULL,
    created_by BIGINT       NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    KEY idx_iter_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE work_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT       NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    item_no        VARCHAR(30)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    description    TEXT         NULL,
    status_code    VARCHAR(30)  NOT NULL,
    priority       VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    assignee_id    BIGINT       NULL,
    reporter_id    BIGINT       NULL,
    iteration_id   BIGINT       NULL,
    parent_id      BIGINT       NULL,
    story_point    DOUBLE       NULL,
    plan_start_date DATE        NULL,
    plan_end_date  DATE         NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_item_no (project_id, item_no),
    KEY idx_item_project (project_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE work_item_comment (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    content    TEXT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT NOT NULL DEFAULT 0,
    KEY idx_comment_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE work_item_activity (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id    BIGINT       NOT NULL,
    project_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    action     VARCHAR(20)  NOT NULL,
    detail     VARCHAR(1000) NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_activity_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE test_case (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT       NOT NULL,
    case_no            VARCHAR(30)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    precondition       TEXT         NULL,
    steps              TEXT         NULL,
    expected           TEXT         NULL,
    priority           VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    module             VARCHAR(50)  NULL,
    latest_result      VARCHAR(20)  NOT NULL DEFAULT 'UNEXECUTED',
    latest_executor_id BIGINT       NULL,
    latest_executed_at DATETIME     NULL,
    bug_item_id        BIGINT       NULL,
    creator_id         BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    KEY idx_case_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE application (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `key`       VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    repo_url    VARCHAR(200) NULL,
    language    VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    owner_id    BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE environment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `key`       VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    type        VARCHAR(20)  NOT NULL DEFAULT 'TEST',
    description VARCHAR(200) NULL,
    hosts       TEXT         NULL,
    sort        INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(300) NULL,
    trigger_type   VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    webhook_token  VARCHAR(64)  NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    KEY idx_pipe_app (application_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline_stage (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline_job (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    stage_id         BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    config           TEXT         NULL,
    continue_on_fail TINYINT      NOT NULL DEFAULT 0,
    sort             INT          NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pipeline_run (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_id     BIGINT      NOT NULL,
    application_id  BIGINT      NOT NULL,
    version         VARCHAR(60) NULL,
    trigger_type    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    trigger_user_id BIGINT      NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at      DATETIME    NULL,
    finished_at     DATETIME    NULL,
    duration_ms     BIGINT      NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_run_pipe (pipeline_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE run_stage (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      BIGINT       NOT NULL,
    stage_id    BIGINT       NULL,
    name        VARCHAR(100) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    started_at  DATETIME     NULL,
    finished_at DATETIME     NULL,
    KEY idx_runstage_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE run_job (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id           BIGINT       NOT NULL,
    run_stage_id     BIGINT       NOT NULL,
    job_id           BIGINT       NULL,
    name             VARCHAR(100) NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    config           TEXT         NULL,
    continue_on_fail TINYINT      NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    log              MEDIUMTEXT   NULL,
    started_at       DATETIME     NULL,
    finished_at      DATETIME     NULL,
    KEY idx_runjob_run (run_id),
    KEY idx_runjob_stage (run_stage_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE artifact (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    version        VARCHAR(60)  NOT NULL,
    pipeline_run_id BIGINT      NULL,
    type           VARCHAR(20)  NOT NULL DEFAULT 'JAR',
    path           VARCHAR(200) NULL,
    size_bytes     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_artifact_app (application_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE deployment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    artifact_id    BIGINT       NOT NULL,
    environment_id BIGINT       NOT NULL,
    version        VARCHAR(60)  NOT NULL,
    deploy_type    VARCHAR(20)  NOT NULL DEFAULT 'DEPLOY',
    rollback_of_id BIGINT       NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    operator_id    BIGINT       NULL,
    log            MEDIUMTEXT   NULL,
    started_at     DATETIME     NULL,
    finished_at    DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_deploy_app (application_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ===== PM 扩展 =====
-- work_item 在上方重建后在此补工时/发布字段（保留字 release 加反引号）
ALTER TABLE work_item
    ADD COLUMN estimate_hours DOUBLE NULL COMMENT '预估工时',
    ADD COLUMN remaining_hours DOUBLE NULL COMMENT '剩余工时',
    ADD COLUMN spent_hours DOUBLE NULL COMMENT '已耗工时',
    ADD COLUMN release_id BIGINT NULL;

DROP TABLE IF EXISTS worklog;
DROP TABLE IF EXISTS work_item_view;
DROP TABLE IF EXISTS test_plan_case;
DROP TABLE IF EXISTS test_plan;
DROP TABLE IF EXISTS `release`;

CREATE TABLE test_plan (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    owner_id    BIGINT       NULL,
    stage       VARCHAR(50)  NULL,
    iteration_id BIGINT      NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    KEY idx_plan_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE test_plan_case (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id     BIGINT       NOT NULL,
    case_id     BIGINT       NOT NULL,
    executor_id BIGINT       NULL,
    result      VARCHAR(20)  NOT NULL DEFAULT 'UNEXECUTED',
    note        VARCHAR(500) NULL,
    executed_at DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_plan_case (plan_id, case_id),
    KEY idx_tpc_case (case_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE worklog (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    hours      DOUBLE      NOT NULL,
    remaining  DOUBLE      NULL,
    log_date   DATE        NOT NULL,
    note       VARCHAR(500) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    KEY idx_worklog_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE work_item_view (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    type        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_public   TINYINT      NOT NULL DEFAULT 0,
    owner_id    BIGINT       NULL,
    filter_json TEXT         NULL,
    sort        VARCHAR(100) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    KEY idx_view_project (project_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `release` (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    version      VARCHAR(50)  NOT NULL,
    release_date DATE         NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    description  VARCHAR(500) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_release_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ===== DevOps 扩展 =====
-- 已有表增量列（上方已 DROP+CREATE 重建，此处追加列，保持幂等）
ALTER TABLE pipeline
    ADD COLUMN cron VARCHAR(64) NULL COMMENT '定时触发 Cron 表达式';
ALTER TABLE pipeline_job
    ADD COLUMN `parallel` TINYINT NOT NULL DEFAULT 0 COMMENT '1=stage 内并行',
    ADD COLUMN timeout_minutes INT NULL COMMENT '任务超时分钟';
ALTER TABLE run_job
    ADD COLUMN `parallel` TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN timeout_minutes INT NULL;
ALTER TABLE environment
    ADD COLUMN require_approval TINYINT NOT NULL DEFAULT 0 COMMENT '部署需审批',
    ADD COLUMN host_group_id BIGINT NULL;
ALTER TABLE artifact
    ADD COLUMN stage VARCHAR(20) NOT NULL DEFAULT 'SNAPSHOT' COMMENT 'SNAPSHOT 半成品/RELEASE 成品',
    ADD COLUMN release_version VARCHAR(30) NULL COMMENT '三位发布版本';
ALTER TABLE deployment
    ADD COLUMN deploy_template_id BIGINT NULL;

DROP TABLE IF EXISTS run_work_item;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS host;
DROP TABLE IF EXISTS host_group;
DROP TABLE IF EXISTS deploy_template;
DROP TABLE IF EXISTS access_token;
DROP TABLE IF EXISTS run_variable;
DROP TABLE IF EXISTS pipeline_variable;

CREATE TABLE pipeline_variable (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_id     BIGINT       NOT NULL,
    `name`          VARCHAR(80)  NOT NULL,
    default_value   TEXT         NULL,
    secret          TINYINT      NOT NULL DEFAULT 0,
    runtime_editable TINYINT     NOT NULL DEFAULT 1 COMMENT '手动触发时可改',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pvar_pipe_name (pipeline_id, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE run_variable (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id     BIGINT      NOT NULL,
    `name`     VARCHAR(80) NOT NULL,
    value      TEXT        NULL,
    secret     TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_runvar_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE access_token (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(128) NOT NULL UNIQUE,
    last_used_at DATETIME     NULL,
    expires_at   DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_token_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE deploy_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id          BIGINT       NOT NULL,
    `name`          VARCHAR(100) NOT NULL,
    pre_script      TEXT         NULL,
    post_script     TEXT         NULL,
    health_check_url VARCHAR(200) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tpl_app (app_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE host_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`      VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(200) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE host (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id   BIGINT       NOT NULL,
    `name`     VARCHAR(100) NOT NULL,
    ip         VARCHAR(50)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    KEY idx_host_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NULL,
    link       VARCHAR(200) NULL,
    read_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notif_user (user_id, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE run_work_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id        BIGINT      NOT NULL,
    work_item_id  BIGINT      NOT NULL,
    work_item_no  VARCHAR(30) NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rwi_run_item (run_id, work_item_id),
    KEY idx_rwi_item (work_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
