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