package com.example.asrdemo.external.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaLlmClient implements LlmClient {

    private final WebClient webClient;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.temperature:0.6}")
    private double temperature;

    @Value("${llm.max-tokens:512}")
    private int maxTokens;

    /** 多轮对话：把历史 messages 一起发给 Ollama */
    @Override
    public String chatWithMessages(String systemPrompt, List<ChatMessage> messages) {
        // 组装 messages：最前面加 system
        Object[] msgs = new Object[messages.size() + 1];
        msgs[0] = Map.of("role", "system", "content", systemPrompt);
        for (int i = 0; i < messages.size(); i++) {
            msgs[i + 1] = Map.of("role", messages.get(i).getRole(),
                    "content", messages.get(i).getContent());
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "options", Map.of(
                        "temperature", temperature,
                        "num_predict", maxTokens,
                        // 上下文窗口（按模型支持调整；不支持时可去掉）
                        "num_ctx", 8192
                ),
                "messages", msgs
        );

        try {
            Map resp = webClient.post()
                    .uri(baseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> Mono.empty())
                    .block();

            if (resp == null) return "";
            Object msg = resp.get("message");
            if (msg instanceof Map m && m.get("content") != null) {
                return m.get("content").toString().trim();
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /** 单轮对话：复用多轮实现 */
    @Override
    public String chat(String systemPrompt, String userText) {
        return chatWithMessages(systemPrompt, List.of(
                new ChatMessage("user", userText)
        ));
    }
}
