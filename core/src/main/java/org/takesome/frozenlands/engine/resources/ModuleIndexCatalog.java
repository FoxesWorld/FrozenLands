package org.takesome.frozenlands.engine.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ModuleIndexCatalog {
    private static final ModuleIndexCatalog DEFAULT = new ModuleIndexCatalog(resolveDefaultProjectRoot());

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path projectRoot;
    private final Map<String, ModuleIndex> indexes = new LinkedHashMap<>();

    public ModuleIndexCatalog(Path projectRoot) {
        this.projectRoot = projectRoot;
        loadIndexes();
    }

    public static ModuleIndexCatalog defaultCatalog() {
        return DEFAULT;
    }

    public Path projectRoot() {
        return projectRoot;
    }


    private static Path resolveDefaultProjectRoot() {
        String configuredRoot = System.getProperty("frozenlands.runtimeRoot");
        if (configuredRoot == null || configuredRoot.isBlank()) {
            configuredRoot = System.getenv("FROZENLANDS_RUNTIME_ROOT");
        }
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot).toAbsolutePath().normalize();
        }

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (isRuntimeRoot(workingDirectory)) {
            return workingDirectory;
        }

        try {
            var codeSource = ModuleIndexCatalog.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path codePath = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                Path start = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
                Path discovered = findRuntimeRoot(start);
                if (discovered != null) {
                    return discovered;
                }
            }
        } catch (Exception ignored) {
        }

        return workingDirectory;
    }

    private static Path findRuntimeRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (isRuntimeRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isRuntimeRoot(Path candidate) {
        return candidate != null
                && Files.isDirectory(candidate.resolve("assets"))
                && Files.isDirectory(candidate.resolve("modulesSrc"));
    }

    public boolean hasModule(String moduleId) {
        return indexes.containsKey(moduleId);
    }

    public String configPath(String moduleId, String key) {
        ModuleIndex index = requireIndex(moduleId);
        Object path = index.configs().get(key);
        if (path == null) {
            throw new IllegalArgumentException("Module config is not declared in module.index.json: " + moduleId + ":" + key);
        }
        return index.resolve(String.valueOf(path)).toString();
    }

    public String optionalConfigPath(String moduleId, String key) {
        ModuleIndex index = requireIndex(moduleId);
        Object path = index.configs().get(key);
        return path == null ? null : index.resolve(String.valueOf(path)).toString();
    }

    public String luaPath(String moduleId, String kind) {
        ModuleIndex index = requireIndex(moduleId);
        Object path = index.lua().get(kind);
        if (path == null) {
            throw new IllegalArgumentException("Module Lua path is not declared in module.index.json: " + moduleId + ":" + kind);
        }
        return index.resolve(String.valueOf(path)).toString();
    }

    public String readLua(String moduleId, String kind) {
        Path luaPath = Path.of(luaPath(moduleId, kind));
        if (!Files.isRegularFile(luaPath)) {
            throw new IllegalArgumentException("Module Lua file is not found: " + luaPath);
        }
        try {
            return Files.readString(luaPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read module Lua file: " + luaPath, e);
        }
    }

    public Map<String, Object> readConfigMap(String moduleId, String key) {
        return readJsonMap(configPath(moduleId, key));
    }

    public Map<String, Object> readJsonMap(String path) {
        JsonNode node = readJsonNode(path);
        return mapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    public JsonNode readJsonNode(String path) {
        Path mutablePath = resolveProjectPath(path);
        if (!Files.isRegularFile(mutablePath)) {
            throw new IllegalArgumentException("Mutable JSON file is not found: " + mutablePath);
        }
        try {
            return mapper.readTree(mutablePath.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mutable JSON: " + mutablePath, e);
        }
    }

    public JsonNode readAssetJsonNode(AssetManager assetManager, String path) {
        AssetInfo info = assetManager.locateAsset(new AssetKey<>(path));
        if (info == null) {
            throw new IllegalArgumentException("Asset JSON is not found through indexed asset roots: " + path);
        }
        try (InputStream inputStream = info.openStream()) {
            return mapper.readTree(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read asset JSON: " + path, e);
        }
    }

    public Map<String, Object> readAssetJsonMap(AssetManager assetManager, String path) {
        JsonNode node = readAssetJsonNode(assetManager, path);
        return mapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }


    public String resolvePath(String moduleId, String relativePath) {
        return requireIndex(moduleId).resolve(relativePath).toString();
    }

    public Map<String, Object> moduleDescriptor(String moduleId) {
        return requireIndex(moduleId).descriptor();
    }

    public List<String> assetRootPaths() {
        List<String> roots = new ArrayList<>();
        indexes.values().forEach(index -> index.assetRoots().forEach(root -> roots.add(index.resolve(String.valueOf(root)).toString())));
        return roots;
    }

    private ModuleIndex requireIndex(String moduleId) {
        ModuleIndex index = indexes.get(moduleId);
        if (index == null) {
            throw new IllegalArgumentException("Module index is not registered: " + moduleId);
        }
        return index;
    }

    private void loadIndexes() {
        loadIndex(projectRoot.resolve("core"));
        loadIndex(projectRoot.resolve("assets"));

        Path modulesRoot = projectRoot.resolve("modulesSrc");
        if (!Files.isDirectory(modulesRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.list(modulesRoot)) {
            stream.filter(Files::isDirectory)
                    .forEach(this::loadIndex);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan modulesSrc", e);
        }
    }

    private void loadIndex(Path moduleRoot) {
        Path indexPath = moduleRoot.resolve("module.index.json");
        if (!Files.isRegularFile(indexPath)) {
            return;
        }
        try {
            Map<String, Object> data = mapper.readValue(indexPath.toFile(), new TypeReference<>() { });
            Object id = data.get("id");
            if (id != null) {
                indexes.put(String.valueOf(id), new ModuleIndex(moduleRoot, data));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read module index: " + indexPath, e);
        }
    }

    private Path resolveProjectPath(String path) {
        Path candidate = Path.of(path);
        return candidate.isAbsolute() ? candidate : projectRoot.resolve(candidate).normalize();
    }

    private record ModuleIndex(Path root, Map<String, Object> data) {
        Path resolve(String relativePath) {
            Path path = Path.of(relativePath);
            return path.isAbsolute() ? path : root.resolve(path).normalize();
        }

        Map<String, Object> descriptor() {
            return Map.copyOf(data);
        }

        Map<String, Object> lua() {
            return map(data.get("lua"));
        }

        Map<String, Object> runtime() {
            return map(data.get("runtime"));
        }

        Map<String, Object> configs() {
            return map(runtime().get("configs"));
        }

        List<Object> assetRoots() {
            Object roots = runtime().get("assetRoots");
            if (roots instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return List.of();
        }

        private static Map<String, Object> map(Object value) {
            if (!(value instanceof Map<?, ?> source)) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
    }
}
