package org.takesome.frozenlands.engine.quality;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record GraphicsQualityProfile(
        GraphicsPreset preset,
        Textures textures,
        Shadows shadows,
        Terrain terrain,
        Models models,
        Particles particles,
        Weather weather,
        PostProcessing postProcessing,
        Sky sky,
        Ui ui
) {
    public GraphicsQualityProfile {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(textures, "textures");
        Objects.requireNonNull(shadows, "shadows");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(particles, "particles");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(postProcessing, "postProcessing");
        Objects.requireNonNull(sky, "sky");
        Objects.requireNonNull(ui, "ui");
    }

    public GraphicsQualityProfile withPreset(GraphicsPreset value) {
        return new GraphicsQualityProfile(
                value,
                textures,
                shadows,
                terrain,
                models,
                particles,
                weather,
                postProcessing,
                sky,
                ui
        );
    }

    public Map<String, Object> toMap() {
        return immutableMap(
                "preset", preset.name(),
                "textures", textures.toMap(),
                "shadows", shadows.toMap(),
                "terrain", terrain.toMap(),
                "models", models.toMap(),
                "particles", particles.toMap(),
                "weather", weather.toMap(),
                "postProcessing", postProcessing.toMap(),
                "sky", sky.toMap(),
                "ui", ui.toMap()
        );
    }

    public record Textures(boolean mipmaps, int anisotropy) {
        public Textures {
            range(anisotropy, 0, 16, "textures.anisotropy");
        }

        public Map<String, Object> toMap() {
            return immutableMap("mipmaps", mipmaps, "anisotropy", anisotropy);
        }
    }

    public record Shadows(
            int mapSize,
            int splits,
            float distance,
            float intensity,
            int edgesThickness
    ) {
        public Shadows {
            if (mapSize < 512 || mapSize > 8192 || (mapSize & (mapSize - 1)) != 0) {
                throw new IllegalArgumentException(
                        "shadows.mapSize must be a power of two between 512 and 8192: " + mapSize
                );
            }
            range(splits, 1, 4, "shadows.splits");
            positive(distance, "shadows.distance");
            unit(intensity, "shadows.intensity");
            range(edgesThickness, 1, 10, "shadows.edgesThickness");
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "mapSize", mapSize,
                    "splits", splits,
                    "distance", distance,
                    "intensity", intensity,
                    "edgesThickness", edgesThickness
            );
        }
    }

    public record Terrain(float viewDistance, int chunkRadius, float lodBias) {
        public Terrain {
            positive(viewDistance, "terrain.viewDistance");
            range(chunkRadius, 1, 16, "terrain.chunkRadius");
            positive(lodBias, "terrain.lodBias");
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "viewDistance", viewDistance,
                    "chunkRadius", chunkRadius,
                    "lodBias", lodBias
            );
        }
    }

    public record Models(float lodBias, float maximumDistance, String shadowMode) {
        public Models {
            positive(lodBias, "models.lodBias");
            positive(maximumDistance, "models.maximumDistance");
            if (shadowMode == null || shadowMode.isBlank()) {
                throw new IllegalArgumentException("models.shadowMode must not be blank");
            }
            shadowMode = shadowMode.trim().toUpperCase();
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "lodBias", lodBias,
                    "maximumDistance", maximumDistance,
                    "shadowMode", shadowMode
            );
        }
    }

    public record Particles(float densityMultiplier, float emissionDistance) {
        public Particles {
            bounded(densityMultiplier, 0.1f, 2f, "particles.densityMultiplier");
            positive(emissionDistance, "particles.emissionDistance");
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "densityMultiplier", densityMultiplier,
                    "emissionDistance", emissionDistance
            );
        }
    }

    public record Weather(float snowDensityMultiplier, boolean visibilityEffects) {
        public Weather {
            bounded(snowDensityMultiplier, 0.1f, 2f, "weather.snowDensityMultiplier");
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "snowDensityMultiplier", snowDensityMultiplier,
                    "visibilityEffects", visibilityEffects
            );
        }
    }

    public record PostProcessing(
            boolean bloom,
            boolean fxaa,
            boolean depthOfField,
            boolean lightRays
    ) {
        public Map<String, Object> toMap() {
            return immutableMap(
                    "bloom", bloom,
                    "fxaa", fxaa,
                    "depthOfField", depthOfField,
                    "lightRays", lightRays
            );
        }
    }

    public record Sky(float cloudQuality, float updateIntervalSeconds) {
        public Sky {
            unit(cloudQuality, "sky.cloudQuality");
            positive(updateIntervalSeconds, "sky.updateIntervalSeconds");
        }

        public Map<String, Object> toMap() {
            return immutableMap(
                    "cloudQuality", cloudQuality,
                    "updateIntervalSeconds", updateIntervalSeconds
            );
        }
    }

    public record Ui(float renderScale, boolean animations) {
        public Ui {
            bounded(renderScale, 0.5f, 2f, "ui.renderScale");
        }

        public Map<String, Object> toMap() {
            return immutableMap("renderScale", renderScale, "animations", animations);
        }
    }

    private static Map<String, Object> immutableMap(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must contain key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return Map.copyOf(result);
    }

    private static void positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
    }

    private static void unit(float value, String name) {
        bounded(value, 0f, 1f, name);
    }

    private static void bounded(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be between " + min + " and " + max + ": " + value
            );
        }
    }

    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be between " + min + " and " + max + ": " + value
            );
        }
    }
}
