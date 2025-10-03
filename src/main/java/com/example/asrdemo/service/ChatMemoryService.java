// service/ChatMemoryService.java
package com.example.asrdemo.service;

import com.example.asrdemo.external.llm.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {
    // key 可用: userId 或 (characterId + 会话id)；这里用 characterId 简化
    private final Map<Long, Deque<ChatMessage>> store = new ConcurrentHashMap<>();

    // 允许保留的历史轮数（双向，一问一答算2条）
    private static final int MAX_TURNS = 10;

    public Deque<ChatMessage> getHistory(Long sessionKey) {
        return store.computeIfAbsent(sessionKey, k -> new ArrayDeque<>());
    }

    public void append(Long sessionKey, ChatMessage msg) {
        Deque<ChatMessage> q = getHistory(sessionKey);
        q.addLast(msg);
        // 控制长度：保留最近 N 轮
        while (q.size() > MAX_TURNS * 2 + 1) { // +1 给 system
            // 删掉最早的非 system 消息
            ChatMessage first = q.peekFirst();
            if (first != null && !"system".equals(first.getRole())) q.removeFirst();
            else break;
        }
    }

    public void clear(Long sessionKey) { store.remove(sessionKey); }
}
