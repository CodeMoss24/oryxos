package com.oryxos.core.session;

import java.util.List;
import java.util.Optional;

/**
 * 会话管理:全入口(CLI/Web/定时)共用的会话获取、查询与保存出口。
 *
 * <p>所有入口只提供 channel + user + profile 三元组,不自行拼接 session_id——id 的拼接只发生在本接口
 * 的实现内部这一处,避免两处各拼一遍、格式差一个分隔符导致同一人出现两条互不相认的历史。
 */
public interface SessionManager {

  /** 按三元组获取或创建会话。幂等:同一三元组历次调用返回同一个 Session(含已有历史)。 */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按 sessionId 获取会话,不存在返回空。 */
  Optional<Session> get(String sessionId);

  /** 持久化会话(含对话历史 messages_json)。 */
  void save(Session session);

  /** 列出全部会话,按 lastActiveAt 倒序。供管理台会话列表等只读查询。 */
  List<Session> listAll();

  /** 列出最近 N 个会话,按 lastActiveAt 倒序。供会话列表端点(默认 100 条)使用。 */
  List<Session> listRecent(int limit);
}
