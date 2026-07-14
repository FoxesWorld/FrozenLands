package org.takesome.frozenlands.engine.events;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EngineStartupEvents {
    private EngineStartupEvents() {
    }

    public static Map<String, Object> progress(String stage, float progress) {
        return progress(stage, progress, "startup", "");
    }

    public static Map<String, Object> progress(String stage, float progress, String phase) {
        return progress(stage, progress, phase, "");
    }

    public static Map<String, Object> progress(String stage, float progress, String phase, String module) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", safeStage(stage));
        payload.put("progress", clamp(progress));
        if (phase != null && !phase.isBlank()) {
            payload.put("phase", phase.trim());
        }
        if (module != null && !module.isBlank()) {
            payload.put("module", module.trim());
        }
        return payload;
    }

    public static Map<String, Object> stage(String stage, String phase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", safeStage(stage));
        if (phase != null && !phase.isBlank()) {
            payload.put("phase", phase.trim());
        }
        return payload;
    }

    public static Map<String, Object> failed(String stage, String phase, Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", safeStage(stage));
        if (phase != null && !phase.isBlank()) {
            payload.put("phase", phase.trim());
        }
        if (throwable != null) {
            payload.put("error", throwable.getClass().getName());
            payload.put("message", throwable.getMessage());
        }
        return payload;
    }

    private static String safeStage(String stage) {
        return stage == null || stage.isBlank() ? "Loading FrozenLands runtime" : stage.trim();
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
