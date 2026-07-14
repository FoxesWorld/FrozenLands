package org.takesome.frozenlands.engine.runtime;

import org.slf4j.Logger;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.operations.EngineOperationCoordinator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event-driven staged game-runtime startup.
 *
 * <p>The command side only accepts a start request. The actual heavy installers
 * are executed by the engine update pump in explicit phases, and every semantic
 * transition is emitted through the module event bus so Lua owns orchestration.</p>
 *
 * <p>The full workflow is protected by {@link EngineOperationCoordinator}. The
 * operation remains active until {@code player.spawned}, not merely until the
 * runtime modules are installed, so repeated New/Continue clicks cannot enqueue
 * a second start while terrain or player spawn is still pending.</p>
 */
public final class GameRuntimeStartupController implements AutoCloseable {
    public static final String START_OPERATION = "runtime.game.start";

    private static final Set<String> MENU_RUNTIME_MODULES = Set.of(
            "engine.save",
            "frozenlands.ui",
            "engine.icoParser"
    );

    private final EngineContext context;
    private final Logger logger;
    private final EngineOperationCoordinator operations;
    private final List<EngineRuntimeInstaller> deferredInstallers = new ArrayList<>();
    private final List<String> installed = new ArrayList<>();
    private final AutoCloseable playerSpawnedSubscription;

    private int installerIndex;
    private boolean moduleLoadingEmitted;
    private boolean starting;
    private boolean started;
    private boolean runtimeReady;
    private Map<String, Object> startRequest = Map.of();
    private String operationId = "";

    public GameRuntimeStartupController(EngineContext context, EngineOperationCoordinator operations) {
        this.context = context;
        this.logger = context.getLogger();
        this.operations = operations;
        this.playerSpawnedSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.PLAYER_SPAWNED,
                this::onPlayerSpawned,
                false
        );
    }

    public boolean shouldDefer(EngineRuntimeInstaller installer) {
        String id = installer == null ? "" : installer.id();
        return !MENU_RUNTIME_MODULES.contains(id);
    }

    public void defer(EngineRuntimeInstaller installer) {
        if (installer == null) {
            return;
        }
        deferredInstallers.add(installer);
        logger.info("Deferred game runtime module until explicit request: {} [{}]", installer.id(), installer.getClass().getName());
    }

    public boolean hasDeferredInstallers() {
        return !deferredInstallers.isEmpty();
    }

    public int deferredCount() {
        return deferredInstallers.size();
    }

    public synchronized Map<String, Object> start(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        Map<String, Object> result = baseResult();

        if (operations.isActive(START_OPERATION)) {
            EngineOperationCoordinator.BeginResult rejected = operations.begin(
                    START_OPERATION,
                    safeArguments,
                    source(safeArguments)
            );
            result.putAll(rejected.toMap());
            result.put("alreadyStarted", started);
            result.put("starting", starting || operations.isActive(START_OPERATION));
            result.put("installed", List.copyOf(installed));
            return result;
        }

        if (started) {
            result.put("ok", true);
            result.put("accepted", false);
            result.put("duplicate", false);
            result.put("busy", false);
            result.put("alreadyStarted", true);
            result.put("starting", false);
            result.put("installed", List.copyOf(installed));
            return result;
        }

        EngineOperationCoordinator.BeginResult begin = operations.begin(
                START_OPERATION,
                safeArguments,
                source(safeArguments)
        );
        result.putAll(begin.toMap());
        if (!begin.accepted()) {
            result.put("alreadyStarted", false);
            result.put("starting", true);
            result.put("installed", List.copyOf(installed));
            return result;
        }

        operationId = begin.operation().operationId();
        starting = true;
        runtimeReady = false;
        installerIndex = 0;
        moduleLoadingEmitted = false;
        installed.clear();
        startRequest = safeArguments;

        Map<String, Object> payload = operationPayload();
        payload.put("deferred", deferredInstallers.stream().map(EngineRuntimeInstaller::id).toList());
        context.getModuleRegistry().publishEvent("runtime.game.starting", payload);
        operations.progress(operationId, "Preparing game runtime", 0.02f, payload);

        result.put("alreadyStarted", false);
        result.put("starting", true);
        result.put("installed", List.of());
        return result;
    }

    public void update() {
        if (!starting || started) {
            return;
        }
        int total = Math.max(1, deferredInstallers.size());
        if (installerIndex >= deferredInstallers.size()) {
            completeRuntime();
            return;
        }

        EngineRuntimeInstaller installer = deferredInstallers.get(installerIndex);
        if (!moduleLoadingEmitted) {
            emitModuleLoading(installer, installerIndex, total);
            moduleLoadingEmitted = true;
            return;
        }

        installCurrentModule(installer, total);
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("started", started);
        result.put("starting", starting);
        result.put("runtimeReady", runtimeReady);
        result.put("operation", START_OPERATION);
        result.put("operationId", operationId);
        result.put("operationActive", operations.isActive(START_OPERATION));
        result.put("installed", List.copyOf(installed));
        result.put("deferred", deferredInstallers.stream().map(EngineRuntimeInstaller::id).toList());
        result.put("index", installerIndex);
        result.put("request", startRequest);
        return result;
    }

    @Override
    public synchronized void close() {
        closeQuietly(playerSpawnedSubscription);
        if (!operationId.isBlank() && operations.isActive(START_OPERATION)) {
            operations.cancel(operationId, "runtime-shutdown", Map.of(
                    "installed", List.copyOf(installed)
            ));
        }
        operationId = "";
        starting = false;
    }

    private void emitModuleLoading(EngineRuntimeInstaller installer, int index, int total) {
        float progress = 0.05f + (0.85f * index / total);
        Map<String, Object> payload = modulePayload(installer, index, total, progress);
        context.getModuleRegistry().publishEvent("runtime.game.module.loading", payload);
        operations.progress(operationId, "Loading " + installer.id(), progress, payload);
    }

    private void installCurrentModule(EngineRuntimeInstaller installer, int total) {
        try {
            float before = 0.05f + (0.85f * installerIndex / total);
            Map<String, Object> installing = modulePayload(installer, installerIndex, total, before);
            context.getModuleRegistry().publishEvent("runtime.game.module.installing", installing);
            operations.progress(operationId, "Installing " + installer.id(), before, installing);

            logger.info("Installing FrozenLands game runtime module: {} [{}]", installer.id(), installer.getClass().getName());
            installer.install(context);
            installed.add(installer.id());
            installerIndex++;
            moduleLoadingEmitted = false;

            float after = 0.05f + (0.85f * installerIndex / total);
            Map<String, Object> loaded = modulePayload(installer, installerIndex, total, after);
            context.getModuleRegistry().publishEvent("runtime.game.module.loaded", loaded);
            operations.progress(operationId, "Loaded " + installer.id(), after, loaded);
        } catch (RuntimeException exception) {
            fail(installer, exception);
        }
    }

    private synchronized void completeRuntime() {
        starting = false;
        started = true;
        runtimeReady = true;
        Map<String, Object> payload = operationPayload();
        payload.put("installed", List.copyOf(installed));
        payload.put("deferred", deferredInstallers.size());
        payload.put("progress", 0.96f);
        payload.put("stage", "Preparing expedition scene");
        context.getModuleRegistry().publishEvent("runtime.game.ready", payload);
        operations.progress(operationId, "Awaiting player spawn", 0.98f, payload);
        logger.info("Game runtime ready installed={} operationId={}", installed, operationId);
    }

    private synchronized void fail(EngineRuntimeInstaller installer, RuntimeException exception) {
        starting = false;
        runtimeReady = false;
        Map<String, Object> payload = operationPayload();
        payload.put("installed", List.copyOf(installed));
        payload.put("failedModule", installer.id());
        payload.put("message", exception.toString());
        payload.put("progress", 1.0f);
        payload.put("stage", "Expedition runtime failed");
        context.getModuleRegistry().publishEvent("runtime.game.failed", payload);
        operations.fail(operationId, exception.toString(), payload);
        operationId = "";
        logger.error("Game runtime start failed module={} installed={} message={}", installer.id(), installed, exception.toString(), exception);
    }

    private synchronized void onPlayerSpawned(Map<String, Object> event) {
        if (operationId.isBlank() || !operations.isActive(START_OPERATION)) {
            return;
        }
        Map<String, Object> player = eventPayload(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", startRequest);
        result.put("installed", List.copyOf(installed));
        result.put("player", player);
        result.put("runtimeReady", runtimeReady);
        operations.complete(operationId, result);
        logger.info("Game start operation completed operationId={} player={}", operationId, player);
        operationId = "";
    }

    private Map<String, Object> modulePayload(EngineRuntimeInstaller installer, int index, int total, float progress) {
        Map<String, Object> payload = operationPayload();
        payload.put("module", installer.id());
        payload.put("index", index);
        payload.put("total", total);
        payload.put("progress", clamp(progress));
        payload.put("stage", "Loading " + installer.id());
        payload.put("installed", List.copyOf(installed));
        return payload;
    }

    private Map<String, Object> operationPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", startRequest);
        payload.put("operation", START_OPERATION);
        payload.put("operationId", operationId);
        return payload;
    }

    private Map<String, Object> baseResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", "engine.core");
        result.put("operation", START_OPERATION);
        result.put("deferred", deferredInstallers.size());
        return result;
    }

    private String source(Map<String, Object> arguments) {
        Object value = arguments.get("source");
        return value == null || String.valueOf(value).isBlank() ? "runtime.game.start" : String.valueOf(value);
    }

    private Map<String, Object> eventPayload(Map<String, Object> event) {
        if (event == null) {
            return Map.of();
        }
        Object payload = event.get("payload");
        if (!(payload instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
