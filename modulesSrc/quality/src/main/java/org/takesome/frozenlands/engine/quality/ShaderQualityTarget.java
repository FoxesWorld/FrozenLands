package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.shaders.Shaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ShaderQualityTarget implements GraphicsQualityTarget {
    private final Shaders shaders;

    ShaderQualityTarget(Shaders shaders) {
        this.shaders = Objects.requireNonNull(shaders, "shaders");
    }

    @Override
    public String id() {
        return "shaders";
    }

    @Override
    public Map<String, Object> apply(GraphicsQualityProfile profile) {
        GraphicsQualityProfile.Shadows shadows = profile.shadows();
        shaders.applyShadowSettings(Map.of(
                "shadowMapSize", shadows.mapSize(),
                "splits", shadows.splits(),
                "shadowZExtend", shadows.distance(),
                "shadowIntensity", shadows.intensity(),
                "edgesThickness", shadows.edgesThickness()
        ));

        GraphicsQualityProfile.PostProcessing post = profile.postProcessing();
        Map<String, Boolean> effects = new LinkedHashMap<>();
        effects.put(Shaders.EFFECT_BLOOM,
                shaders.setEffectEnabled(Shaders.EFFECT_BLOOM, post.bloom()));
        effects.put(Shaders.EFFECT_FXAA,
                shaders.setEffectEnabled(Shaders.EFFECT_FXAA, post.fxaa()));
        effects.put(Shaders.EFFECT_DOF,
                shaders.setEffectEnabled(Shaders.EFFECT_DOF, post.depthOfField()));
        effects.put(Shaders.EFFECT_LIGHT_SCATTERING,
                shaders.setEffectEnabled(
                        Shaders.EFFECT_LIGHT_SCATTERING,
                        post.lightRays()
                ));
        effects.put(Shaders.EFFECT_SHADOWS,
                shaders.setEffectEnabled(Shaders.EFFECT_SHADOWS, true));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shadowSettings", shaders.getShadowSettings().toMap());
        result.put("effectsApplied", Map.copyOf(effects));
        return Map.copyOf(result);
    }

    @Override
    public Map<String, Object> status() {
        return shaders.status();
    }
}
