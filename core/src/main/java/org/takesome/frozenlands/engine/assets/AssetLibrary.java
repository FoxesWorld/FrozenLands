package org.takesome.frozenlands.engine.assets;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FrozenLands-owned asset facade.
 *
 * <p>Game code addresses assets by stable IDs or portable virtual paths. The
 * JME asset manager is an internal format/backend implementation and is not part
 * of the gameplay-facing contract.</p>
 */
public final class AssetLibrary {
    public enum Type {
        MODEL,
        TEXTURE,
        MATERIAL
    }

    private final AssetCatalog catalog;
    private final AssetUsageMetrics metrics;
    private final AssetRuntimeBackend runtime;
    private final TextureQualityPolicy textureQuality = new TextureQualityPolicy();

    public AssetLibrary(AssetManager backend, Logger logger) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(logger, "logger");
        this.catalog = new AssetCatalog(logger);
        this.metrics = new AssetUsageMetrics();
        this.runtime = new AssetRuntimeBackend(backend, logger, metrics);
    }

    public void registerModel(String id, String source) {
        registerModel(id, source, Map.of());
    }

    public void registerModel(String id, String source, Map<String, ?> metadata) {
        catalog.registerModel(id, source, metadata);
    }

    public void registerTexture(String id, String source) {
        registerTexture(id, source, Map.of());
    }

    public void registerTexture(String id, String source, Map<String, ?> metadata) {
        catalog.registerTexture(id, source, metadata);
    }

    public void registerMaterial(String id, Material prototype) {
        registerMaterial(id, prototype, Map.of());
    }

    public void registerMaterial(String id, Material prototype, Map<String, ?> metadata) {
        catalog.registerMaterial(id, prototype, metadata);
    }

    public Spatial model(String idOrPath) {
        String source = resolve(Type.MODEL, idOrPath);
        return runtime.model(idOrPath, source);
    }

    public Texture texture(String idOrPath) {
        return texture(idOrPath, TextureProfile.WORLD);
    }

    public Texture texture(String idOrPath, TextureProfile profile) {
        return texture(idOrPath, textureQuality.options(profile));
    }

    public Texture texture(String idOrPath, Texture.WrapMode wrapMode) {
        return texture(
                idOrPath,
                textureQuality.options(TextureProfile.WORLD).withWrap(wrapMode)
        );
    }

    public Texture texture(String idOrPath, TextureOptions options) {
        TextureOptions safeOptions = options == null ? TextureOptions.defaults() : options;
        String source = resolve(Type.TEXTURE, idOrPath);
        return runtime.texture(idOrPath, source, safeOptions);
    }

    public Material material(String idOrPath) {
        Material prototype = catalog.materialPrototype(idOrPath);
        if (prototype != null) {
            metrics.success(Type.MATERIAL, idOrPath, "generated:" + idOrPath, 0L);
            return prototype;
        }
        String source = resolve(Type.MATERIAL, idOrPath);
        return runtime.material(idOrPath, source);
    }

    public Material createMaterial(String definitionPath) {
        return runtime.createMaterial(canonicalPath(definitionPath));
    }

    public String resolve(Type type, String idOrPath) {
        return catalog.resolve(Objects.requireNonNull(type, "type"), idOrPath);
    }

    public boolean registered(Type type, String id) {
        return catalog.registered(Objects.requireNonNull(type, "type"), id);
    }

    public boolean exists(Type type, String idOrPath) {
        Type safeType = Objects.requireNonNull(type, "type");
        String source = resolve(safeType, idOrPath);
        if (source.startsWith("generated:")) {
            return registered(safeType, idOrPath);
        }
        return runtime.exists(safeType, source);
    }

    public void preload(Type type, String idOrPath) {
        switch (Objects.requireNonNull(type, "type")) {
            case MODEL -> model(idOrPath);
            case TEXTURE -> texture(idOrPath);
            case MATERIAL -> material(idOrPath);
        }
    }

    public List<Map<String, Object>> catalog() {
        return catalog.snapshot();
    }

    public List<Map<String, Object>> catalog(Type type) {
        return catalog.snapshot(Objects.requireNonNull(type, "type"));
    }

    public int evict(Type type, String idOrPath) {
        return runtime.evict(resolve(Objects.requireNonNull(type, "type"), idOrPath));
    }

    public void clearRuntimeCache() {
        runtime.clearCache();
    }

    public void configureTextureQuality(boolean mipmaps, int anisotropy) {
        textureQuality.configure(mipmaps, anisotropy);
    }

    public Map<String, Object> textureQualityStatus() {
        return textureQuality.status();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", catalog.count(Type.MODEL));
        result.put("textures", catalog.count(Type.TEXTURE));
        result.put("materials", catalog.count(Type.MATERIAL));
        result.put("successfulLoads", metrics.successfulLoads());
        result.put("failedLoads", metrics.failedLoads());
        result.put("trackedNativeSources", runtime.trackedSourceCount());
        result.put("trackedTextureImages", runtime.trackedTextureImageCount());
        result.put("textureMemoryEstimateBytes", runtime.textureMemoryEstimateBytes());
        result.put("usage", metrics.snapshot());
        return result;
    }

    public List<Map<String, Object>> usageSnapshot() {
        return metrics.snapshot();
    }

    public String canonicalPath(String path) {
        return AssetPaths.canonicalPath(path);
    }

    public record TextureOptions(
            boolean flipY,
            boolean generateMips,
            int anisotropy,
            Texture.WrapMode wrapMode,
            Texture.MinFilter minFilter,
            Texture.MagFilter magFilter
    ) {
        public TextureOptions {
            anisotropy = Math.max(0, anisotropy);
        }

        public static TextureOptions defaults() {
            return new TextureOptions(true, false, 0, null, null, null);
        }

        public TextureOptions withWrap(Texture.WrapMode value) {
            return new TextureOptions(
                    flipY,
                    generateMips,
                    anisotropy,
                    value,
                    minFilter,
                    magFilter
            );
        }

        public TextureOptions withMipmaps(boolean value) {
            return new TextureOptions(
                    flipY,
                    value,
                    anisotropy,
                    wrapMode,
                    minFilter,
                    magFilter
            );
        }

        public TextureOptions withAnisotropy(int value) {
            return new TextureOptions(
                    flipY,
                    generateMips,
                    value,
                    wrapMode,
                    minFilter,
                    magFilter
            );
        }

        public TextureOptions withFilters(Texture.MinFilter min, Texture.MagFilter mag) {
            return new TextureOptions(
                    flipY,
                    generateMips,
                    anisotropy,
                    wrapMode,
                    min,
                    mag
            );
        }
    }
}
