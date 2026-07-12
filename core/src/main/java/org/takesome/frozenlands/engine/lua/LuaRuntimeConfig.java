package org.takesome.frozenlands.engine.lua;

import org.takesome.frozenlands.engine.config.RuntimeConfigStore;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Map;

public final class LuaRuntimeConfig {
    private static final String CORE_MODULE_ID = "engine.core";

    private final ModuleIndexCatalog moduleIndexCatalog = ModuleIndexCatalog.defaultCatalog();

    public Map<String, Object> read(String moduleId) {
        if (CORE_MODULE_ID.equals(moduleId)) {
            return RuntimeConfigStore.core(moduleIndexCatalog).readMap();
        }
        String indexedPath = moduleIndexCatalog.optionalConfigPath(moduleId, "runtime");
        return indexedPath == null ? Map.of() : moduleIndexCatalog.readJsonMap(indexedPath);
    }

    public Map<String, Object> map(Map<String, Object> source, String key) {
        return RuntimeMaps.map(source, key);
    }

    public String string(Map<String, Object> source, String key, String fallback) {
        return RuntimeMaps.string(source, key, fallback);
    }

    public int integer(Map<String, Object> source, String key, int fallback) {
        return RuntimeMaps.integer(source, key, fallback);
    }

    public float floating(Map<String, Object> source, String key, float fallback) {
        return RuntimeMaps.floating(source, key, fallback);
    }

    public boolean bool(Map<String, Object> source, String key, boolean fallback) {
        return RuntimeMaps.bool(source, key, fallback);
    }
}
