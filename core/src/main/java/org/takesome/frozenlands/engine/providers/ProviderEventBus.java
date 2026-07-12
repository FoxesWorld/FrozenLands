package org.takesome.frozenlands.engine.providers;

import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeOptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Compatibility facade for provider-facing event APIs.
 *
 * <p>The implementation is intentionally delegated to EngineEventBus so module,
 * provider and startup events use one small event primitive instead of parallel
 * copy-pasted buses.</p>
 */
public final class ProviderEventBus {
    private final EngineEventBus delegate;

    public ProviderEventBus() {
        this(new EngineEventBus("provider", EngineRuntimeOptions.defaultOptions().eventHistoryLimit("provider")));
    }

    public ProviderEventBus(EngineEventBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public EngineEventBus delegate() {
        return delegate;
    }

    public Map<String, Object> publish(String topic, Map<String, Object> payload) {
        return delegate.publish(topic, payload);
    }

    public Map<String, Object> publish(String topic, Map<String, Object> payload,
                                       String emitter, Map<String, Object> metadata) {
        return delegate.publish(topic, payload, emitter, metadata);
    }

    public Map<String, Object> publishLive(String topic, Map<String, Object> payload) {
        return delegate.publishLive(topic, payload);
    }

    public Map<String, Object> publishLive(String topic, Map<String, Object> payload,
                                           String emitter, Map<String, Object> metadata) {
        return delegate.publishLive(topic, payload, emitter, metadata);
    }

    public AutoCloseable subscribe(String topic, Consumer<Map<String, Object>> listener) {
        return delegate.subscribe(topic, listener);
    }

    public AutoCloseable subscribe(String topic, Consumer<Map<String, Object>> listener, boolean replayLatest) {
        return delegate.subscribe(topic, listener, replayLatest);
    }

    public List<Map<String, Object>> snapshot() {
        return delegate.snapshot();
    }

    public List<Map<String, Object>> recent(int limit) {
        return delegate.recent(limit);
    }

    public List<Map<String, Object>> drain() {
        return delegate.drain();
    }

    public Map<String, Object> latest(String topic) {
        return delegate.latest(topic);
    }

    public Map<String, Object> latestByTopic() {
        return delegate.latestByTopic();
    }

    public List<String> topics() {
        return delegate.topics();
    }

    public Map<String, Object> status() {
        return delegate.status();
    }
}
