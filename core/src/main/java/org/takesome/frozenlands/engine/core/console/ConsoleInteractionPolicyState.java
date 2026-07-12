package org.takesome.frozenlands.engine.core.console;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts console visibility into authoritative interaction-gate requests.
 *
 * <p>The player module also consumes the visibility event directly as a hard
 * gate, so later focus/menu enable requests cannot override an open console.</p>
 */
public final class ConsoleInteractionPolicyState extends BaseAppState {
    private static final String EMITTER = "engine.core.console";

    private final EngineContext context;
    private AutoCloseable subscription;
    private boolean consoleOpen;

    public ConsoleInteractionPolicyState(EngineContext context) {
        this.context = context;
    }

    @Override
    protected void initialize(Application application) {
        subscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.CONSOLE_VISIBILITY_CHANGED,
                this::onConsoleVisibilityChanged,
                true
        );
        if (context.getModuleRegistry().getEventBus()
                .latest(EngineEventTopics.CONSOLE_VISIBILITY_CHANGED)
                .isEmpty()) {
            publishInteractionState(false, "policy-initialized");
        }
    }

    @Override
    protected void cleanup(Application application) {
        if (consoleOpen) {
            publishInteractionState(false, "policy-cleanup");
        }
        close(subscription);
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    private void onConsoleVisibilityChanged(Map<String, Object> event) {
        publishInteractionState(booleanPayload(event, "open"), "console-visibility");
    }

    private void publishInteractionState(boolean open, String reason) {
        consoleOpen = open;
        Map<String, Object> metadata = Map.of(
                "policy", "console",
                "reason", reason
        );
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.CURSOR_VISIBILITY_REQUESTED,
                Map.of("visible", open, "source", "console"),
                EMITTER,
                metadata
        );
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.CAMERA_FOLLOW_PAUSE_REQUESTED,
                Map.of("paused", open, "source", "console"),
                EMITTER,
                metadata
        );
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.CAMERA_LOOK_INPUT_ENABLED_REQUESTED,
                Map.of("enabled", !open, "source", "console"),
                EMITTER,
                metadata
        );
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.PLAYER_INPUT_ENABLED_REQUESTED,
                Map.of("enabled", !open, "source", "console"),
                EMITTER,
                metadata
        );

        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("open", open);
        capture.put("inputBlocked", open);
        capture.put("mouseBlocked", open);
        capture.put("keyboardCaptured", open);
        capture.put("playerInputEnabled", !open);
        capture.put("cameraLookEnabled", !open);
        capture.put("cursorVisible", open);
        capture.put("reason", reason);
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.CONSOLE_INPUT_CAPTURE_CHANGED,
                capture,
                EMITTER,
                metadata
        );
    }

    private boolean booleanPayload(Map<String, Object> event, String key) {
        Object payload = event == null ? null : event.get("payload");
        return payload instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get(key));
    }

    private void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
