package org.takesome.frozenlands.engine.assets;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

final class AssetUsageMetrics {
    private final ConcurrentMap<UsageKey, Usage> usage = new ConcurrentHashMap<>();
    private final LongAdder successfulLoads = new LongAdder();
    private final LongAdder failedLoads = new LongAdder();

    void success(AssetLibrary.Type type, String reference, String source, long nanos) {
        successfulLoads.increment();
        usage.computeIfAbsent(new UsageKey(type, reference), ignored -> new Usage())
                .success(source, nanos);
    }

    void failure(
            AssetLibrary.Type type,
            String reference,
            String source,
            long nanos,
            RuntimeException exception
    ) {
        failedLoads.increment();
        usage.computeIfAbsent(new UsageKey(type, reference), ignored -> new Usage())
                .failure(source, nanos, exception);
    }

    long successfulLoads() {
        return successfulLoads.sum();
    }

    long failedLoads() {
        return failedLoads.sum();
    }

    List<Map<String, Object>> snapshot() {
        return usage.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<UsageKey, Usage> entry) -> entry.getKey().type().name())
                        .thenComparing(entry -> entry.getKey().reference()))
                .map(entry -> entry.getValue().toMap(entry.getKey()))
                .toList();
    }

    private record UsageKey(AssetLibrary.Type type, String reference) {
    }

    private static final class Usage {
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private volatile String source = "";
        private volatile long lastLoadNanos;
        private volatile String lastError = "";

        private void success(String source, long nanos) {
            successes.increment();
            this.source = source;
            this.lastLoadNanos = nanos;
            this.lastError = "";
        }

        private void failure(String source, long nanos, RuntimeException exception) {
            failures.increment();
            this.source = source;
            this.lastLoadNanos = nanos;
            this.lastError = exception.toString();
        }

        private Map<String, Object> toMap(UsageKey key) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", key.type().name());
            result.put("reference", key.reference());
            result.put("source", source);
            result.put("successes", successes.sum());
            result.put("failures", failures.sum());
            result.put("lastLoadMicros", lastLoadNanos / 1_000L);
            result.put("lastError", lastError);
            return result;
        }
    }
}
