package org.takesome.frozenlands.engine.player.camera;

public enum CameraViewMode {
    THIRD_PERSON,
    FIRST_PERSON,
    FRONT_PERSON;

    public CameraViewMode next() {
        return switch (this) {
            case THIRD_PERSON -> FIRST_PERSON;
            case FIRST_PERSON -> FRONT_PERSON;
            case FRONT_PERSON -> THIRD_PERSON;
        };
    }

    public CameraViewMode toggle() {
        return switch (this) {
            case FIRST_PERSON -> THIRD_PERSON;
            case THIRD_PERSON, FRONT_PERSON -> FIRST_PERSON;
        };
    }

    public boolean showsPlayerVisual() {
        return this != FIRST_PERSON;
    }

    public static CameraViewMode parse(String value, CameraViewMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return CameraViewMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
