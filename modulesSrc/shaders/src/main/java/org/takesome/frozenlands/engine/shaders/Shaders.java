package org.takesome.frozenlands.engine.shaders;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowFilter;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.world.sky.Sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Shaders extends BaseAppState {
    public static final String EFFECT_BLOOM = "bloom";
    public static final String EFFECT_LIGHT_SCATTERING = "lightScattering";
    public static final String EFFECT_DOF = "dof";
    public static final String EFFECT_SHADOWS = "shadows";
    public static final String EFFECT_FXAA = "fxaa";

    private final FilterPostProcessor fpp;
    private final EngineContext context;
    private final AssetManager assetManager;
    private final ViewPort viewPort;
    private final ShaderRuntimeSettings runtimeSettings = new ShaderRuntimeSettings();
    private final ShadowSettings shadowSettings = new ShadowSettings();
    private final Map<String, Filter> filters = new LinkedHashMap<>();
    private final Map<String, Boolean> desiredEnabled = new LinkedHashMap<>();

    private LightScatteringFilter lightScatteringFilter;
    private BloomFilter bloomFilter;
    private DepthOfFieldFilter dofFilter;
    private DirectionalLightShadowFilter shadowFilter;
    private FXAAFilter fxaaFilter;
    private boolean processorAttached;

    public Shaders(EngineContext context) {
        this.context = context;
        this.fpp = context.getFpp();
        this.assetManager = context.getAssetManager();
        this.viewPort = context.getViewPort();
        initializeDesiredState();
        setEnabled(runtimeSettings.pipelineEnabled());
    }

    @Override
    protected void initialize(Application application) {
        buildPipeline();
        if (isEnabled()) {
            attachProcessorIfNeeded();
        }
        publish(EngineEventTopics.SHADER_PIPELINE_INITIALIZED, status());
    }

    @Override
    protected void cleanup(Application application) {
        detachProcessorIfNeeded();
        filters.clear();
    }

    @Override
    protected void onEnable() {
        attachProcessorIfNeeded();
        publish(EngineEventTopics.SHADER_PIPELINE_ENABLED_CHANGED, Map.of("enabled", true));
    }

    @Override
    protected void onDisable() {
        detachProcessorIfNeeded();
        publish(EngineEventTopics.SHADER_PIPELINE_ENABLED_CHANGED, Map.of("enabled", false));
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("initialized", isInitialized());
        result.put("enabled", isEnabled());
        result.put("processorAttached", processorAttached);
        result.put("effects", effectStatuses());
        result.put("shadowSettings", shadowSettings.toMap());
        return result;
    }

    public List<Map<String, Object>> effectStatuses() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String effectId : filters.keySet()) {
            result.add(effectStatus(effectId));
        }
        return result;
    }

    public Map<String, Object> effectStatus(String effectId) {
        String normalized = normalizeEffectId(effectId);
        Filter filter = filters.get(normalized);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", normalized);
        result.put("available", filter != null);
        result.put("enabled", filter != null && filter.isEnabled());
        result.put("desiredEnabled", desiredEnabled.getOrDefault(normalized, true));
        result.put("filter", filter == null ? null : filter.getClass().getSimpleName());
        return result;
    }

    public boolean setEffectEnabled(String effectId, boolean enabled) {
        String normalized = normalizeEffectId(effectId);
        desiredEnabled.put(normalized, enabled);
        Filter filter = filters.get(normalized);
        if (filter == null) {
            return false;
        }
        filter.setEnabled(enabled);
        publish(EngineEventTopics.SHADER_EFFECT_ENABLED_CHANGED, Map.of(
                "id", normalized,
                "enabled", enabled,
                "filter", filter.getClass().getSimpleName()
        ));
        return true;
    }

    public ShadowSettings applyShadowSettings(Map<String, Object> values) {
        int oldShadowMapSize = shadowSettings.getShadowMapSize();
        int oldSplits = shadowSettings.getSplits();
        shadowSettings.update(values);
        boolean rebuildRequired = shadowFilter != null
                && (oldShadowMapSize != shadowSettings.getShadowMapSize()
                || oldSplits != shadowSettings.getSplits());
        if (rebuildRequired) {
            rebuildShadowFilter();
        } else {
            applyShadowFilterSettings();
        }
        publish(EngineEventTopics.SHADER_SETTINGS_CHANGED, Map.of(
                "type", "shadows",
                "settings", shadowSettings.toMap(),
                "rebuildRequired", rebuildRequired
        ));
        return shadowSettings;
    }

    public LightScatteringFilter getLsf() { return lightScatteringFilter; }
    public BloomFilter getBloom() { return bloomFilter; }
    public DepthOfFieldFilter getDof() { return dofFilter; }
    public DirectionalLightShadowFilter getShadowFilter() { return shadowFilter; }
    public FXAAFilter getFxaaFilter() { return fxaaFilter; }
    public ShadowSettings getShadowSettings() { return shadowSettings; }

    private void initializeDesiredState() {
        desiredEnabled.put(EFFECT_BLOOM, runtimeSettings.effectEnabled(EFFECT_BLOOM, true));
        desiredEnabled.put(EFFECT_LIGHT_SCATTERING, runtimeSettings.effectEnabled(EFFECT_LIGHT_SCATTERING, true));
        desiredEnabled.put(EFFECT_DOF, runtimeSettings.effectEnabled(EFFECT_DOF, true));
        desiredEnabled.put(EFFECT_SHADOWS, runtimeSettings.effectEnabled(EFFECT_SHADOWS, true));
        desiredEnabled.put(EFFECT_FXAA, runtimeSettings.effectEnabled(EFFECT_FXAA, true));
    }

    private void buildPipeline() {
        filters.clear();
        bloomFilter = new BloomFilter();
        bloomFilter.setBloomIntensity(runtimeSettings.bloomIntensity());
        bloomFilter.setExposurePower(runtimeSettings.bloomExposurePower());
        register(EFFECT_BLOOM, bloomFilter);

        lightScatteringFilter = new LightScatteringFilter(runtimeSettings.lightDirection());
        lightScatteringFilter.setLightDensity(runtimeSettings.lightDensity());
        register(EFFECT_LIGHT_SCATTERING, lightScatteringFilter);

        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(runtimeSettings.focusDistance());
        dofFilter.setFocusRange(runtimeSettings.focusRange());
        register(EFFECT_DOF, dofFilter);

        shadowFilter = new DirectionalLightShadowFilter(assetManager,
                shadowSettings.getShadowMapSize(), shadowSettings.getSplits());
        shadowFilter.setLight(context.requireService(Sky.class).getSun());
        applyShadowFilterSettings();
        register(EFFECT_SHADOWS, shadowFilter);

        fxaaFilter = new FXAAFilter();
        register(EFFECT_FXAA, fxaaFilter);
    }

    private void register(String id, Filter filter) {
        boolean enabled = desiredEnabled.getOrDefault(id, true);
        filter.setEnabled(enabled);
        filters.put(id, filter);
        fpp.addFilter(filter);
    }

    private void attachProcessorIfNeeded() {
        if (!isInitialized() || processorAttached) {
            return;
        }
        viewPort.addProcessor(fpp);
        processorAttached = true;
    }

    private void detachProcessorIfNeeded() {
        if (!processorAttached) {
            return;
        }
        viewPort.removeProcessor(fpp);
        processorAttached = false;
    }

    private void rebuildShadowFilter() {
        boolean enabled = desiredEnabled.getOrDefault(EFFECT_SHADOWS, true);
        if (fxaaFilter != null) {
            fpp.removeFilter(fxaaFilter);
        }
        if (shadowFilter != null) {
            fpp.removeFilter(shadowFilter);
        }
        shadowFilter = new DirectionalLightShadowFilter(
                assetManager,
                shadowSettings.getShadowMapSize(),
                shadowSettings.getSplits()
        );
        shadowFilter.setLight(context.requireService(Sky.class).getSun());
        shadowFilter.setEnabled(enabled);
        applyShadowFilterSettings();
        filters.put(EFFECT_SHADOWS, shadowFilter);
        fpp.addFilter(shadowFilter);
        if (fxaaFilter != null) {
            fpp.addFilter(fxaaFilter);
        }
    }

    private void applyShadowFilterSettings() {
        if (shadowFilter == null) {
            return;
        }
        shadowFilter.setShadowIntensity(shadowSettings.getShadowIntensity());
        shadowFilter.setEdgesThickness(shadowSettings.getEdgesThickness());
        shadowFilter.setShadowZExtend(shadowSettings.getShadowZExtend());
    }

    private String normalizeEffectId(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return "";
        }
        return switch (effectId.trim()) {
            case "lsf", "light-scattering", "light_scattering" -> EFFECT_LIGHT_SCATTERING;
            case "depthOfField", "depth-of-field", "depth_of_field" -> EFFECT_DOF;
            case "shadow", "shadowFilter" -> EFFECT_SHADOWS;
            default -> effectId.trim();
        };
    }

    private void publish(String topic, Map<String, Object> payload) {
        context.getModuleRegistry().publishEvent(topic, payload);
    }
}
