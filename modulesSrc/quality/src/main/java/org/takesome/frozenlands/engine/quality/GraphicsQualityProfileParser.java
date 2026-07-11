package org.takesome.frozenlands.engine.quality;

import java.util.Map;

final class GraphicsQualityProfileParser {
    GraphicsQualityProfile parseRequired(
            GraphicsPreset preset,
            Map<String, Object> values
    ) {
        Map<String, Object> textures = requiredMap(values, "textures");
        Map<String, Object> shadows = requiredMap(values, "shadows");
        Map<String, Object> terrain = requiredMap(values, "terrain");
        Map<String, Object> models = requiredMap(values, "models");
        Map<String, Object> particles = requiredMap(values, "particles");
        Map<String, Object> weather = requiredMap(values, "weather");
        Map<String, Object> post = requiredMap(values, "postProcessing");
        Map<String, Object> sky = requiredMap(values, "sky");
        Map<String, Object> ui = requiredMap(values, "ui");

        return new GraphicsQualityProfile(
                preset,
                new GraphicsQualityProfile.Textures(
                        requiredBoolean(textures, "mipmaps"),
                        requiredInt(textures, "anisotropy")
                ),
                new GraphicsQualityProfile.Shadows(
                        requiredInt(shadows, "mapSize"),
                        requiredInt(shadows, "splits"),
                        requiredFloat(shadows, "distance"),
                        requiredFloat(shadows, "intensity"),
                        requiredInt(shadows, "edgesThickness")
                ),
                new GraphicsQualityProfile.Terrain(
                        requiredFloat(terrain, "viewDistance"),
                        requiredInt(terrain, "chunkRadius"),
                        requiredFloat(terrain, "lodBias")
                ),
                new GraphicsQualityProfile.Models(
                        requiredFloat(models, "lodBias"),
                        requiredFloat(models, "maximumDistance"),
                        requiredString(models, "shadowMode")
                ),
                new GraphicsQualityProfile.Particles(
                        requiredFloat(particles, "densityMultiplier"),
                        requiredFloat(particles, "emissionDistance")
                ),
                new GraphicsQualityProfile.Weather(
                        requiredFloat(weather, "snowDensityMultiplier"),
                        requiredBoolean(weather, "visibilityEffects")
                ),
                new GraphicsQualityProfile.PostProcessing(
                        requiredBoolean(post, "bloom"),
                        requiredBoolean(post, "fxaa"),
                        requiredBoolean(post, "depthOfField"),
                        requiredBoolean(post, "lightRays")
                ),
                new GraphicsQualityProfile.Sky(
                        requiredFloat(sky, "cloudQuality"),
                        requiredFloat(sky, "updateIntervalSeconds")
                ),
                new GraphicsQualityProfile.Ui(
                        requiredFloat(ui, "renderScale"),
                        requiredBoolean(ui, "animations")
                )
        );
    }

    GraphicsQualityProfile custom(
            GraphicsQualityProfile base,
            Map<String, Object> overrides
    ) {
        Map<String, Object> safe = overrides == null ? Map.of() : overrides;
        Map<String, Object> textures = optionalMap(safe, "textures");
        Map<String, Object> shadows = optionalMap(safe, "shadows");
        Map<String, Object> terrain = optionalMap(safe, "terrain");
        Map<String, Object> models = optionalMap(safe, "models");
        Map<String, Object> particles = optionalMap(safe, "particles");
        Map<String, Object> weather = optionalMap(safe, "weather");
        Map<String, Object> post = optionalMap(safe, "postProcessing");
        Map<String, Object> sky = optionalMap(safe, "sky");
        Map<String, Object> ui = optionalMap(safe, "ui");

        return new GraphicsQualityProfile(
                GraphicsPreset.CUSTOM,
                new GraphicsQualityProfile.Textures(
                        bool(textures, "mipmaps", base.textures().mipmaps()),
                        integer(textures, "anisotropy", base.textures().anisotropy())
                ),
                new GraphicsQualityProfile.Shadows(
                        integer(shadows, "mapSize", base.shadows().mapSize()),
                        integer(shadows, "splits", base.shadows().splits()),
                        floating(shadows, "distance", base.shadows().distance()),
                        floating(shadows, "intensity", base.shadows().intensity()),
                        integer(shadows, "edgesThickness", base.shadows().edgesThickness())
                ),
                new GraphicsQualityProfile.Terrain(
                        floating(terrain, "viewDistance", base.terrain().viewDistance()),
                        integer(terrain, "chunkRadius", base.terrain().chunkRadius()),
                        floating(terrain, "lodBias", base.terrain().lodBias())
                ),
                new GraphicsQualityProfile.Models(
                        floating(models, "lodBias", base.models().lodBias()),
                        floating(models, "maximumDistance", base.models().maximumDistance()),
                        string(models, "shadowMode", base.models().shadowMode())
                ),
                new GraphicsQualityProfile.Particles(
                        floating(particles, "densityMultiplier", base.particles().densityMultiplier()),
                        floating(particles, "emissionDistance", base.particles().emissionDistance())
                ),
                new GraphicsQualityProfile.Weather(
                        floating(weather, "snowDensityMultiplier", base.weather().snowDensityMultiplier()),
                        bool(weather, "visibilityEffects", base.weather().visibilityEffects())
                ),
                new GraphicsQualityProfile.PostProcessing(
                        bool(post, "bloom", base.postProcessing().bloom()),
                        bool(post, "fxaa", base.postProcessing().fxaa()),
                        bool(post, "depthOfField", base.postProcessing().depthOfField()),
                        bool(post, "lightRays", base.postProcessing().lightRays())
                ),
                new GraphicsQualityProfile.Sky(
                        floating(sky, "cloudQuality", base.sky().cloudQuality()),
                        floating(sky, "updateIntervalSeconds", base.sky().updateIntervalSeconds())
                ),
                new GraphicsQualityProfile.Ui(
                        floating(ui, "renderScale", base.ui().renderScale()),
                        bool(ui, "animations", base.ui().animations())
                )
        );
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> requiredMap(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Required quality section is missing: " + key);
        }
        return (Map<String, Object>) map;
    }

    String requiredString(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Required quality value is missing: " + key);
        }
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> optionalMap(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int requiredInt(Map<String, Object> values, String key) {
        Object value = required(values, key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private float requiredFloat(Map<String, Object> values, String key) {
        Object value = required(values, key);
        return value instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(value));
    }

    private boolean requiredBoolean(Map<String, Object> values, String key) {
        Object value = required(values, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Object required(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required quality value is missing: " + key);
        }
        return value;
    }

    private int integer(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private float floating(Map<String, Object> values, String key, float fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.floatValue() : value == null ? fallback : Float.parseFloat(String.valueOf(value));
    }

    private boolean bool(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String string(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }
}
