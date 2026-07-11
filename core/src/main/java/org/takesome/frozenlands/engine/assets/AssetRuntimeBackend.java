package org.takesome.frozenlands.engine.assets;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.MaterialKey;
import com.jme3.asset.ModelKey;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Internal native backend for loading assets and managing runtime cache keys. */
final class AssetRuntimeBackend {
    private final AssetManager backend;
    private final Logger logger;
    private final AssetUsageMetrics metrics;
    private final TextureMemoryTracker textureMemory = new TextureMemoryTracker();
    private final ConcurrentMap<String, Set<AssetKey<?>>> keysBySource =
            new ConcurrentHashMap<>();

    AssetRuntimeBackend(AssetManager backend, Logger logger, AssetUsageMetrics metrics) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    Spatial model(String reference, String source) {
        ModelKey key = new ModelKey(source);
        return load(
                AssetLibrary.Type.MODEL,
                reference,
                source,
                key,
                () -> backend.loadModel(key)
        );
    }

    Texture texture(
            String reference,
            String source,
            AssetLibrary.TextureOptions options
    ) {
        TextureKey key = new TextureKey(source, options.flipY());
        key.setGenerateMips(options.generateMips());
        key.setAnisotropy(options.anisotropy());
        Texture nativeTexture = load(
                AssetLibrary.Type.TEXTURE,
                reference,
                source,
                key,
                () -> backend.loadTexture(key)
        );
        textureMemory.track(source, nativeTexture);

        Texture result = nativeTexture.clone();
        applyTextureOptions(result, options);
        return result;
    }

    Material material(String reference, String source) {
        MaterialKey key = new MaterialKey(source);
        Material loaded = load(
                AssetLibrary.Type.MATERIAL,
                reference,
                source,
                key,
                () -> backend.loadMaterial(source)
        );
        return loaded.clone();
    }

    Material createMaterial(String definitionPath) {
        long started = System.nanoTime();
        try {
            Material material = new Material(backend, definitionPath);
            metrics.success(
                    AssetLibrary.Type.MATERIAL,
                    definitionPath,
                    definitionPath,
                    System.nanoTime() - started
            );
            return material;
        } catch (RuntimeException exception) {
            metrics.failure(
                    AssetLibrary.Type.MATERIAL,
                    definitionPath,
                    definitionPath,
                    System.nanoTime() - started,
                    exception
            );
            throw new IllegalStateException(
                    "Failed to create material from definition: " + definitionPath,
                    exception
            );
        }
    }

    boolean exists(AssetLibrary.Type type, String source) {
        if (source.startsWith("generated:")) {
            return true;
        }
        AssetInfo info = backend.locateAsset(key(type, source));
        return info != null;
    }

    int evict(String source) {
        Set<AssetKey<?>> keys = keysBySource.remove(source);
        int removed = 0;
        if (keys != null) {
            for (AssetKey<?> key : keys) {
                if (deleteFromCache(key)) {
                    removed++;
                }
            }
        }
        textureMemory.release(source);
        return removed;
    }

    void clearCache() {
        backend.clearCache();
        keysBySource.clear();
        textureMemory.clear();
        logger.info("FrozenLands asset runtime cache cleared");
    }

    int trackedSourceCount() {
        return keysBySource.size();
    }

    int trackedTextureImageCount() {
        return textureMemory.imageCount();
    }

    long textureMemoryEstimateBytes() {
        return textureMemory.estimatedBytes();
    }

    private AssetKey<?> key(AssetLibrary.Type type, String source) {
        return switch (type) {
            case MODEL -> new ModelKey(source);
            case TEXTURE -> new TextureKey(source);
            case MATERIAL -> new MaterialKey(source);
        };
    }

    private <T> T load(
            AssetLibrary.Type type,
            String reference,
            String source,
            AssetKey<?> key,
            Supplier<T> loader
    ) {
        long started = System.nanoTime();
        try {
            T value = loader.get();
            if (value == null) {
                throw new IllegalStateException("Asset backend returned null");
            }
            keysBySource
                    .computeIfAbsent(source, ignored -> ConcurrentHashMap.newKeySet())
                    .add(key);
            metrics.success(type, reference, source, System.nanoTime() - started);
            return value;
        } catch (RuntimeException exception) {
            metrics.failure(type, reference, source, System.nanoTime() - started, exception);
            throw new IllegalStateException(
                    "Failed to load asset type=" + type
                            + " reference=" + reference
                            + " source=" + source,
                    exception
            );
        }
    }

    private void applyTextureOptions(
            Texture texture,
            AssetLibrary.TextureOptions options
    ) {
        if (options.wrapMode() != null) {
            texture.setWrap(options.wrapMode());
        }
        if (options.minFilter() != null) {
            texture.setMinFilter(options.minFilter());
        }
        if (options.magFilter() != null) {
            texture.setMagFilter(options.magFilter());
        }
        if (options.anisotropy() > 0) {
            texture.setAnisotropicFilter(options.anisotropy());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean deleteFromCache(AssetKey<?> key) {
        return backend.deleteFromCache((AssetKey) key);
    }
}
