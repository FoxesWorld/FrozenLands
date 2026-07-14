package org.takesome.frozenlands.engine.core;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Map;

final class CoreGameRuntimeCommands {
    private final EngineContext context;

    CoreGameRuntimeCommands(EngineContext context) {
        this.context = context;
    }

    void register(Map<String, ModuleCommand> commands) {
        commands.put("runtime.game.start", ModuleCommand.of(
                "runtime.game.start",
                "Accept a deferred game-runtime start request and emit runtime.game.* events.",
                this::start
        ));
        commands.put("runtime.game.status", ModuleCommand.of(
                "runtime.game.status",
                "Return deferred game-runtime status.",
                this::status
        ));
    }

    private Map<String, Object> start(Map<String, Object> args) {
        if (context instanceof Kernel kernel) {
            return kernel.startGameRuntime(args);
        }
        return RuntimeMaps.error("Engine context does not support deferred game runtime start");
    }

    private Map<String, Object> status(Map<String, Object> args) {
        if (context instanceof Kernel kernel) {
            return kernel.gameRuntimeStatus();
        }
        return RuntimeMaps.error("Engine context does not support deferred game runtime status");
    }
}
