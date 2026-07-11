package org.takesome.frozenlands.engine.providers;

import java.util.Map;

public final class ProviderArguments {
    private ProviderArguments() {
    }

    public static String string(
            Map<String, Object> arguments,
            String name,
            String fallback
    ) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    public static boolean bool(
            Map<String, Object> arguments,
            String name,
            boolean fallback
    ) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static String reference(Map<String, Object> arguments) {
        return firstNonBlank(
                string(arguments, "asset", ""),
                string(arguments, "path", "")
        );
    }

    public static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        throw new IllegalArgumentException("Required provider argument is blank");
    }

    public static String requiredString(Map<String, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Required provider value is missing: " + key);
        }
        return String.valueOf(value).trim();
    }
}
