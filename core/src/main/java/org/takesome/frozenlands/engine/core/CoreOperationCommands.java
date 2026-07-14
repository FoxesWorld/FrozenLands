package org.takesome.frozenlands.engine.core;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.operations.EngineOperationCoordinator;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Map;

/** Core command facade for the global engine-operation lifecycle. */
final class CoreOperationCommands {
    private final EngineContext context;

    CoreOperationCommands(EngineContext context) {
        this.context = context;
    }

    void register(Map<String, ModuleCommand> commands) {
        commands.put("operations.status", ModuleCommand.of(
                "operations.status",
                "Return active and latest engine-operation snapshots.",
                args -> coordinator().status()
        ));
        commands.put("operations.active", ModuleCommand.of(
                "operations.active",
                "Return the active snapshot for one operation key.",
                this::active
        ));
    }

    private Map<String, Object> active(Map<String, Object> args) {
        String operation = RuntimeMaps.string(args, "operation", "");
        return coordinator().active(operation)
                .map(snapshot -> RuntimeMaps.result("operation", snapshot))
                .orElseGet(() -> {
                    Map<String, Object> result = RuntimeMaps.result("operation", Map.of());
                    result.put("active", false);
                    result.put("key", operation);
                    return result;
                });
    }

    private EngineOperationCoordinator coordinator() {
        return context.requireService(EngineOperationCoordinator.class);
    }
}
