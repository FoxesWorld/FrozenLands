package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregates runtime quality data without coupling gameplay systems together. */
public final class QualityDiagnosticsService {
    private static final List<String> STATUS_MODULES = List.of(
            "frozenlands.terrain",
            "engine.terrain",
            "engine.particles",
            "engine.player",
            "engine.world",
            "engine.weather",
            "engine.shaders"
    );

    private final EngineContext context;
    private final GraphicsQualityService quality;
    private final QualityFrameSamplerState sampler;

    public QualityDiagnosticsService(
            EngineContext context,
            GraphicsQualityService quality,
            QualityFrameSamplerState sampler
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    public Map<String, Object> status() {
        Map<String, Object> render = sampler.renderStatus();
        Map<String, Object> assets = context.assets().status();
        Map<String, Object> tasks = context.getTaskPool().status();
        Map<String, Object> events = eventStatus();
        Map<String, Map<String, Object>> modules = moduleStatuses();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("preset", quality.currentProfile().preset().name());
        result.put("quality", quality.status());
        result.put("frame", sampler.frameStatus());
        result.put("render", render);
        result.put("physics", sampler.physicsStatus());
        result.put("world", worldStatus(render, modules));
        result.put("assets", assets);
        result.put("tasks", withPendingTasks(tasks));
        result.put("events", events);
        result.put("services", context.servicePoolStatus());
        result.put("modules", Map.copyOf(modules));
        return Map.copyOf(result);
    }

    private Map<String, Object> eventStatus() {
        Map<String, Object> bus = context.getModuleRegistry().getEventBus().status();
        Map<String, Object> result = new LinkedHashMap<>(bus);
        result.put("deliveryMode", "synchronous");
        result.put("queueDepth", 0);
        result.put("historyDepth", number(bus.get("historySize")));
        return Map.copyOf(result);
    }

    private Map<String, Object> withPendingTasks(Map<String, Object> tasks) {
        Map<String, Object> result = new LinkedHashMap<>(tasks);
        result.put(
                "pending",
                number(tasks.get("active")) + number(tasks.get("queued"))
        );
        return Map.copyOf(result);
    }

    private Map<String, Map<String, Object>> moduleStatuses() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String moduleId : STATUS_MODULES) {
            result.put(moduleId, moduleStatus(moduleId));
        }
        return result;
    }

    private Map<String, Object> moduleStatus(String moduleId) {
        EngineModule module = context.getModuleRegistry().find(moduleId).orElse(null);
        if (module == null) {
            return Map.of("available", false);
        }
        ModuleCommand command = module.commands().get("status");
        if (command == null) {
            return Map.of(
                    "available", true,
                    "statusCommand", false
            );
        }
        try {
            Map<String, Object> status = command.execute(Map.of());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", true);
            result.put("statusCommand", true);
            if (status != null) {
                result.putAll(status);
            }
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            return Map.of(
                    "available", true,
                    "statusCommand", true,
                    "error", exception.toString()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> worldStatus(
            Map<String, Object> render,
            Map<String, Map<String, Object>> modules
    ) {
        Map<String, Integer> statistics = render.get("statistics") instanceof Map<?, ?> raw
                ? (Map<String, Integer>) raw
                : Map.of();
        Map<String, Object> terrain = modules.getOrDefault("engine.terrain", Map.of());
        Map<String, Object> particles = modules.getOrDefault("engine.particles", Map.of());
        Map<String, Object> weather = modules.getOrDefault("engine.weather", Map.of());

        int loadedChunks = findNumber(terrain, List.of("loadedChunks", "activeChunks"));
        int activeBursts = findNumber(particles, List.of("activeBursts"));
        int snowFlakes = findNumber(weather, List.of("flakeCount", "snowFlakeCount"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeChunks", loadedChunks);
        result.put("visibleModels", rendererMetric(statistics, "Objects"));
        result.put("triangles", rendererMetric(statistics, "Triangles"));
        result.put("activeParticles", Math.max(0, activeBursts) + Math.max(0, snowFlakes));
        result.put("activeBursts", activeBursts);
        result.put("snowFlakes", snowFlakes);
        return Map.copyOf(result);
    }

    private int rendererMetric(Map<String, Integer> statistics, String token) {
        for (Map.Entry<String, Integer> entry : statistics.entrySet()) {
            if (entry.getKey().toLowerCase().contains(token.toLowerCase())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private int findNumber(Object value, List<String> keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object direct = map.get(key);
                if (direct instanceof Number number) {
                    return number.intValue();
                }
            }
            for (Object nested : map.values()) {
                int result = findNumber(nested, keys);
                if (result >= 0) {
                    return result;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                int result = findNumber(nested, keys);
                if (result >= 0) {
                    return result;
                }
            }
        }
        return -1;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
