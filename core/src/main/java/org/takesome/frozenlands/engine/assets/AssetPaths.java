package org.takesome.frozenlands.engine.assets;

import java.util.Locale;

final class AssetPaths {
    private AssetPaths() {
    }

    static String requireReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Asset reference must not be blank");
        }
        return value.trim();
    }

    static String canonicalId(String id) {
        return requireReference(id).toLowerCase(Locale.ROOT);
    }

    static String canonicalPath(String path) {
        String raw = requireReference(path).replace('\\', '/');
        while (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        while (raw.startsWith("./")) {
            raw = raw.substring(2);
        }
        if (raw.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "Asset paths must be virtual paths, not disk paths or URIs: " + path
            );
        }

        StringBuilder normalized = new StringBuilder(raw.length());
        for (String segment : raw.split("/+")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "Parent traversal is not allowed in asset paths: " + path
                );
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Asset path resolves to an empty virtual path: " + path
            );
        }
        return normalized.toString();
    }
}
