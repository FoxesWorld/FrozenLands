package org.takesome.frozenlands.engine.operations;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Process-wide coordinator for mutually exclusive engine operations.
 *
 * <p>Every operation attempt produces a lifecycle event. Only one active lease
 * is allowed for a given operation key, so duplicate requests are observable
 * and rejected before any side effect begins. The coordinator is deliberately
 * generic: runtime startup is one consumer, while save, networking and world
 * transitions can adopt the same contract later.</p>
 */
public final class EngineOperationCoordinator {
    private final EngineContext context;
    private final Map<String, OperationSnapshot> activeByOperation = new LinkedHashMap<>();
    private final Map<String, OperationSnapshot> activeById = new LinkedHashMap<>();
    private final Map<String, OperationSnapshot> latestByOperation = new LinkedHashMap<>();
    private long sequence;

    public EngineOperationCoordinator(EngineContext context) {
        this.context = context;
    }

    public BeginResult begin(String operation, Map<String, Object> request, String source) {
        String safeOperation = normalizeOperation(operation);
        String safeSource = normalizeSource(source);
        Map<String, Object> safeRequest = immutableCopy(request);

        OperationSnapshot startedSnapshot = null;
        OperationSnapshot activeSnapshot;
        String requestId;
        synchronized (this) {
            requestId = safeOperation + ":request:" + (++sequence);
            activeSnapshot = activeByOperation.get(safeOperation);
            if (activeSnapshot == null) {
                String operationId = safeOperation + ":" + (++sequence);
                Instant now = Instant.now();
                startedSnapshot = new OperationSnapshot(
                        safeOperation,
                        operationId,
                        requestId,
                        "running",
                        true,
                        safeSource,
                        safeRequest,
                        Map.of(),
                        "Accepted",
                        0f,
                        now,
                        now
                );
                activeByOperation.put(safeOperation, startedSnapshot);
                activeById.put(operationId, startedSnapshot);
                latestByOperation.put(safeOperation, startedSnapshot);
                activeSnapshot = startedSnapshot;
            }
        }

        Map<String, Object> requested = basePayload(safeOperation, requestId, safeSource, safeRequest);
        requested.put("accepted", startedSnapshot != null);
        requested.put("duplicate", startedSnapshot == null);
        if (activeSnapshot != null) {
            requested.put("operationId", activeSnapshot.operationId());
        }
        publish(EngineEventTopics.ENGINE_OPERATION_REQUESTED, requested);

        if (startedSnapshot == null) {
            Map<String, Object> rejected = activeSnapshot.toMap();
            rejected.put("operationRequestId", activeSnapshot.requestId());
            rejected.put("requestId", requestId);
            rejected.put("accepted", false);
            rejected.put("duplicate", true);
            rejected.put("busy", true);
            rejected.put("reason", "operation-already-running");
            rejected.put("attemptSource", safeSource);
            rejected.put("attemptRequest", safeRequest);
            publish(EngineEventTopics.ENGINE_OPERATION_REJECTED, rejected);
            return new BeginResult(false, true, requestId, activeSnapshot);
        }

        Map<String, Object> started = startedSnapshot.toMap();
        started.put("accepted", true);
        started.put("duplicate", false);
        started.put("busy", true);
        publish(EngineEventTopics.ENGINE_OPERATION_STARTED, started);
        return new BeginResult(true, false, requestId, startedSnapshot);
    }

    public boolean progress(String operationId, String stage, float progress, Map<String, Object> details) {
        OperationSnapshot next;
        synchronized (this) {
            OperationSnapshot current = activeById.get(operationId);
            if (current == null) {
                return false;
            }
            next = current.withProgress(stage, clamp(progress), immutableCopy(details));
            replaceActive(next);
        }
        Map<String, Object> payload = next.toMap();
        payload.put("busy", true);
        publish(EngineEventTopics.ENGINE_OPERATION_PROGRESS, payload);
        return true;
    }

    public boolean complete(String operationId, Map<String, Object> result) {
        OperationSnapshot completed;
        synchronized (this) {
            OperationSnapshot current = activeById.get(operationId);
            if (current == null) {
                return false;
            }
            completed = current.finish("completed", "Completed", 1f, immutableCopy(result));
            removeActive(current);
            latestByOperation.put(current.operation(), completed);
        }
        Map<String, Object> payload = completed.toMap();
        payload.put("busy", false);
        publish(EngineEventTopics.ENGINE_OPERATION_COMPLETED, payload);
        return true;
    }

    public boolean fail(String operationId, String message, Map<String, Object> details) {
        OperationSnapshot failed;
        synchronized (this) {
            OperationSnapshot current = activeById.get(operationId);
            if (current == null) {
                return false;
            }
            Map<String, Object> failure = new LinkedHashMap<>(immutableCopy(details));
            failure.put("message", message == null ? "" : message);
            failed = current.finish("failed", "Failed", 1f, failure);
            removeActive(current);
            latestByOperation.put(current.operation(), failed);
        }
        Map<String, Object> payload = failed.toMap();
        payload.put("busy", false);
        publish(EngineEventTopics.ENGINE_OPERATION_FAILED, payload);
        return true;
    }

    public boolean cancel(String operationId, String reason, Map<String, Object> details) {
        OperationSnapshot cancelled;
        synchronized (this) {
            OperationSnapshot current = activeById.get(operationId);
            if (current == null) {
                return false;
            }
            Map<String, Object> cancellation = new LinkedHashMap<>(immutableCopy(details));
            cancellation.put("reason", reason == null ? "" : reason);
            cancelled = current.finish("cancelled", "Cancelled", current.progress(), cancellation);
            removeActive(current);
            latestByOperation.put(current.operation(), cancelled);
        }
        Map<String, Object> payload = cancelled.toMap();
        payload.put("busy", false);
        publish(EngineEventTopics.ENGINE_OPERATION_CANCELLED, payload);
        return true;
    }

    public synchronized boolean isActive(String operation) {
        if (operation == null || operation.isBlank()) {
            return false;
        }
        return activeByOperation.containsKey(operation.trim());
    }

    public synchronized Optional<Map<String, Object>> active(String operation) {
        if (operation == null || operation.isBlank()) {
            return Optional.empty();
        }
        OperationSnapshot snapshot = activeByOperation.get(operation.trim());
        return snapshot == null ? Optional.empty() : Optional.of(snapshot.toMap());
    }

    public synchronized List<Map<String, Object>> activeSnapshots() {
        return activeByOperation.values().stream()
                .map(OperationSnapshot::toMap)
                .map(Collections::unmodifiableMap)
                .toList();
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("activeCount", activeByOperation.size());
        result.put("active", snapshots(activeByOperation));
        result.put("latest", snapshots(latestByOperation));
        return Collections.unmodifiableMap(result);
    }

    private void replaceActive(OperationSnapshot snapshot) {
        activeByOperation.put(snapshot.operation(), snapshot);
        activeById.put(snapshot.operationId(), snapshot);
        latestByOperation.put(snapshot.operation(), snapshot);
    }

    private void removeActive(OperationSnapshot snapshot) {
        activeByOperation.remove(snapshot.operation());
        activeById.remove(snapshot.operationId());
    }

    private Map<String, Object> snapshots(Map<String, OperationSnapshot> source) {
        Map<String, Object> snapshots = new LinkedHashMap<>();
        source.forEach((operation, snapshot) -> snapshots.put(operation, snapshot.toMap()));
        return Collections.unmodifiableMap(snapshots);
    }

    private Map<String, Object> basePayload(
            String operation,
            String requestId,
            String source,
            Map<String, Object> request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation);
        payload.put("requestId", requestId);
        payload.put("source", source);
        payload.put("request", request);
        return payload;
    }

    private void publish(String topic, Map<String, Object> payload) {
        context.getModuleRegistry().publishEvent(topic, payload, "engine.operations", Map.of(
                "operation", payload.getOrDefault("operation", ""),
                "state", payload.getOrDefault("state", "")
        ));
    }

    private String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Engine operation key must not be blank");
        }
        String normalized = operation.trim();
        if (normalized.contains(" ")) {
            throw new IllegalArgumentException("Engine operation key must not contain spaces: " + operation);
        }
        return normalized;
    }

    private String normalizeSource(String source) {
        return source == null || source.isBlank() ? "engine" : source.trim();
    }

    private Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    public record BeginResult(
            boolean accepted,
            boolean duplicate,
            String requestId,
            OperationSnapshot operation
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("accepted", accepted);
            result.put("duplicate", duplicate);
            result.put("busy", operation != null && operation.active());
            if (operation != null) {
                result.putAll(operation.toMap());
                result.put("operationRequestId", operation.requestId());
            }
            result.put("requestId", requestId);
            return result;
        }
    }

    public record OperationSnapshot(
            String operation,
            String operationId,
            String requestId,
            String state,
            boolean active,
            String source,
            Map<String, Object> request,
            Map<String, Object> details,
            String stage,
            float progress,
            Instant startedAt,
            Instant updatedAt
    ) {
        private OperationSnapshot withProgress(String nextStage, float nextProgress, Map<String, Object> nextDetails) {
            return new OperationSnapshot(
                    operation,
                    operationId,
                    requestId,
                    "running",
                    true,
                    source,
                    request,
                    nextDetails,
                    nextStage == null || nextStage.isBlank() ? stage : nextStage,
                    nextProgress,
                    startedAt,
                    Instant.now()
            );
        }

        private OperationSnapshot finish(
                String nextState,
                String nextStage,
                float nextProgress,
                Map<String, Object> nextDetails
        ) {
            return new OperationSnapshot(
                    operation,
                    operationId,
                    requestId,
                    nextState,
                    false,
                    source,
                    request,
                    nextDetails,
                    nextStage,
                    nextProgress,
                    startedAt,
                    Instant.now()
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("operation", operation);
            map.put("operationId", operationId);
            map.put("requestId", requestId);
            map.put("state", state);
            map.put("active", active);
            map.put("source", source);
            map.put("request", request);
            map.put("details", details);
            map.put("stage", stage);
            map.put("progress", progress);
            map.put("startedAt", startedAt.toString());
            map.put("updatedAt", updatedAt.toString());
            return new LinkedHashMap<>(map);
        }
    }
}
