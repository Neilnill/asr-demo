// external/llm/ChatMessage.java
package com.example.asrdemo.external.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ChatMessage {
    private String role;    // "system" | "user" | "assistant"
    private String content;
}
