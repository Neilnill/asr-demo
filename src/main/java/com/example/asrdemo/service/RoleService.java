package com.example.asrdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class RoleService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> roles = new ArrayList<>();

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getResourceAsStream("/roles.json")) {
            if (is != null) {
                List<Map<String, Object>> loaded =
                        mapper.readValue(is, new TypeReference<>() {});
                roles.addAll(loaded);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load roles.json", e);
        }
    }

    public List<Map<String, Object>> all() {
        return Collections.unmodifiableList(roles);
    }

    public Map<String, Object> findById(long id) {
        return roles.stream()
                .filter(r -> Objects.equals(Long.valueOf(String.valueOf(r.get("id"))), id))
                .findFirst().orElse(null);
    }
}
