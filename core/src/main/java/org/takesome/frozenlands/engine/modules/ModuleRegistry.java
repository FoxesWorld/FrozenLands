package org.takesome.frozenlands.engine.modules;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModuleRegistry {
    private final Map<String, EngineModule> modulesById = new LinkedHashMap<>();
    private final ModuleEventBus eventBus;

    public ModuleRegistry() {
        this(new EngineEventBus("module", EngineRuntimeOptions.defaultOptions().eventHistoryLimit("module")));
    }

    public ModuleRegistry(EngineEventBus eventBus) {
        this.eventBus = new ModuleEventBus(eventBus);
    }

    public EngineModule register(EngineModule module, EngineContext context) {
        if (module == null) {
            throw new IllegalArgumentException("Module must not be null");
        }
        if (module.id() == null || module.id().isBlank()) {
            throw new IllegalArgumentException("Module id must not be blank");
        }
        if (modulesById.containsKey(module.id())) {
            throw new IllegalStateException("Module already registered: " + module.id());
        }

        List<String> missingDependencies = missingDependencies(module);
        boolean strictDependencies = EngineRuntimeOptions.defaultOptions().strictModuleDependencies();
        if (!missingDependencies.isEmpty()) {
            String message = "Module dependencies are not registered: module=" + module.id()
                    + " declaredDependencies=" + module.dependencies()
                    + " missingDependencies=" + missingDependencies
                    + " strictDependencies=" + strictDependencies;
            if (strictDependencies) {
                throw new IllegalStateException(message);
            }
            context.getLogger().warn(message);
        }

        modulesById.put(module.id(), module);
        registerModuleService(module, context);
        module.onRegister(context);
        publishEvent("module.registered", Map.of(
                "module", module.id(),
                "serviceId", "module:" + module.id(),
                "serviceType", module.getClass().getName(),
                "declaredDependencies", module.dependencies(),
                "missingDependencies", missingDependencies,
                "strictDependencies", strictDependencies
        ));
        return module;
    }

    private List<String> missingDependencies(EngineModule module) {
        if (module.dependencies().isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String dependency : module.dependencies()) {
            if (dependency == null || dependency.isBlank()) {
                continue;
            }
            String normalized = dependency.trim();
            if (!modulesById.containsKey(normalized)) {
                missing.add(normalized);
            }
        }
        return List.copyOf(missing);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerModuleService(EngineModule module, EngineContext context) {
        Class moduleType = module.getClass();
        String serviceId = "module:" + module.id();
        context.registerService(serviceId, moduleType, module);
        context.getLogger().info(
                "Module registered in ServicePool module={} serviceId={} type={} commands={}",
                module.id(),
                serviceId,
                module.getClass().getName(),
                module.commands().keySet()
        );
    }

    public Optional<EngineModule> find(String id) {
        return Optional.ofNullable(modulesById.get(id));
    }

    public EngineModule require(String id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Module is not registered: " + id));
    }

    public Map<String, Object> call(String moduleId, String commandId, Map<String, Object> arguments) {
        Map<String, Object> result = require(moduleId).execute(commandId, arguments);
        publishEvent("module.command.executed", Map.of("module", moduleId, "command", commandId));
        return result;
    }

    public Map<String, Object> publishEvent(String topic, Map<String, Object> payload) {
        return eventBus.publish(topic, payload);
    }

    public Map<String, Object> publishEvent(String topic, Map<String, Object> payload,
                                            String emitter, Map<String, Object> metadata) {
        return eventBus.publish(topic, payload, emitter, metadata);
    }

    public Map<String, Object> publishLiveEvent(String topic, Map<String, Object> payload) {
        return eventBus.publishLive(topic, payload);
    }

    public Map<String, Object> publishLiveEvent(String topic, Map<String, Object> payload,
                                                String emitter, Map<String, Object> metadata) {
        return eventBus.publishLive(topic, payload, emitter, metadata);
    }

    public ModuleEventBus getEventBus() {
        return eventBus;
    }

    public Map<String, EngineModule> snapshot() {
        return Collections.unmodifiableMap(modulesById);
    }

    public Map<String, Object> luaManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        modulesById.forEach((id, module) -> manifest.put(id, module.luaDescriptor()));
        return Collections.unmodifiableMap(manifest);
    }
}
