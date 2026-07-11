package org.takesome.frozenlands.engine.assets;

import com.fasterxml.jackson.databind.JsonNode;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads model and texture aliases from mutable FrozenLands JSON catalogues. */
public final class AssetCatalogLoader {
    private final AssetLibrary assets;
    private final ModuleIndexCatalog moduleIndex;

    public AssetCatalogLoader(AssetLibrary assets) {
        this(assets, ModuleIndexCatalog.defaultCatalog());
    }

    public AssetCatalogLoader(AssetLibrary assets, ModuleIndexCatalog moduleIndex) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.moduleIndex = Objects.requireNonNull(moduleIndex, "moduleIndex");
    }

    public LoadResult load(
            String path,
            Map<String, AssetLibrary.Type> sections,
            Map<String, ?> commonMetadata
    ) {
        JsonNode root = moduleIndex.readJsonNode(path);
        int models = 0;
        int textures = 0;
        for (Map.Entry<String, AssetLibrary.Type> section : sections.entrySet()) {
            int loaded = loadSection(root.path(section.getKey()), section.getValue(), commonMetadata);
            switch (section.getValue()) {
                case MODEL -> models += loaded;
                case TEXTURE -> textures += loaded;
                case MATERIAL -> throw new IllegalArgumentException(
                        "Material prototypes must be registered by MaterialProvider"
                );
            }
        }
        return new LoadResult(models, textures);
    }

    public int loadSection(
            JsonNode entries,
            AssetLibrary.Type type,
            Map<String, ?> commonMetadata
    ) {
        if (entries.isMissingNode() || entries.isNull()) {
            return 0;
        }
        if (!entries.isArray()) {
            throw new IllegalArgumentException("Asset catalog section must be an array: " + type);
        }

        int count = 0;
        for (JsonNode entry : entries) {
            String id = requiredText(entry, "id");
            String source = requiredText(entry, "path");
            Map<String, Object> metadata = metadata(entry, commonMetadata);
            register(type, id, source, metadata);
            count++;
        }
        return count;
    }

    private void register(
            AssetLibrary.Type type,
            String id,
            String source,
            Map<String, Object> metadata
    ) {
        switch (type) {
            case MODEL -> assets.registerModel(id, source, metadata);
            case TEXTURE -> assets.registerTexture(id, source, metadata);
            case MATERIAL -> throw new IllegalArgumentException(
                    "Material prototypes must be registered by MaterialProvider"
            );
        }
    }

    private Map<String, Object> metadata(JsonNode entry, Map<String, ?> commonMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (commonMetadata != null) {
            commonMetadata.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        entry.properties().forEach(field -> {
            String name = field.getKey();
            if (!"id".equals(name) && !"path".equals(name)) {
                metadata.put(name, value(field.getValue()));
            }
        });
        return metadata;
    }

    private Object value(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            node.forEach(value -> result.add(value(value)));
            return List.copyOf(result);
        }
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            node.properties().forEach(field -> result.put(field.getKey(), value(field.getValue())));
            return Map.copyOf(result);
        }
        return null;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Required asset catalog field is missing: " + field);
        }
        return value;
    }

    public record LoadResult(int models, int textures) {
        public int total() {
            return models + textures;
        }
    }
}
