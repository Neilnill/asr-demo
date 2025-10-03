package com.example.asrdemo.controller;

import com.example.asrdemo.external.asr.AsrClient;
import com.example.asrdemo.external.asr.AsrResult;
import com.example.asrdemo.external.llm.ChatMessage;
import com.example.asrdemo.external.llm.LlmClient;
import com.example.asrdemo.service.ChatMemoryService;
import com.example.asrdemo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class ChatController {

    private final LlmClient llmClient;
    private final AsrClient asrClient;
    private final WebClient webClient;
    private final ChatMemoryService memory;
    private final RoleService roleService;

    @Value("${tts.base-url:http://127.0.0.1:5002}")
    private String ttsBaseUrl;

    /** 默认角色的系统提示（当在角色库里找不到 id 时使用） */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一名简洁、友好的中文助手。
            目标：帮助用户解决问题并给出可执行建议。
            风格：短句、分点，最多 5 条；信息不足先提出 1-2 个澄清问题。
            边界：不编造成就/事实；不确定要说明并提供下一步建议。
            格式：使用 Markdown 列表或小标题，避免长段落。
            """;

    @PostMapping(
            path = "/{id}/chat",
            consumes = {
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.ALL_VALUE
            },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> chat(@PathVariable("id") Long id,
                                    @RequestPart(value = "user_audio", required = false) MultipartFile userAudio,
                                    @RequestParam(value = "text", required = false) String text,
                                    // ↓↓↓ 新增三项：允许前端临时覆盖角色默认音色/语速/音量
                                    @RequestParam(value = "ttsVoice", required = false) String ttsVoice,
                                    @RequestParam(value = "ttsRate", required = false) String ttsRate,
                                    @RequestParam(value = "ttsVolume", required = false) String ttsVolume) throws Exception {

        // 0) 角色与技能、TTS 声线参数
        Map<String, Object> role = roleService.findById(id);
        String systemPrompt = role != null
                ? String.valueOf(role.getOrDefault("systemPrompt", DEFAULT_SYSTEM_PROMPT))
                : DEFAULT_SYSTEM_PROMPT;

        String voice  = role != null ? String.valueOf(role.getOrDefault("voice",  "zh-CN-XiaoxiaoNeural")) : "zh-CN-XiaoxiaoNeural";
        String rate   = role != null ? String.valueOf(role.getOrDefault("rate",   "+0%")) : "+0%";
        String volume = role != null ? String.valueOf(role.getOrDefault("volume", "+0%")) : "+0%";

        // 选填：根据角色的 skills 开关，增强 systemPrompt
        if (role != null && role.get("skills") instanceof Map<?,?> skills) {
            if (Boolean.TRUE.equals(skills.get("socratic"))) {
                systemPrompt += "\n- 技能：先提出1-2个澄清/启发问题，再简述建议。";
            }
            if (Boolean.TRUE.equals(skills.get("loreStrict"))) {
                systemPrompt += "\n- 技能：严格遵从设定与世界观，对越界问题礼貌拒绝并引导回设定。";
            }
            if (Boolean.TRUE.equals(skills.get("summary"))) {
                systemPrompt += "\n- 技能：当用户输入 #总结 时，输出要点与TODO。";
            }
        }

        // ★★ 覆盖逻辑：如果前端传了临时音色参数，就覆盖角色默认值
        if (StringUtils.hasText(ttsVoice))  voice  = ttsVoice.trim();
        if (StringUtils.hasText(ttsRate))   rate   = ttsRate.trim();
        if (StringUtils.hasText(ttsVolume)) volume = ttsVolume.trim();

        // 1) 拿用户文本（优先语音识别）
        String userText = StringUtils.hasText(text) ? text.trim() : "";
        if (userAudio != null && !userAudio.isEmpty()) {
            AsrResult asr = asrClient.transcribe(
                    userAudio.getBytes(),
                    userAudio.getOriginalFilename() == null ? "audio.wav" : userAudio.getOriginalFilename()
            );
            if (asr != null && StringUtils.hasText(asr.getText())) {
                userText = asr.getText().trim();
            }
        }
        if (!StringUtils.hasText(userText)) {
            userText = "（未识别到有效内容）";
        }

        // 2) 会话历史（按 characterId）
        var history = memory.getHistory(id);
        memory.append(id, new ChatMessage("user", userText));

        // 3) 带上下文调用 LLM
        String replyText = llmClient.chatWithMessages(systemPrompt, List.copyOf(history));
        if (!StringUtils.hasText(replyText)) {
            replyText = "我没完全听清，可以再说一遍吗？";
        }

        // 4) 加入历史
        memory.append(id, new ChatMessage("assistant", replyText));

        // 5) 给 TTS 前，去掉 Markdown
        String ttsText = markdownToPlain(replyText);
        String audioUrl = synthUrl(ttsText, voice, rate, volume);

        // 6) 返回
        Map<String, Object> resp = new HashMap<>();
        resp.put("characterId", id);
        resp.put("userText", userText);
        resp.put("replyText", replyText); // 保留 Markdown 供前端渲染
        resp.put("audioUrl", audioUrl);
        resp.put("asrUsed", userAudio != null);
        resp.put("voice", voice);
        resp.put("rate", rate);
        resp.put("volume", volume);
        return resp;
    }

    /** 清空该角色会话（可选） */
    @DeleteMapping("/{id}/chat/history")
    public Map<String, Object> clearHistory(@PathVariable("id") Long id) {
        memory.clear(id);
        return Map.of("cleared", true, "characterId", id);
    }

    /** 访问 tts_server 的 /tts_url，传入声线参数，返回完整可播 URL */
    private String synthUrl(String text, String voice, String rate, String volume) {
        try {
            Map<String, Object> body = Map.of(
                    "text", text,
                    "voice", voice,
                    "rate", rate,
                    "volume", volume
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> r = webClient.post()
                    .uri(trimRight(ttsBaseUrl) + "/tts_url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> Mono.empty())
                    .block();

            if (r == null) return null;
            Object url = r.get("url");
            if (url == null) return null;

            String u = url.toString();
            if (u.startsWith("http://") || u.startsWith("https://")) {
                return u;
            }
            return trimRight(ttsBaseUrl) + (u.startsWith("/") ? u : ("/" + u));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String trimRight(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** 把 Markdown 清洗成适合朗读的纯文本，避免 TTS 读出星号/链接等标记 */
    private static String markdownToPlain(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?s)```.*?```", " ");
        s = s.replaceAll("`([^`]+)`", "$1");
        s = s.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ");
        s = s.replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1");
        s = s.replaceAll("([*_]{1,3})([^*_]+)\\1", "$2");
        s = s.replaceAll("(?m)^\\s{0,3}[#>\\-+*]+\\s*", "");
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }
}
