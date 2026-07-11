package org.takesome.frozenlands.engine.quality;

import java.util.Locale;

public enum GraphicsPreset {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA,
    CUSTOM;

    public static GraphicsPreset parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Graphics preset must not be blank");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
