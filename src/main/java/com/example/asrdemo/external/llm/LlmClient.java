package com.example.asrdemo.external.llm;

import java.util.List;

public interface LlmClient {
    String chat(String systemPrompt, String userText);
    String chatWithMessages(String modelSystemPrompt, List<ChatMessage> messages);
}
