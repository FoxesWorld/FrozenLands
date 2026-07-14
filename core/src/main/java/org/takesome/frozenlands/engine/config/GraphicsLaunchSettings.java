package org.takesome.frozenlands.engine.config;

import com.jme3.system.AppSettings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical pre-launch graphics variables.
 *
 * <p>The launcher writes these values into {@link AppSettings}; runtime modules
 * read the same keys after the graphics context has been created. Values are
 * additionally mirrored to system properties for diagnostics, scripts and
 * external module integrations.</p>
 */
public final class GraphicsLaunchSettings {
    public static final String KEY_GRAPHICS_PRESET = "FrozenLands.Graphics.Preset";
    public static final String KEY_SHADOW_PRESET = "FrozenLands.Graphics.ShadowPreset";
    public static final String KEY_FXAA = "FrozenLands.Graphics.FXAA";
    public static final String KEY_SSAO = "FrozenLands.Graphics.SSAO";
    public static final String KEY_BLOOM = "FrozenLands.Graphics.Bloom";
    public static final String KEY_DEPTH_OF_FIELD = "FrozenLands.Graphics.DepthOfField";
    public static final String KEY_LIGHT_RAYS = "FrozenLands.Graphics.LightRays";
    public static final String KEY_SHADOWS = "FrozenLands.Graphics.Shadows";

    public static final String PROPERTY_GRAPHICS_PRESET = "frozenlands.graphics.preset";
    public static final String PROPERTY_SHADOW_PRESET = "frozenlands.graphics.shadowPreset";
    public static final String PROPERTY_MSAA = "frozenlands.graphics.msaa";
    public static final String PROPERTY_FXAA = "frozenlands.graphics.fxaa";
    public static final String PROPERTY_SSAO = "frozenlands.graphics.ssao";
    public static final String PROPERTY_BLOOM = "frozenlands.graphics.bloom";
    public static final String PROPERTY_DEPTH_OF_FIELD = "frozenlands.graphics.depthOfField";
    public static final String PROPERTY_LIGHT_RAYS = "frozenlands.graphics.lightRays";
    public static final String PROPERTY_SHADOWS = "frozenlands.graphics.shadows";

    private static final Set<String> GRAPHICS_PRESETS = Set.of("LOW", "MEDIUM", "HIGH", "ULTRA");
    private static final Set<String> SHADOW_PRESETS = Set.of(
            "OFF", "PERFORMANCE", "BALANCED", "QUALITY", "CINEMATIC"
    );

    private GraphicsLaunchSettings() {
    }

    public static Snapshot defaults() {
        return new Snapshot(
                "MEDIUM",
                "BALANCED",
                0,
                true,
                false,
                true,
                false,
                false,
                true
        );
    }

    public static Snapshot read(AppSettings settings) {
        Snapshot fallback = defaults();
        if (settings == null) {
            return fallback;
        }
        String graphicsPreset = normalizePreset(
                settings.getString(KEY_GRAPHICS_PRESET, fallback.graphicsPreset()),
                GRAPHICS_PRESETS,
                fallback.graphicsPreset()
        );
        String shadowPreset = normalizePreset(
                settings.getString(KEY_SHADOW_PRESET, fallback.shadowPreset()),
                SHADOW_PRESETS,
                fallback.shadowPreset()
        );
        return new Snapshot(
                graphicsPreset,
                shadowPreset,
                normalizeMsaa(settings.getSamples()),
                settings.getBoolean(KEY_FXAA, fallback.fxaa()),
                settings.getBoolean(KEY_SSAO, fallback.ssao()),
                settings.getBoolean(KEY_BLOOM, fallback.bloom()),
                settings.getBoolean(KEY_DEPTH_OF_FIELD, fallback.depthOfField()),
                settings.getBoolean(KEY_LIGHT_RAYS, fallback.lightRays()),
                settings.getBoolean(KEY_SHADOWS, fallback.shadows())
        );
    }

    public static void write(AppSettings settings, Snapshot snapshot) {
        if (settings == null) {
            throw new IllegalArgumentException("AppSettings must not be null");
        }
        Snapshot value = snapshot == null ? defaults() : snapshot.normalized();
        settings.putString(KEY_GRAPHICS_PRESET, value.graphicsPreset());
        settings.putString(KEY_SHADOW_PRESET, value.shadowPreset());
        settings.setSamples(value.msaaSamples());
        settings.putBoolean(KEY_FXAA, value.fxaa());
        settings.putBoolean(KEY_SSAO, value.ssao());
        settings.putBoolean(KEY_BLOOM, value.bloom());
        settings.putBoolean(KEY_DEPTH_OF_FIELD, value.depthOfField());
        settings.putBoolean(KEY_LIGHT_RAYS, value.lightRays());
        settings.putBoolean(KEY_SHADOWS, value.shadows());
    }

    public static void publishSystemProperties(AppSettings settings) {
        Snapshot value = read(settings);
        System.setProperty(PROPERTY_GRAPHICS_PRESET, value.graphicsPreset());
        System.setProperty(PROPERTY_SHADOW_PRESET, value.shadowPreset());
        System.setProperty(PROPERTY_MSAA, Integer.toString(value.msaaSamples()));
        System.setProperty(PROPERTY_FXAA, Boolean.toString(value.fxaa()));
        System.setProperty(PROPERTY_SSAO, Boolean.toString(value.ssao()));
        System.setProperty(PROPERTY_BLOOM, Boolean.toString(value.bloom()));
        System.setProperty(PROPERTY_DEPTH_OF_FIELD, Boolean.toString(value.depthOfField()));
        System.setProperty(PROPERTY_LIGHT_RAYS, Boolean.toString(value.lightRays()));
        System.setProperty(PROPERTY_SHADOWS, Boolean.toString(value.shadows()));
    }

    public static Map<String, Object> toMap(AppSettings settings) {
        Snapshot value = read(settings);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphicsPreset", value.graphicsPreset());
        result.put("shadowPreset", value.shadowPreset());
        result.put("msaaSamples", value.msaaSamples());
        result.put("fxaa", value.fxaa());
        result.put("ssao", value.ssao());
        result.put("bloom", value.bloom());
        result.put("depthOfField", value.depthOfField());
        result.put("lightRays", value.lightRays());
        result.put("shadows", value.shadows());
        return Map.copyOf(result);
    }

    private static String normalizePreset(
            String value,
            Set<String> allowed,
            String fallback
    ) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static int normalizeMsaa(int samples) {
        return switch (samples) {
            case 2, 4, 8, 16 -> samples;
            default -> 0;
        };
    }

    public record Snapshot(
            String graphicsPreset,
            String shadowPreset,
            int msaaSamples,
            boolean fxaa,
            boolean ssao,
            boolean bloom,
            boolean depthOfField,
            boolean lightRays,
            boolean shadows
    ) {
        public Snapshot normalized() {
            Snapshot fallback = defaults();
            String normalizedGraphics = normalizePreset(
                    graphicsPreset,
                    GRAPHICS_PRESETS,
                    fallback.graphicsPreset()
            );
            String normalizedShadows = normalizePreset(
                    shadowPreset,
                    SHADOW_PRESETS,
                    fallback.shadowPreset()
            );
            int normalizedSamples = normalizeMsaa(msaaSamples);
            boolean effectiveShadows = shadows && !"OFF".equals(normalizedShadows);
            return new Snapshot(
                    normalizedGraphics,
                    effectiveShadows ? normalizedShadows : "OFF",
                    normalizedSamples,
                    fxaa,
                    ssao,
                    bloom,
                    depthOfField,
                    lightRays,
                    effectiveShadows
            );
        }
    }
}
