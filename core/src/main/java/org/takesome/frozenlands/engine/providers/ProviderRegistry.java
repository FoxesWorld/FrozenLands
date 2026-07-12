package org.takesome.frozenlands.engine.providers;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeOptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {
    private final Map<String, Object> providersById = new LinkedHashMap<>();
    private final Map<Class<?>, Object> providersByType = new LinkedHashMap<>();
    private final ProviderEventBus eventBus;

    public ProviderRegistry() {
        this(new EngineEventBus("provider", EngineRuntimeOptions.defaultOptions().eventHistoryLimit("provider")));
    }

    public ProviderRegistry(EngineEventBus eventBus) {
        this.eventBus = new ProviderEventBus(eventBus);
    }

    public <T> T register(String id, Class<T> type, T provider) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Provider type must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Provider instance must not be null: " + id);
        }
        if (providersById.containsKey(id)) {
            throw new IllegalStateException("Provider id already registered: " + id);
        }

        providersById.put(id, provider);
        providersByType.put(type, provider);
        return provider;
    }

    public EngineProvider register(EngineProvider provider, EngineContext context) {
        register(provider.id(), EngineProvider.class, provider);
        providersByType.put(provider.getClass(), provider);
        provider.register(context);
        publishEvent("provider.registered", Map.of("provider", provider.id()));
        return provider;
    }

    public Optional<Object> find(String id) {
        return Optional.ofNullable(providersById.get(id));
    }

    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(providersByType.get(type)));
    }

    public Object require(String id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Provider is not registered: " + id));
    }

    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Provider is not registered: " + type.getName()));
    }

    public Map<String, Object> call(String providerId, String commandId, Map<String, Object> arguments) {
        Object provider = require(providerId);
        if (!(provider instanceof EngineProvider engineProvider)) {
            throw new IllegalStateException("Provider does not implement EngineProvider: " + providerId);
        }

        Map<String, Object> result = engineProvider.execute(commandId, arguments);
        publishEvent("provider.command.executed", Map.of("provider", providerId, "command", commandId));
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

    public ProviderEventBus getEventBus() {
        return eventBus;
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(providersById);
    }

    public Map<String, Object> luaManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        providersById.forEach((id, provider) -> {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("id", id);
            descriptor.put("class", provider.getClass().getName());
            descriptor.put("type", provider.getClass().getSimpleName());
            descriptor.put("registered", true);
            if (provider instanceof EngineProvider engineProvider) {
                descriptor.put("dependencies", engineProvider.dependencies());
                descriptor.put("lua", engineProvider.luaDescriptor());
            }
            manifest.put(id, descriptor);
        });
        return Collections.unmodifiableMap(manifest);
    }
}
