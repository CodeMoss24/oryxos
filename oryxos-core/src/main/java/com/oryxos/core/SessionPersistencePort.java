package com.oryxos.core;

import com.oryxos.core.session.Session;

/** Session 持久化端口。由 oryxos-provider 实现,用 SessionRepository 写入 SQLite。 */
public interface SessionPersistencePort {

  void save(Session session);
}
