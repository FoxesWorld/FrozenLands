package org.takesome.frozenlands.engine.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small synchronous engine-level event bus.
 *
 * <p>This bus is intentionally infrastructure-only: it knows nothing about UI,
 * modules, gameplay, loading screens or persistence. Runtime layers publish
 * topic strings with map payloads; other layers subscribe to topics or topic
 * prefixes. This keeps startup, module runtime and UI integration decoupled
 * without introducing a monolithic kernel object.</p>
 */
public final class EngineEventBus {
    private static final int DEFAULT_HISTORY_LIMIT = 512;

    private final String busId;
    private final int historyLimit;
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final Map<String, List<Consumer<Map<String, Object>>>> listenersByTopic = new LinkedHashMap<>();
    private final Map<String, List<Consumer<Map<String, Object>>>> listenersByPrefix = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> latestByTopic = new LinkedHashMap<>();
    private final Map<String, Long> countsByTopic = new LinkedHashMap<>();

    private long sequence;
    private long totalPublished;
    private long totalRecorded;
    private long totalLive;
    private long totalDelivered;
    private long totalListenerFailures;

    public EngineEventBus() {
        this("engine", DEFAULT_HISTORY_LIMIT);
    }

    public EngineEventBus(int historyLimit) {
        this("engine", historyLimit);
    }

    public EngineEventBus(String busId, int historyLimit) {
        this.busId = normalizeBusId(busId);
        this.historyLimit = Math.max(0, historyLimit);
    }

    public String busId() {
        return busId;
    }

    public Map<String, Object> publish(String topic, Map<String, Object> payload) {
        return publish(topic, payload, null, Map.of(), true);
    }

    public Map<String, Object> publish(String topic, Map<String, Object> payload, String emitter) {
        return publish(topic, payload, emitter, Map.of(), true);
    }

    public Map<String, Object> publish(String topic, Map<String, Object> payload,
                                       String emitter, Map<String, Object> metadata) {
        return publish(topic, payload, emitter, metadata, true);
    }

    public Map<String, Object> publishLive(String topic, Map<String, Object> payload) {
        return publish(topic, payload, null, Map.of(), false);
    }

    public Map<String, Object> publishLive(String topic, Map<String, Object> payload, String emitter) {
        return publish(topic, payload, emitter, Map.of(), false);
    }

    public Map<String, Object> publishLive(String topic, Map<String, Object> payload,
                                           String emitter, Map<String, Object> metadata) {
        return publish(topic, payload, emitter, metadata, false);
    }

    public AutoCloseable subscribe(String topic, Consumer<Map<String, Object>> listener) {
        return subscribe(topic, listener, false);
    }

    public AutoCloseable subscribe(String topic, Consumer<Map<String, Object>> listener, boolean replayLatest) {
        String normalizedTopic = normalizeTopic(topic);
        if (listener == null) {
            throw new IllegalArgumentException("Engine event listener must not be null");
        }

        List<Map<String, Object>> replayEvents;
        synchronized (this) {
            subscriptionMap(normalizedTopic)
                    .computeIfAbsent(subscriptionKey(normalizedTopic), ignored -> new ArrayList<>())
                    .add(listener);
            replayEvents = replayLatest ? latestMatching(normalizedTopic) : List.of();
        }

        for (Map<String, Object> event : replayEvents) {
            listener.accept(event);
        }
        return () -> unsubscribe(normalizedTopic, listener);
    }

    public synchronized List<Map<String, Object>> snapshot() {
        return List.copyOf(events);
    }

    public synchronized List<Map<String, Object>> recent(int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0 || events.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, events.size() - safeLimit);
        return List.copyOf(events.subList(from, events.size()));
    }

    public synchronized List<Map<String, Object>> drain() {
        List<Map<String, Object>> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public synchronized Map<String, Object> latest(String topic) {
        if (topic == null || topic.isBlank()) {
            return Map.of();
        }
        return latestByTopic.getOrDefault(topic.trim(), Map.of());
    }

    public synchronized Map<String, Object> latestByTopic() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(latestByTopic));
    }

    public synchronized List<String> topics() {
        return List.copyOf(countsByTopic.keySet());
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ok", true);
        status.put("bus", busId);
        status.put("historyLimit", historyLimit);
        status.put("historySize", events.size());
        status.put("totalPublished", totalPublished);
        status.put("totalRecorded", totalRecorded);
        status.put("totalLive", totalLive);
        status.put("totalDelivered", totalDelivered);
        status.put("totalListenerFailures", totalListenerFailures);
        status.put("latestTopics", latestByTopic.size());
        status.put("topicCount", countsByTopic.size());
        status.put("countsByTopic", Collections.unmodifiableMap(new LinkedHashMap<>(countsByTopic)));
        status.put("listenersByTopic", listenerCounts(listenersByTopic));
        status.put("listenersByPrefix", listenerCounts(listenersByPrefix));
        status.put("listenerCount", listenerCount());
        return Collections.unmodifiableMap(status);
    }

    private Map<String, Object> publish(String topic, Map<String, Object> payload,
                                        String emitter, Map<String, Object> metadata,
                                        boolean record) {
        String normalizedTopic = normalizeTopic(topic);
        Map<String, Object> payloadCopy = copyMap(payload);
        Map<String, Object> metadataCopy = copyMap(metadata);
        List<Consumer<Map<String, Object>>> listeners;
        long eventSequence;
        Instant now = Instant.now();

        synchronized (this) {
            eventSequence = ++sequence;
            totalPublished++;
            if (record) {
                totalRecorded++;
            } else {
                totalLive++;
            }
            countsByTopic.merge(normalizedTopic, 1L, Long::sum);
            listeners = matchingListeners(normalizedTopic);
        }

        Map<String, Object> event = event(
                eventSequence,
                normalizedTopic,
                payloadCopy,
                metadataCopy,
                emitter,
                record,
                listeners.size(),
                now
        );

        List<Map<String, Object>> listenerErrors = new ArrayList<>();
        int delivered = 0;
        for (Consumer<Map<String, Object>> listener : listeners) {
            try {
                listener.accept(Collections.unmodifiableMap(event));
                delivered++;
            } catch (RuntimeException exception) {
                listenerErrors.add(listenerError(listener, exception));
            }
        }

        event.put("delivered", delivered);
        event.put("listenerFailures", listenerErrors.size());
        if (!listenerErrors.isEmpty()) {
            event.put("listenerErrors", List.copyOf(listenerErrors));
        }

        Map<String, Object> immutableEvent = Collections.unmodifiableMap(new LinkedHashMap<>(event));
        synchronized (this) {
            latestByTopic.put(normalizedTopic, immutableEvent);
            totalDelivered += delivered;
            totalListenerFailures += listenerErrors.size();
            if (record) {
                remember(immutableEvent);
            }
        }
        return immutableEvent;
    }

    private synchronized void unsubscribe(String topic, Consumer<Map<String, Object>> listener) {
        Map<String, List<Consumer<Map<String, Object>>>> target = subscriptionMap(topic);
        String key = subscriptionKey(topic);
        List<Consumer<Map<String, Object>>> listeners = target.get(key);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            target.remove(key);
        }
    }

    private void remember(Map<String, Object> event) {
        if (historyLimit <= 0) {
            return;
        }
        events.add(event);
        while (events.size() > historyLimit) {
            events.remove(0);
        }
    }

    private Map<String, Object> event(long eventSequence,
                                      String topic,
                                      Map<String, Object> payload,
                                      Map<String, Object> metadata,
                                      String emitter,
                                      boolean record,
                                      int listenerCount,
                                      Instant timestamp) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ok", true);
        event.put("id", busId + ":" + eventSequence);
        event.put("sequence", eventSequence);
        event.put("bus", busId);
        event.put("topic", topic);
        event.put("family", family(topic));
        event.put("timestamp", timestamp.toString());
        event.put("epochMillis", timestamp.toEpochMilli());
        event.put("recorded", record);
        event.put("live", !record);
        event.put("thread", Thread.currentThread().getName());
        event.put("payload", Collections.unmodifiableMap(payload));
        event.put("payloadKeys", List.copyOf(payload.keySet()));
        event.put("payloadSize", payload.size());
        event.put("listenerCount", listenerCount);
        if (emitter != null && !emitter.isBlank()) {
            event.put("emitter", emitter);
        }
        if (!metadata.isEmpty()) {
            event.put("metadata", Collections.unmodifiableMap(metadata));
        }
        return event;
    }

    private Map<String, Object> listenerError(Consumer<Map<String, Object>> listener, RuntimeException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("listener", listener.getClass().getName());
        error.put("error", exception.getClass().getName());
        error.put("message", exception.getMessage());
        return Collections.unmodifiableMap(error);
    }

    private List<Consumer<Map<String, Object>>> matchingListeners(String topic) {
        List<Consumer<Map<String, Object>>> listeners = new ArrayList<>();
        listeners.addAll(listenersByTopic.getOrDefault(topic, List.of()));
        listenersByPrefix.forEach((prefix, prefixListeners) -> {
            if (topic.startsWith(prefix)) {
                listeners.addAll(prefixListeners);
            }
        });
        return List.copyOf(listeners);
    }

    private List<Map<String, Object>> latestMatching(String subscriptionTopic) {
        if (!isPrefixSubscription(subscriptionTopic)) {
            Map<String, Object> latest = latestByTopic.get(subscriptionTopic);
            return latest == null ? List.of() : List.of(latest);
        }
        String prefix = subscriptionKey(subscriptionTopic);
        List<Map<String, Object>> matches = new ArrayList<>();
        latestByTopic.forEach((topic, event) -> {
            if (topic.startsWith(prefix)) {
                matches.add(event);
            }
        });
        return List.copyOf(matches);
    }

    private Map<String, List<Consumer<Map<String, Object>>>> subscriptionMap(String topic) {
        return isPrefixSubscription(topic) ? listenersByPrefix : listenersByTopic;
    }

    private String subscriptionKey(String topic) {
        if ("*".equals(topic)) {
            return "";
        }
        return isPrefixSubscription(topic) ? topic.substring(0, topic.length() - 1) : topic;
    }

    private boolean isPrefixSubscription(String topic) {
        return "*".equals(topic) || topic.endsWith(".*");
    }

    private Map<String, Long> listenerCounts(Map<String, List<Consumer<Map<String, Object>>>> listeners) {
        Map<String, Long> counts = new LinkedHashMap<>();
        listeners.forEach((topic, topicListeners) -> counts.put(topic, (long) topicListeners.size()));
        return Collections.unmodifiableMap(counts);
    }

    private long listenerCount() {
        long exact = listenersByTopic.values().stream().mapToLong(List::size).sum();
        long prefix = listenersByPrefix.values().stream().mapToLong(List::size).sum();
        return exact + prefix;
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Engine event topic must not be blank");
        }
        return topic.trim();
    }

    private String normalizeBusId(String value) {
        String normalized = value == null || value.isBlank() ? "engine" : value.trim();
        if (normalized.contains(" ")) {
            throw new IllegalArgumentException("Engine event bus id must not contain spaces: " + value);
        }
        return normalized;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private String family(String topic) {
        int dot = topic.indexOf('.');
        return dot <= 0 ? topic : topic.substring(0, dot);
    }
}
