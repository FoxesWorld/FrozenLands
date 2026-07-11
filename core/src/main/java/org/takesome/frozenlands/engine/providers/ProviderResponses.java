package org.takesome.frozenlands.engine.providers;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProviderResponses {
    private ProviderResponses() {
    }

    public static Map<String, Object> ok() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        return result;
    }

    public static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> result = ok();
        result.put(key, value);
        return result;
    }

    public static Map<String, Object> ok(Map<String, ?> values) {
        Map<String, Object> result = ok();
        if (values != null) {
            values.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }
}
