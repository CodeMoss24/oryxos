CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    duration_ms BIGINT NOT NULL,
    success INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    input_json TEXT,
    result_json TEXT,
    success INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_ms BIGINT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    channel TEXT NOT NULL,
    user_id TEXT NOT NULL,
    messages_json TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at TEXT
);

-- memory_entries:长期记忆条目(手工建表,与 sessions/llm_calls 同口径)
CREATE TABLE IF NOT EXISTS memory_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scope VARCHAR(16) NOT NULL,          -- CORE / ARCHIVAL
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_scope ON memory_entries (scope);

-- scheduled_tasks:定时任务登记与运行状态(第 28 节,定义来源仍是 Skill/Profile 的 schedules,
-- 此表只存"状态+历史",不作为定义源,重启时从文件重新注册)
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    task_id TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    cron TEXT NOT NULL,
    zone TEXT,
    message TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    next_run_at TEXT,
    last_run_at TEXT,
    last_status TEXT,
    run_count INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

-- task_executions:定时任务每次执行历史(第 28 节,成功失败都记,与宪法 V 审计同源)
CREATE TABLE IF NOT EXISTS task_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    success INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_ms BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_task_executions_task ON task_executions (task_id);
-- provider_configs:Provider 动态注册表(管理台 CRUD,name 主键;启动播种,之后注册表是唯一事实源)
CREATE TABLE IF NOT EXISTS provider_configs (
    name TEXT PRIMARY KEY,
    api_key TEXT,
    base_url TEXT,
    description TEXT,
    created_at TEXT,
    updated_at TEXT
);

-- notify_channels:全局通知渠道注册表(管理台 CRUD,Agent 按名引用;name 主键)
CREATE TABLE IF NOT EXISTS notify_channels (
    name TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    url TEXT NOT NULL,
    description TEXT,
    created_at TEXT,
    updated_at TEXT
);

-- sandbox_whitelist_entries:Sandbox 白名单持久化(运行时增删写穿、重启恢复;(category, entry_value) 唯一)
CREATE TABLE IF NOT EXISTS sandbox_whitelist_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    entry_value TEXT NOT NULL,
    created_at TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sandbox_whitelist_cat_val ON sandbox_whitelist_entries (category, entry_value);

-- agent_executions:Agent 每次执行历史(手动/定时都记,成功失败都记)
CREATE TABLE IF NOT EXISTS agent_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_name TEXT,
    source TEXT,
    session_id TEXT,
    started_at TEXT,
    ended_at TEXT,
    success INTEGER,
    error_message TEXT,
    duration_ms BIGINT
);
CREATE INDEX IF NOT EXISTS idx_agent_executions_agent ON agent_executions (agent_name);
