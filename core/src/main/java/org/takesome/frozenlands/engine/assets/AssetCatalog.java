package org.takesome.frozenlands.engine.assets;

import com.jme3.material.Material;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class AssetCatalog {
    private final Logger logger;
    private final ConcurrentMap<String, AssetDescriptor> models = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AssetDescriptor> textures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AssetDescriptor> materials = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Material> materialPrototypes = new ConcurrentHashMap<>();

    AssetCatalog(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void registerModel(String id, String source, Map<String, ?> metadata) {
        register(models, descriptor(id, AssetLibrary.Type.MODEL, source, metadata));
    }

    void registerTexture(String id, String source, Map<String, ?> metadata) {
        register(textures, descriptor(id, AssetLibrary.Type.TEXTURE, source, metadata));
    }

    void registerMaterial(String id, Material prototype, Map<String, ?> metadata) {
        String key = AssetPaths.canonicalId(id);
        String displayId = AssetPaths.requireReference(id);
        Material safePrototype = Objects.requireNonNull(prototype, "prototype").clone();
        safePrototype.setName(displayId);
        materialPrototypes.put(key, safePrototype);
        materials.put(key, new AssetDescriptor(
                displayId,
                AssetLibrary.Type.MATERIAL,
                generatedSource(displayId),
                copyMetadata(metadata)
        ));
    }

    Material materialPrototype(String id) {
        Material prototype = materialPrototypes.get(AssetPaths.canonicalId(id));
        return prototype == null ? null : prototype.clone();
    }

    String resolve(AssetLibrary.Type type, String idOrPath) {
        String raw = AssetPaths.requireReference(idOrPath);
        AssetDescriptor descriptor = entries(type).get(AssetPaths.canonicalId(raw));
        if (descriptor != null) {
            return descriptor.source();
        }
        if (type == AssetLibrary.Type.MATERIAL && materialPrototypes.containsKey(AssetPaths.canonicalId(raw))) {
            return generatedSource(raw);
        }
        return AssetPaths.canonicalPath(raw);
    }

    boolean registered(AssetLibrary.Type type, String id) {
        String key = AssetPaths.canonicalId(id);
        if (type == AssetLibrary.Type.MATERIAL) {
            return materials.containsKey(key) || materialPrototypes.containsKey(key);
        }
        return entries(type).containsKey(key);
    }

    int count(AssetLibrary.Type type) {
        return type == AssetLibrary.Type.MATERIAL ? materialPrototypes.size() : entries(type).size();
    }

    List<Map<String, Object>> snapshot() {
        List<AssetDescriptor> descriptors = new ArrayList<>();
        descriptors.addAll(models.values());
        descriptors.addAll(textures.values());
        descriptors.addAll(materials.values());
        descriptors.sort(Comparator
                .comparing(AssetDescriptor::type)
                .thenComparing(AssetDescriptor::id));
        return descriptors.stream().map(AssetDescriptor::toMap).toList();
    }

    List<Map<String, Object>> snapshot(AssetLibrary.Type type) {
        return entries(type).values().stream()
                .sorted(Comparator.comparing(AssetDescriptor::id))
                .map(AssetDescriptor::toMap)
                .toList();
    }

    private AssetDescriptor descriptor(
            String id,
            AssetLibrary.Type type,
            String source,
            Map<String, ?> metadata
    ) {
        return new AssetDescriptor(
                AssetPaths.requireReference(id),
                type,
                AssetPaths.canonicalPath(source),
                copyMetadata(metadata)
        );
    }

    private void register(
            ConcurrentMap<String, AssetDescriptor> target,
            AssetDescriptor descriptor
    ) {
        String key = AssetPaths.canonicalId(descriptor.id());
        AssetDescriptor previous = target.put(key, descriptor);
        if (previous != null && !previous.source().equals(descriptor.source())) {
            logger.info(
                    "Asset alias replaced type={} id={} old={} new={}",
                    descriptor.type(),
                    descriptor.id(),
                    previous.source(),
                    descriptor.source()
            );
        }
    }

    private ConcurrentMap<String, AssetDescriptor> entries(AssetLibrary.Type type) {
        return switch (type) {
            case MODEL -> models;
            case TEXTURE -> textures;
            case MATERIAL -> materials;
        };
    }

    private Map<String, Object> copyMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        metadata.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return Map.copyOf(copy);
    }

    private String generatedSource(String id) {
        return "generated:" + id;
    }

    private record AssetDescriptor(
            String id,
            AssetLibrary.Type type,
            String source,
            Map<String, Object> metadata
    ) {
        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("type", type.name());
            result.put("source", source);
            result.put("metadata", metadata);
            return Map.copyOf(result);
        }
    }
}
