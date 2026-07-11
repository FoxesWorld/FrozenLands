package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.EngineContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Single source of truth for the active FrozenLands graphics profile. */
public final class GraphicsQualityService {
    private final GraphicsQualityCatalog catalog;
    private final QualityEventPublisher events;
    private final Map<String, GraphicsQualityTarget> targets = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> targetResults = new LinkedHashMap<>();
    private final Map<String, String> targetFailures = new LinkedHashMap<>();

    private GraphicsQualityProfile current;
    private long revision;

    public GraphicsQualityService(EngineContext context, GraphicsQualityCatalog catalog) {
        this(
                catalog,
                (topic, payload) -> context.getModuleRegistry().publishEvent(
                        topic,
                        payload,
                        GraphicsQualityModule.ID,
                        Map.of("quality", true)
                )
        );
    }

    GraphicsQualityService(
            GraphicsQualityCatalog catalog,
            QualityEventPublisher events
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.events = Objects.requireNonNull(events, "events");
        this.current = catalog.profile(catalog.defaultPreset());
    }

    public synchronized void registerTarget(GraphicsQualityTarget target) {
        Objects.requireNonNull(target, "target");
        String id = requireId(target.id());
        if (targets.putIfAbsent(id, target) != null) {
            throw new IllegalStateException("Graphics quality target already registered: " + id);
        }
        applyTarget(target, current);
    }

    public synchronized GraphicsQualityProfile apply(GraphicsPreset preset) {
        return applyProfile(catalog.profile(preset));
    }

    public synchronized GraphicsQualityProfile applyCustom(Map<String, Object> overrides) {
        return applyProfile(catalog.custom(current, overrides));
    }

    public synchronized GraphicsQualityProfile currentProfile() {
        return current;
    }

    public synchronized List<GraphicsPreset> presets() {
        return catalog.presets();
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", targetFailures.isEmpty());
        result.put("preset", current.preset().name());
        result.put("revision", revision);
        result.put("profile", current.toMap());
        result.put("targets", Map.copyOf(targetResults));
        result.put("failures", Map.copyOf(targetFailures));
        result.put("availablePresets", catalog.presets().stream().map(Enum::name).toList());
        return Collections.unmodifiableMap(result);
    }

    private GraphicsQualityProfile applyProfile(GraphicsQualityProfile profile) {
        GraphicsQualityProfile previous = current;
        Map<String, Map<String, Object>> previousResults = new LinkedHashMap<>(targetResults);
        targetResults.clear();
        targetFailures.clear();

        for (GraphicsQualityTarget target : targets.values()) {
            applyTarget(target, profile);
        }
        if (!targetFailures.isEmpty()) {
            current = previous;
            targetResults.clear();
            targetResults.putAll(previousResults);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestedPreset", profile.preset().name());
            payload.put("activePreset", previous.preset().name());
            payload.put("failures", Map.copyOf(targetFailures));
            events.publish(QualityEvents.PROFILE_FAILED, Map.copyOf(payload));
            throw new IllegalStateException(
                    "Graphics quality profile was rejected by targets: " + targetFailures
            );
        }

        current = profile;
        revision++;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preset", profile.preset().name());
        payload.put("revision", revision);
        payload.put("profile", profile.toMap());
        payload.put("targets", Map.copyOf(targetResults));
        events.publish(QualityEvents.PROFILE_CHANGED, Map.copyOf(payload));
        return profile;
    }

    private void applyTarget(
            GraphicsQualityTarget target,
            GraphicsQualityProfile profile
    ) {
        String id = requireId(target.id());
        try {
            Map<String, Object> result = target.apply(profile);
            targetResults.put(id, result == null ? Map.of() : Map.copyOf(result));
            targetFailures.remove(id);
        } catch (RuntimeException exception) {
            targetFailures.put(id, exception.toString());
        }
    }

    private String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Graphics quality target id must not be blank");
        }
        return id.trim();
    }
}
