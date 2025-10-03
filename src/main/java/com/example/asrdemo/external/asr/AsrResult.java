package com.example.asrdemo.external.asr;

import lombok.Data;
import java.util.List;

@Data
public class AsrResult {
    private String text;
    private String lang;
    private List<Segment> segments;

    @Data
    public static class Segment {
        private double start;
        private double end;
        private String text;
    }
}
