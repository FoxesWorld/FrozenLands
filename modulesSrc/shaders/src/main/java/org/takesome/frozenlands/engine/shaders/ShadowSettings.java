package org.takesome.frozenlands.engine.shaders;

import com.jme3.shadow.CompareMode;
import com.jme3.shadow.EdgeFilteringMode;
import org.takesome.frozenlands.engine.lua.LuaRuntimeConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Mutable runtime settings for dynamic solar shadows. */
public final class ShadowSettings {
    private static final String MODULE_ID = "engine.shaders";
    private static final long DEPTH_BYTES_PER_TEXEL = 4L;

    private ShadowQualityPreset preset;
    private String presetId;
    private SolarShadowMode mode;
    private int shadowMapSize;
    private int splits;
    private float shadowZExtend;
    private float shadowZFadeLength;
    private float shadowIntensity;
    private int edgesThickness;
    private int pathSamples;
    private float sunAngularRadiusDegrees;
    private float sampleRotationDegrees;
    private float lambda;
    private boolean stabilizationEnabled;
    private boolean hardwareCompare;
    private boolean renderBackFacesShadows;
    private String edgeFiltering;

    private float pcssAngularRadiusDegrees;
    private float pcssBlockerSearchRadius;
    private float pcssBlockerSearchWorldRadius;
    private float pcssMinFilterRadius;
    private float pcssMaxFilterRadius;
    private int pcssBlockerSamples;
    private int pcssFilterSamples;
    private float pcssDepthBias;
    private float pcssDepthBiasWorld;
    private float pcssPenumbraScale;

    public ShadowSettings() {
        LuaRuntimeConfig loader = new LuaRuntimeConfig();
        Map<String, Object> config = loader.read(MODULE_ID);
        Map<String, Object> shadows = loader.map(config, "shadows");

        preset = ShadowQualityPreset.from(string(shadows, "preset", "balanced"));
        applyPreset(preset);

        String configuredMode = string(shadows, "mode", mode.id());
        String configuredFiltering = string(shadows, "filtering", getFiltering());
        mode = SolarShadowMode.fromConfig(configuredMode, configuredFiltering);
        shadowMapSize = clampMapSize(loader.integer(shadows, "shadowMapSize", shadowMapSize));
        splits = clampSplits(loader.integer(shadows, "splits", splits));
        shadowZExtend = positive(loader.floating(shadows, "shadowZExtend", 180f), 180f);
        shadowZFadeLength = nonNegative(loader.floating(shadows, "shadowZFadeLength", 24f), 24f);
        shadowIntensity = clamp01(loader.floating(shadows, "shadowIntensity", 0.45f));
        edgesThickness = clamp(loader.integer(shadows, "edgesThickness", 3), 1, 10);
        pathSamples = clamp(loader.integer(shadows, "pathSamples", pathSamples), 1, 8);
        sunAngularRadiusDegrees = SolarShadowPathSampler.clampAngularRadius(
                loader.floating(shadows, "sunAngularRadiusDegrees", 0.27f)
        );
        sampleRotationDegrees = finite(loader.floating(shadows, "sampleRotationDegrees", 0f), 0f);
        lambda = clamp01(loader.floating(shadows, "lambda", 0.65f));
        stabilizationEnabled = bool(shadows, "stabilization", true);
        hardwareCompare = bool(shadows, "hardwareCompare", false);
        renderBackFacesShadows = bool(shadows, "renderBackFacesShadows", false);
        edgeFiltering = string(shadows, "edgeFiltering", "PCFPOISSON");

        pcssAngularRadiusDegrees = SolarShadowPathSampler.clampAngularRadius(
                loader.floating(shadows, "pcssAngularRadiusDegrees", pcssAngularRadiusDegrees)
        );
        pcssBlockerSearchRadius = positive(
                loader.floating(shadows, "pcssBlockerSearchRadius", pcssBlockerSearchRadius),
                pcssBlockerSearchRadius
        );
        pcssBlockerSearchWorldRadius = positive(
                loader.floating(shadows, "pcssBlockerSearchWorldRadius", pcssBlockerSearchWorldRadius),
                pcssBlockerSearchWorldRadius
        );
        pcssMinFilterRadius = nonNegative(
                loader.floating(shadows, "pcssMinFilterRadius", pcssMinFilterRadius),
                pcssMinFilterRadius
        );
        pcssMaxFilterRadius = positive(
                loader.floating(shadows, "pcssMaxFilterRadius", pcssMaxFilterRadius),
                pcssMaxFilterRadius
        );
        pcssBlockerSamples = clamp(
                loader.integer(shadows, "pcssBlockerSamples", pcssBlockerSamples),
                1,
                16
        );
        pcssFilterSamples = clamp(
                loader.integer(shadows, "pcssFilterSamples", pcssFilterSamples),
                1,
                16
        );
        pcssDepthBias = nonNegative(
                loader.floating(shadows, "pcssDepthBias", pcssDepthBias),
                pcssDepthBias
        );
        pcssDepthBiasWorld = nonNegative(
                loader.floating(shadows, "pcssDepthBiasWorld", pcssDepthBiasWorld),
                pcssDepthBiasWorld
        );
        pcssPenumbraScale = positive(
                loader.floating(shadows, "pcssPenumbraScale", 1f),
                1f
        );
        normalizePcssRadii();
    }

    public String getPreset() { return presetId; }
    public String getMode() { return mode.id(); }
    public SolarShadowMode getSolarShadowMode() { return mode; }
    public String getFiltering() { return mode.usesPcss() ? "pcss" : "hard"; }
    public boolean isPcssEnabled() { return mode.usesPcss(); }
    public boolean isPathSamplingEnabled() { return mode.usesPathSampling(); }
    public int getShadowMapSize() { return shadowMapSize; }
    public int getSplits() { return splits; }
    public float getShadowZExtend() { return shadowZExtend; }
    public float getShadowZFadeLength() { return shadowZFadeLength; }
    public float getShadowIntensity() { return shadowIntensity; }
    public int getEdgesThickness() { return edgesThickness; }
    public int getPathSamples() { return pathSamples; }
    public int getEffectivePathSamples() { return mode.effectiveSampleCount(pathSamples); }
    public float getSunAngularRadiusDegrees() { return sunAngularRadiusDegrees; }
    public float getSampleRotationDegrees() { return sampleRotationDegrees; }
    public float getLambda() { return lambda; }
    public boolean isStabilizationEnabled() { return stabilizationEnabled; }
    public boolean isHardwareCompare() { return hardwareCompare; }
    public boolean isRenderBackFacesShadows() { return renderBackFacesShadows; }
    public String getEdgeFiltering() { return edgeFiltering; }
    public float getPcssAngularRadiusDegrees() { return pcssAngularRadiusDegrees; }
    public float getPcssBlockerSearchRadius() { return pcssBlockerSearchRadius; }
    public float getPcssBlockerSearchWorldRadius() { return pcssBlockerSearchWorldRadius; }
    public float getPcssMinFilterRadius() { return pcssMinFilterRadius; }
    public float getPcssMaxFilterRadius() { return pcssMaxFilterRadius; }
    public int getPcssBlockerSamples() { return pcssBlockerSamples; }
    public int getPcssFilterSamples() { return pcssFilterSamples; }
    public float getPcssDepthBias() { return pcssDepthBias; }
    public float getPcssDepthBiasWorld() { return pcssDepthBiasWorld; }
    public float getPcssPenumbraScale() { return pcssPenumbraScale; }

    /** Raw depth is required by both manual hard compare and PCSS blocker search. */
    public CompareMode getCompareMode() {
        return CompareMode.Software;
    }

    public EdgeFilteringMode getEdgeFilteringMode() {
        return switch (normalize(edgeFiltering)) {
            case "nearest" -> EdgeFilteringMode.Nearest;
            case "bilinear" -> EdgeFilteringMode.Bilinear;
            case "dither" -> EdgeFilteringMode.Dither;
            case "pcf4" -> EdgeFilteringMode.PCF4;
            case "pcf8" -> EdgeFilteringMode.PCF8;
            case "pcfpoisson", "poisson", "pcf-poisson" -> EdgeFilteringMode.PCFPOISSON;
            default -> EdgeFilteringMode.PCFPOISSON;
        };
    }

    public String structuralKey() {
        return mode.id() + ":" + shadowMapSize + ":" + splits + ":" + getEffectivePathSamples();
    }

    public void update(Map<String, Object> values) {
        if (values == null) {
            return;
        }

        boolean hasManualOverrides = values.keySet().stream()
                .anyMatch(key -> !"preset".equals(key));
        if (values.containsKey("preset")) {
            preset = ShadowQualityPreset.from(stringArg(values, "preset", preset.id()));
            applyPreset(preset);
        }

        boolean modeSupplied = values.containsKey("mode");
        boolean filteringSupplied = values.containsKey("filtering");
        if (modeSupplied) {
            mode = SolarShadowMode.fromConfig(
                    stringArg(values, "mode", mode.id()),
                    stringArg(values, "filtering", getFiltering())
            );
        } else if (filteringSupplied) {
            String filtering = normalize(stringArg(values, "filtering", getFiltering()));
            if (filtering.equals("pcss")) {
                mode = mode.usesPathSampling() ? SolarShadowMode.HYBRID : SolarShadowMode.PCSS_SINGLE_CSM;
            } else {
                mode = SolarShadowMode.PATH_SAMPLED_HARD;
            }
        }

        shadowMapSize = clampMapSize(intArg(values, "shadowMapSize", shadowMapSize));
        splits = clampSplits(intArg(values, "splits", splits));
        shadowZExtend = positive(floatArg(values, "shadowZExtend", shadowZExtend), shadowZExtend);
        shadowZFadeLength = nonNegative(floatArg(values, "shadowZFadeLength", shadowZFadeLength), shadowZFadeLength);
        shadowIntensity = clamp01(floatArg(values, "shadowIntensity", shadowIntensity));
        edgesThickness = clamp(intArg(values, "edgesThickness", edgesThickness), 1, 10);
        pathSamples = clamp(intArg(values, "pathSamples", pathSamples), 1, 8);
        sunAngularRadiusDegrees = SolarShadowPathSampler.clampAngularRadius(
                floatArg(values, "sunAngularRadiusDegrees", sunAngularRadiusDegrees)
        );
        sampleRotationDegrees = finite(
                floatArg(values, "sampleRotationDegrees", sampleRotationDegrees),
                sampleRotationDegrees
        );
        lambda = clamp01(floatArg(values, "lambda", lambda));
        stabilizationEnabled = boolArg(values, "stabilization", stabilizationEnabled);
        hardwareCompare = boolArg(values, "hardwareCompare", hardwareCompare);
        renderBackFacesShadows = boolArg(values, "renderBackFacesShadows", renderBackFacesShadows);
        edgeFiltering = stringArg(values, "edgeFiltering", edgeFiltering);

        pcssAngularRadiusDegrees = SolarShadowPathSampler.clampAngularRadius(
                floatArg(values, "pcssAngularRadiusDegrees", pcssAngularRadiusDegrees)
        );
        pcssBlockerSearchRadius = positive(
                floatArg(values, "pcssBlockerSearchRadius", pcssBlockerSearchRadius),
                pcssBlockerSearchRadius
        );
        pcssBlockerSearchWorldRadius = positive(
                floatArg(values, "pcssBlockerSearchWorldRadius", pcssBlockerSearchWorldRadius),
                pcssBlockerSearchWorldRadius
        );
        pcssMinFilterRadius = nonNegative(
                floatArg(values, "pcssMinFilterRadius", pcssMinFilterRadius),
                pcssMinFilterRadius
        );
        pcssMaxFilterRadius = positive(
                floatArg(values, "pcssMaxFilterRadius", pcssMaxFilterRadius),
                pcssMaxFilterRadius
        );
        pcssBlockerSamples = clamp(intArg(values, "pcssBlockerSamples", pcssBlockerSamples), 1, 16);
        pcssFilterSamples = clamp(intArg(values, "pcssFilterSamples", pcssFilterSamples), 1, 16);
        pcssDepthBias = nonNegative(floatArg(values, "pcssDepthBias", pcssDepthBias), pcssDepthBias);
        pcssDepthBiasWorld = nonNegative(
                floatArg(values, "pcssDepthBiasWorld", pcssDepthBiasWorld),
                pcssDepthBiasWorld
        );
        pcssPenumbraScale = positive(
                floatArg(values, "pcssPenumbraScale", pcssPenumbraScale),
                pcssPenumbraScale
        );
        normalizePcssRadii();
        if (hasManualOverrides) {
            presetId = "custom";
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("preset", presetId);
        map.put("mode", mode.id());
        map.put("filtering", getFiltering());
        map.put("algorithm", mode.algorithmId());
        map.put("backend", "cascaded-shadow-maps");
        map.put("visibilityResolve", "arithmetic-mean");
        map.put("hardwareRayTracing", false);
        map.put("shadowMapSize", shadowMapSize);
        map.put("splits", splits);
        map.put("shadowZExtend", shadowZExtend);
        map.put("shadowZFadeLength", shadowZFadeLength);
        map.put("shadowIntensity", shadowIntensity);
        map.put("edgesThickness", edgesThickness);
        map.put("pathSamples", pathSamples);
        map.put("effectivePathSamples", getEffectivePathSamples());
        map.put("sunAngularRadiusDegrees", sunAngularRadiusDegrees);
        map.put("pcssAngularRadiusDegrees", pcssAngularRadiusDegrees);
        map.put("sampleRotationDegrees", sampleRotationDegrees);
        map.put("lambda", lambda);
        map.put("stabilization", stabilizationEnabled);
        map.put("hardwareCompareRequested", hardwareCompare);
        map.put("effectiveCompareMode", getCompareMode().name());
        map.put("renderBackFacesShadows", renderBackFacesShadows);
        map.put("edgeFiltering", edgeFiltering);
        map.put("pcssBlockerSearchRadius", pcssBlockerSearchRadius);
        map.put("pcssBlockerSearchWorldRadius", pcssBlockerSearchWorldRadius);
        map.put("pcssMinFilterRadius", pcssMinFilterRadius);
        map.put("pcssMaxFilterRadius", pcssMaxFilterRadius);
        map.put("pcssBlockerSamples", pcssBlockerSamples);
        map.put("pcssFilterSamples", pcssFilterSamples);
        map.put("pcssDepthBias", pcssDepthBias);
        map.put("pcssDepthBiasWorld", pcssDepthBiasWorld);
        map.put("pcssPenumbraScale", pcssPenumbraScale);
        map.put("totalShadowMaps", getEffectivePathSamples() * splits);
        map.put("gpuBudget", gpuBudget());
        return map;
    }

    public Map<String, Object> gpuBudget() {
        int samples = getEffectivePathSamples();
        int maps = samples * splits;
        long oneMapBytes = (long) shadowMapSize * shadowMapSize * DEPTH_BYTES_PER_TEXEL;
        long depthMapBytes = oneMapBytes * maps;
        int tapsPerPass = mode.usesPcss() ? pcssBlockerSamples + pcssFilterSamples : 1;
        int receiverDepthSamples = samples * tapsPerPass;

        String risk;
        if (depthMapBytes > 512L * 1024L * 1024L || receiverDepthSamples > 128) {
            risk = "extreme";
        } else if (depthMapBytes > 256L * 1024L * 1024L || receiverDepthSamples > 64) {
            risk = "high";
        } else if (depthMapBytes > 128L * 1024L * 1024L || receiverDepthSamples > 32) {
            risk = "moderate";
        } else {
            risk = "bounded";
        }

        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("shadowMapCount", maps);
        budget.put("shadowMapMemoryBytes", depthMapBytes);
        budget.put("shadowMapMemoryMiB", depthMapBytes / (1024.0 * 1024.0));
        budget.put("fullScreenReceiverPasses", samples);
        budget.put("worstCaseShadowDepthSamplesPerPixel", receiverDepthSamples);
        budget.put("worstCaseShadowDepthSamplesPerPixelMsaa4x", receiverDepthSamples * 4);
        budget.put("risk", risk);
        return budget;
    }

    private void applyPreset(ShadowQualityPreset selected) {
        presetId = selected.id();
        mode = selected.mode();
        shadowMapSize = selected.shadowMapSize();
        splits = selected.splits();
        pathSamples = selected.pathSamples();
        pcssBlockerSamples = selected.blockerSamples();
        pcssFilterSamples = selected.filterSamples();
        pcssBlockerSearchRadius = selected.blockerSearchRadiusTexels();
        pcssBlockerSearchWorldRadius = selected.blockerSearchRadiusWorld();
        pcssMinFilterRadius = selected.minFilterRadiusTexels();
        pcssMaxFilterRadius = selected.maxFilterRadiusTexels();
        pcssDepthBias = selected.normalizedDepthBias();
        pcssDepthBiasWorld = selected.worldDepthBias();
        pcssAngularRadiusDegrees = selected.pcssAngularRadiusDegrees();
        pcssPenumbraScale = 1f;
    }

    private void normalizePcssRadii() {
        if (pcssMaxFilterRadius < pcssMinFilterRadius) {
            pcssMaxFilterRadius = pcssMinFilterRadius;
        }
    }

    private int intArg(Map<String, Object> values, String name, int fallback) {
        Object value = values.get(name);
        return value instanceof Number number
                ? number.intValue()
                : value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private float floatArg(Map<String, Object> values, String name, float fallback) {
        Object value = values.get(name);
        return value instanceof Number number
                ? number.floatValue()
                : value == null ? fallback : Float.parseFloat(String.valueOf(value));
    }

    private boolean boolArg(Map<String, Object> values, String name, boolean fallback) {
        Object value = values.get(name);
        return value instanceof Boolean bool
                ? bool
                : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringArg(Map<String, Object> values, String name, String fallback) {
        Object value = values.get(name);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private boolean bool(Map<String, Object> values, String key, boolean fallback) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Boolean bool
                ? bool
                : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String string(Map<String, Object> values, String key, String fallback) {
        Object value = values == null ? null : values.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int clampMapSize(int value) {
        int safe = clamp(value, 256, 8192);
        int power = Integer.highestOneBit(safe);
        return power < safe ? Math.min(8192, power << 1) : power;
    }

    private int clampSplits(int value) {
        return clamp(value, 1, 4);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, finite(value, 0f)));
    }

    private float positive(float value, float fallback) {
        float safe = finite(value, fallback);
        return safe > 0f ? safe : fallback;
    }

    private float nonNegative(float value, float fallback) {
        float safe = finite(value, fallback);
        return Math.max(0f, safe);
    }

    private float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
