package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Validated repository of named graphics profiles. */
public final class GraphicsQualityCatalog {
    public static final String MODULE_ID = "engine.quality";
    private static final List<GraphicsPreset> CONFIGURED_PRESETS = List.of(
            GraphicsPreset.LOW,
            GraphicsPreset.MEDIUM,
            GraphicsPreset.HIGH,
            GraphicsPreset.ULTRA
    );

    private final GraphicsQualityProfileParser parser = new GraphicsQualityProfileParser();
    private final EnumMap<GraphicsPreset, GraphicsQualityProfile> profiles =
            new EnumMap<>(GraphicsPreset.class);
    private final GraphicsPreset defaultPreset;

    public GraphicsQualityCatalog() {
        this(ModuleIndexCatalog.defaultCatalog().readConfigMap(MODULE_ID, "quality"));
    }

    public GraphicsQualityCatalog(Map<String, Object> config) {
        defaultPreset = GraphicsPreset.parse(
                parser.requiredString(config, "defaultPreset")
        );
        if (defaultPreset == GraphicsPreset.CUSTOM) {
            throw new IllegalArgumentException("Default graphics preset cannot be CUSTOM");
        }

        Map<String, Object> configured = parser.requiredMap(config, "presets");
        for (GraphicsPreset preset : CONFIGURED_PRESETS) {
            Map<String, Object> values = parser.requiredMap(
                    configured,
                    preset.name().toLowerCase()
            );
            profiles.put(preset, parser.parseRequired(preset, values));
        }
    }

    public GraphicsPreset defaultPreset() {
        return defaultPreset;
    }

    public GraphicsQualityProfile profile(GraphicsPreset preset) {
        if (preset == null || preset == GraphicsPreset.CUSTOM) {
            throw new IllegalArgumentException("A configured non-custom preset is required");
        }
        GraphicsQualityProfile result = profiles.get(preset);
        if (result == null) {
            throw new IllegalArgumentException("Graphics preset is not configured: " + preset);
        }
        return result;
    }

    public List<GraphicsPreset> presets() {
        return List.copyOf(profiles.keySet());
    }

    public GraphicsQualityProfile custom(
            GraphicsQualityProfile base,
            Map<String, Object> overrides
    ) {
        if (base == null) {
            throw new IllegalArgumentException("Custom quality profile requires a base profile");
        }
        return parser.custom(base, overrides);
    }
}
