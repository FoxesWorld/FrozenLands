package org.takesome.frozenlands.engine.ui.screen;

import java.util.LinkedHashMap;
import java.util.Map;

public record UiScreenModel(String id, String stage, float progress, Map<String, Object> properties) {
    public static final String LOADING = "loading";

    public UiScreenModel {
        id = id == null || id.isBlank() ? LOADING : id.trim();
        stage = stage == null || stage.isBlank() ? "" : stage.trim();
        progress = clamp(progress);
        properties = properties == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(properties));
    }

    public static UiScreenModel loading(String stage, float progress) {
        return new UiScreenModel(LOADING, stage, progress, Map.of());
    }

    public UiScreenModel withProgress(String nextStage, float nextProgress) {
        return new UiScreenModel(id, nextStage, nextProgress, properties);
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
