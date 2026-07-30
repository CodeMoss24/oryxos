package com.oryxos.memory;

import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Session;
import org.springframework.stereotype.Service;

/**
 * MemoryService 统一门面实现。对 ReAct 循环暴露统一的记忆读写接口。
 * 内部把会话记忆委托给 Session(由 AgentService 传入),把长期记忆委托给 LongTermMemoryStore。
 */
@Service
public class MemoryServiceImpl implements MemoryService {

    private final LongTermMemoryStore longTermMemoryStore;

    public MemoryServiceImpl(LongTermMemoryStore longTermMemoryStore) {
        this.longTermMemoryStore = longTermMemoryStore;
    }

    @Override
    public String loadContext(Profile profile, Session session) {
        StringBuilder sb = new StringBuilder();
        if (session != null && session.getMessages() != null) {
            sb.append("## 会话历史\n");
            for (var msg : session.getMessages()) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }
        String longTerm = longTermMemoryStore.load();
        if (longTerm != null && !longTerm.isBlank()) {
            sb.append("\n## 长期记忆\n").append(longTerm);
        }
        return sb.toString();
    }

    @Override
    public void append(String content, String scope) {
        MemoryScope memoryScope = "CORE".equalsIgnoreCase(scope)
                ? MemoryScope.CORE
                : MemoryScope.ARCHIVAL;
        longTermMemoryStore.append(content, memoryScope);
    }

    @Override
    public String recallByKeyword(String query) {
        return longTermMemoryStore.recallByKeyword(query);
    }
}
