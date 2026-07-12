package org.takesome.frozenlands.engine.shaders;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
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
    private final Sky sky;
    private final ShaderRuntimeSettings runtimeSettings = new ShaderRuntimeSettings();
    private final ShadowSettings shadowSettings = new ShadowSettings();
    private final SolarShadowBenchmark solarShadowBenchmark = new SolarShadowBenchmark();
    private final Map<String, Filter> filters = new LinkedHashMap<>();
    private final Map<String, Boolean> desiredEnabled = new LinkedHashMap<>();

    private LightScatteringFilter lightScatteringFilter;
    private BloomFilter bloomFilter;
    private DepthOfFieldFilter dofFilter;
    private DirectionalLightShadowFilter shadowFilter;
    private PathSampledSolarShadowPipeline solarShadowPipeline;
    private FXAAFilter fxaaFilter;
    private SolarLightingSyncControl solarLightingSyncControl;
    private final Vector3f directionToSun = new Vector3f();
    private final Vector3f directionToMoon = new Vector3f();
    private final Vector3f sunPropagationDirection = new Vector3f();
    private final Vector3f moonPropagationDirection = new Vector3f();
    private final Vector3f primaryPropagationDirection = new Vector3f(0f, -1f, 0f);
    private final Vector3f previousPrimaryPropagationDirection = new Vector3f(0f, -1f, 0f);
    private final Vector3f directionToPrimaryLight = new Vector3f(0f, 1f, 0f);
    private final Vector3f primaryLightWorldPosition = new Vector3f();
    private final Vector3f skyUpDirection = new Vector3f();
    private String currentPrimaryLightSource = "sun";
    private float currentPrimaryAltitudeVisibility = 1f;
    private float currentEnvironmentShadowFactor = 1f;
    private float currentPrimaryShadowFactor = 1f;
    private long primaryDirectionRevision;
    private boolean processorAttached;

    public Shaders(EngineContext context) {
        this.context = context;
        this.fpp = context.getFpp();
        this.assetManager = context.getAssetManager();
        this.viewPort = context.getViewPort();
        this.sky = context.requireService(Sky.class);
        initializeDesiredState();
        setEnabled(runtimeSettings.pipelineEnabled());
    }

    @Override
    protected void initialize(Application application) {
        buildPipeline();
        attachSolarSynchronization();
        if (isEnabled()) {
            attachProcessorIfNeeded();
        }
        publish(EngineEventTopics.SHADER_PIPELINE_INITIALIZED, status());
    }

    @Override
    protected void cleanup(Application application) {
        detachSolarSynchronization();
        detachProcessorIfNeeded();
        if (solarShadowPipeline != null) {
            solarShadowPipeline.detach();
        }
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
        result.put("solarShadows", solarShadowStatus());
        result.put("solarShadowBenchmark", solarShadowBenchmark.status());
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
        if (EFFECT_SHADOWS.equals(normalized)) {
            result.put("available", solarShadowPipeline != null);
            result.put("enabled", solarShadowPipeline != null && solarShadowPipeline.isEnabled());
            result.put("desiredEnabled", desiredEnabled.getOrDefault(normalized, true));
            result.put("filter", "PathSampledSolarShadowPipeline");
            result.put("runtime", solarShadowStatus());
            return result;
        }
        result.put("available", filter != null);
        result.put("enabled", filter != null && filter.isEnabled());
        result.put("desiredEnabled", desiredEnabled.getOrDefault(normalized, true));
        result.put("filter", filter == null ? null : filter.getClass().getSimpleName());
        return result;
    }

    public boolean setEffectEnabled(String effectId, boolean enabled) {
        String normalized = normalizeEffectId(effectId);
        desiredEnabled.put(normalized, enabled);
        if (EFFECT_SHADOWS.equals(normalized)) {
            if (solarShadowPipeline == null) {
                return false;
            }
            solarShadowPipeline.setEnabled(enabled);
            publish(EngineEventTopics.SHADER_EFFECT_ENABLED_CHANGED, Map.of(
                    "id", normalized,
                    "enabled", enabled,
                    "filter", "PathSampledSolarShadowPipeline"
            ));
            return true;
        }
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
        String oldStructure = shadowSettings.structuralKey();
        shadowSettings.update(values);
        boolean rebuildRequired = solarShadowPipeline != null
                && !oldStructure.equals(shadowSettings.structuralKey());
        if (solarShadowPipeline != null) {
            if (rebuildRequired) {
                rebuildSolarShadowPipeline();
            } else {
                solarShadowPipeline.applySettings(shadowSettings);
            }
            solarShadowPipeline.synchronize(primaryPropagationDirection, currentPrimaryShadowFactor);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "shadows");
        payload.put("settings", shadowSettings.toMap());
        payload.put("runtime", solarShadowStatus());
        payload.put("rebuildRequired", rebuildRequired);
        publish(EngineEventTopics.SHADER_SETTINGS_CHANGED, payload);
        return shadowSettings;
    }

    public Map<String, Object> startSolarShadowBenchmark(int frames, int warmupFrames) {
        Map<String, Object> snapshot = new LinkedHashMap<>(shadowSettings.toMap());
        snapshot.put("viewportWidth", viewPort.getCamera().getWidth());
        snapshot.put("viewportHeight", viewPort.getCamera().getHeight());
        snapshot.put("processorAttached", processorAttached);
        snapshot.put("shadowsEnabled", solarShadowPipeline != null && solarShadowPipeline.isEnabled());
        return solarShadowBenchmark.start(frames, warmupFrames, snapshot);
    }

    public Map<String, Object> solarShadowBenchmarkStatus() {
        return solarShadowBenchmark.status();
    }

    public Map<String, Object> cancelSolarShadowBenchmark() {
        return solarShadowBenchmark.cancel();
    }

    public LightScatteringFilter getLsf() { return lightScatteringFilter; }
    public BloomFilter getBloom() { return bloomFilter; }
    public DepthOfFieldFilter getDof() { return dofFilter; }
    public DirectionalLightShadowFilter getShadowFilter() { return shadowFilter; }
    public List<DirectionalLightShadowFilter> getShadowFilters() {
        return solarShadowPipeline == null ? List.of() : solarShadowPipeline.filters();
    }
    public FXAAFilter getFxaaFilter() { return fxaaFilter; }
    public ShadowSettings getShadowSettings() { return shadowSettings; }

    public Map<String, Object> solarShadowStatus() {
        if (solarShadowPipeline == null) {
            Map<String, Object> status = new LinkedHashMap<>(shadowSettings.toMap());
            status.put("initialized", false);
            return status;
        }
        Map<String, Object> status = new LinkedHashMap<>(solarShadowPipeline.status());
        status.put("initialized", true);
        status.put("dynamicSourceTracking", true);
        status.put("directionAuthority", "-SkyLightingSnapshot.mainDirection");
        status.put("primaryDirectionalSource", currentPrimaryLightSource);
        status.put("primaryLightPropagationDirection", vectorMap(primaryPropagationDirection));
        status.put("directionToPrimaryLight", vectorMap(directionToPrimaryLight));
        status.put("primaryLightWorldPosition", vectorMap(primaryLightWorldPosition));
        status.put("primaryAltitudeVisibility", currentPrimaryAltitudeVisibility);
        status.put("environmentShadowFactor", currentEnvironmentShadowFactor);
        status.put("primaryShadowFactor", currentPrimaryShadowFactor);
        status.put("primaryDirectionRevision", primaryDirectionRevision);
        status.put("mainDirectionalLightDirection", sky.getPrimaryDirectionalLight() == null
                ? Map.of()
                : vectorMap(sky.getPrimaryDirectionalLight().getDirection()));
        return status;
    }

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

        sky.getPrimaryLightWorldPosition(
                runtimeSettings.lightDistance(),
                primaryLightWorldPosition
        );
        lightScatteringFilter = new LightScatteringFilter(primaryLightWorldPosition.clone());
        lightScatteringFilter.setLightDensity(runtimeSettings.lightDensity());
        register(EFFECT_LIGHT_SCATTERING, lightScatteringFilter);

        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(runtimeSettings.focusDistance());
        dofFilter.setFocusRange(runtimeSettings.focusRange());
        register(EFFECT_DOF, dofFilter);

        createSolarShadowPipeline();
        synchronizeSolarEffects();

        fxaaFilter = new FXAAFilter();
        register(EFFECT_FXAA, fxaaFilter);
    }

    private void createSolarShadowPipeline() {
        solarShadowPipeline = new PathSampledSolarShadowPipeline(assetManager, fpp, shadowSettings);
        solarShadowPipeline.setEnabled(desiredEnabled.getOrDefault(EFFECT_SHADOWS, true));
        solarShadowPipeline.attach();
        shadowFilter = solarShadowPipeline.primaryFilter();
        filters.put(EFFECT_SHADOWS, shadowFilter);
    }

    private void rebuildSolarShadowPipeline() {
        boolean shadowsEnabled = desiredEnabled.getOrDefault(EFFECT_SHADOWS, true);
        if (fxaaFilter != null) {
            fpp.removeFilter(fxaaFilter);
        }
        if (solarShadowPipeline != null) {
            solarShadowPipeline.detach();
        }
        solarShadowPipeline = new PathSampledSolarShadowPipeline(assetManager, fpp, shadowSettings);
        solarShadowPipeline.setEnabled(shadowsEnabled);
        solarShadowPipeline.attach();
        shadowFilter = solarShadowPipeline.primaryFilter();
        filters.put(EFFECT_SHADOWS, shadowFilter);
        if (fxaaFilter != null) {
            fpp.addFilter(fxaaFilter);
        }
        solarShadowPipeline.synchronize(primaryPropagationDirection, currentPrimaryShadowFactor);
        publish(EngineEventTopics.SHADER_SOLAR_SHADOWS_REBUILT, solarShadowStatus());
    }

    private void attachSolarSynchronization() {
        if (solarLightingSyncControl != null) {
            return;
        }
        solarLightingSyncControl = new SolarLightingSyncControl();
        context.getRootNode().addControl(solarLightingSyncControl);
    }

    private void detachSolarSynchronization() {
        if (solarLightingSyncControl == null) {
            return;
        }
        context.getRootNode().removeControl(solarLightingSyncControl);
        solarLightingSyncControl = null;
    }

    /**
     * Synchronize all directional-light effects from SkyControl's selected main
     * source. This runs after SkyControl, so sun/moon transitions and movement are
     * visible to the shadow cameras in the same frame.
     */
    private void synchronizeSolarEffects() {
        if (sky.getPrimaryDirectionalLight() == null) {
            return;
        }

        sky.getDirectionToSun(directionToSun);
        sky.getDirectionToMoon(directionToMoon);
        sky.getUpDirection(skyUpDirection);
        sky.getPrimaryLightPropagationDirection(primaryPropagationDirection);
        directionToPrimaryLight.set(primaryPropagationDirection).negateLocal();

        sunPropagationDirection.set(directionToSun).negateLocal();
        moonPropagationDirection.set(directionToMoon).negateLocal();
        currentPrimaryLightSource = resolvePrimaryLightSource();

        float directionDot = FastMath.clamp(
                previousPrimaryPropagationDirection.dot(primaryPropagationDirection),
                -1f,
                1f
        );
        if (directionDot < 0.999999f) {
            primaryDirectionRevision++;
            previousPrimaryPropagationDirection.set(primaryPropagationDirection);
        }

        float altitudeSine = FastMath.clamp(
                directionToPrimaryLight.dot(skyUpDirection),
                -1f,
                1f
        );
        currentPrimaryAltitudeVisibility = smoothSourceVisibility(altitudeSine);
        currentEnvironmentShadowFactor = sky.getPrimaryShadowIntensity();
        currentPrimaryShadowFactor = "starlight".equals(currentPrimaryLightSource)
                ? 0f
                : currentPrimaryAltitudeVisibility * currentEnvironmentShadowFactor;

        if (lightScatteringFilter != null) {
            sky.getPrimaryLightWorldPosition(
                    runtimeSettings.lightDistance(),
                    primaryLightWorldPosition
            );
            lightScatteringFilter.setLightPosition(primaryLightWorldPosition);
            float sourceScattering = "moon".equals(currentPrimaryLightSource) ? 0.18f
                    : "sun".equals(currentPrimaryLightSource) ? 1f : 0f;
            lightScatteringFilter.setLightDensity(
                    runtimeSettings.lightDensity()
                            * currentPrimaryAltitudeVisibility
                            * sourceScattering
            );
        }
        if (solarShadowPipeline != null) {
            solarShadowPipeline.synchronize(
                    primaryPropagationDirection,
                    currentPrimaryShadowFactor
            );
        }
    }

    private String resolvePrimaryLightSource() {
        float sunAlignment = primaryPropagationDirection.dot(sunPropagationDirection);
        float moonAlignment = primaryPropagationDirection.dot(moonPropagationDirection);
        if (sky.isSunUp() && sunAlignment >= moonAlignment) {
            return "sun";
        }
        if (sky.isMoonUp() && moonAlignment > sunAlignment) {
            return "moon";
        }
        return "starlight";
    }

    private float smoothSourceVisibility(float altitudeSine) {
        float normalized = FastMath.clamp((altitudeSine + 0.03f) / 0.18f, 0f, 1f);
        return normalized * normalized * (3f - 2f * normalized);
    }

    private final class SolarLightingSyncControl extends AbstractControl {
        @Override
        protected void controlUpdate(float tpf) {
            synchronizeSolarEffects();
            solarShadowBenchmark.update(tpf);
        }

        @Override
        protected void controlRender(RenderManager renderManager, ViewPort viewPort) {
            // Solar synchronization is update-thread work.
        }
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

    private Map<String, Object> vectorMap(Vector3f vector) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("x", vector.x);
        value.put("y", vector.y);
        value.put("z", vector.z);
        return value;
    }

    private String normalizeEffectId(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return "";
        }
        return switch (effectId.trim()) {
            case "lsf", "light-scattering", "light_scattering" -> EFFECT_LIGHT_SCATTERING;
            case "depthOfField", "depth-of-field", "depth_of_field" -> EFFECT_DOF;
            case "shadow", "shadowFilter", "solarShadows", "solar-shadows" -> EFFECT_SHADOWS;
            default -> effectId.trim();
        };
    }

    private void publish(String topic, Map<String, Object> payload) {
        context.getModuleRegistry().publishEvent(topic, payload);
    }
}
