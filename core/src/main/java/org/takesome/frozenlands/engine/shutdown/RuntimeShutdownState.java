package org.takesome.frozenlands.engine.shutdown;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.util.Map;

public final class RuntimeShutdownState extends BaseAppState {
    private final EngineContext context;
    private AutoCloseable subscription;

    public RuntimeShutdownState(EngineContext context) {
        this.context = context;
    }

    @Override
    protected void initialize(Application application) {
        subscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.RUNTIME_EXIT_REQUESTED,
                this::onRuntimeExitRequested,
                false
        );
    }

    @Override
    protected void cleanup(Application application) {
        if (subscription == null) {
            return;
        }
        try {
            subscription.close();
        } catch (Exception exception) {
            context.getLogger().warn("Runtime shutdown subscription cleanup failed: {}", exception.toString());
        }
        subscription = null;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    private void onRuntimeExitRequested(Map<String, Object> event) {
        Object reason = event == null ? null : event.get("reason");
        RuntimeShutdown.request(context, reason == null ? "runtime-exit-event" : String.valueOf(reason));
    }
}
