package org.takesome.frozenlands.engine.core.console;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.util.Map;

/** Keeps the cursor visible while the console owns input focus. */
public final class ConsoleCursorPolicyState extends BaseAppState {
    private final EngineContext context;
    private AutoCloseable cursorSubscription;
    private AutoCloseable consoleSubscription;
    private boolean requestedVisible;
    private boolean consoleOpen;
    private Boolean appliedVisible;

    public ConsoleCursorPolicyState(EngineContext context) {
        this.context = context;
    }

    @Override
    protected void initialize(Application application) {
        cursorSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.CURSOR_VISIBILITY_REQUESTED,
                this::onCursorVisibilityRequested,
                true
        );
        consoleSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.CONSOLE_VISIBILITY_CHANGED,
                this::onConsoleVisibilityChanged,
                true
        );
        applyCursorVisibility();
    }

    @Override
    protected void cleanup(Application application) {
        close(cursorSubscription);
        close(consoleSubscription);
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    private void onCursorVisibilityRequested(Map<String, Object> event) {
        requestedVisible = booleanPayload(event, "visible");
        applyCursorVisibility();
    }

    private void onConsoleVisibilityChanged(Map<String, Object> event) {
        consoleOpen = booleanPayload(event, "open");
        applyCursorVisibility();
    }

    private void applyCursorVisibility() {
        boolean visible = consoleOpen || requestedVisible;
        if (context.getInputManager() == null) {
            return;
        }
        if (appliedVisible != null && appliedVisible == visible) {
            return;
        }
        appliedVisible = visible;
        context.getInputManager().setCursorVisible(visible);
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
