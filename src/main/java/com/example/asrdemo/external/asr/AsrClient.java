package com.example.asrdemo.external.asr;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AsrClient {

    private final WebClient webClient;

    @Value("${asr.base-url}")
    private String asrBaseUrl;

    public AsrResult transcribe(byte[] audioBytes, String filename) {
        // 必须带文件名，否则有些服务器会报 400
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override public String getFilename() {
                return (filename == null || filename.isEmpty()) ? "audio.wav" : filename;
            }
        };

        return webClient.post()
                .uri(asrBaseUrl + "/asr")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", resource))
                .retrieve()
                .bodyToMono(AsrResult.class)
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.empty();
                })
                .block();
    }
}
