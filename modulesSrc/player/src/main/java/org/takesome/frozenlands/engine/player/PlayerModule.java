package org.takesome.frozenlands.engine.player;

import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerModule implements EngineModule {
    public static final String ID = "engine.player";

    private final EngineContext context;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public PlayerModule(EngineContext context) {
        this.context = context;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Player manager, movement, footsteps and runtime control";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of("status", "Return active player manager status", args -> manager().status()));
        commands.put("manager.status", ModuleCommand.of("manager.status", "Return player manager status", args -> manager().status()));
        commands.put("position", ModuleCommand.of("position", "Return active player position", args -> position()));
        commands.put("locomotion", ModuleCommand.of("locomotion", "Return active player locomotion state", args -> locomotion()));
        commands.put("tool.status", ModuleCommand.of("tool.status", "Return active player tool status", args -> toolStatus()));
        commands.put("feedback.status", ModuleCommand.of("feedback.status", "Return player feedback router status", args -> feedbackStatus()));
        commands.put("input.sensitivity.status", ModuleCommand.of("input.sensitivity.status", "Return mouse sensitivity settings", args -> inputSensitivityStatus()));
        commands.put("input.sensitivity.set", ModuleCommand.of("input.sensitivity.set", "Set active player mouse sensitivity", this::setInputSensitivity));
        commands.put("warp", ModuleCommand.of("warp", "Warp active player to coordinates", this::warp));
    }

    private Map<String, Object> position() {
        Player player = player();
        if (player == null) {
            return RuntimeMaps.result("spawned", false);
        }
        Vector3f position = player.getPlayerPosition();
        Map<String, Object> result = RuntimeMaps.result("spawned", true);
        result.put("x", position.x);
        result.put("y", position.y);
        result.put("z", position.z);
        return result;
    }

    private Map<String, Object> locomotion() {
        Player player = player();
        if (player == null) {
            return RuntimeMaps.result("spawned", false);
        }
        return RuntimeMaps.result("locomotion", player.getLocomotionState().toMap(player.runtimeId()));
    }

    private Map<String, Object> toolStatus() {
        Player player = player();
        if (player == null || player.getToolController() == null) {
            return RuntimeMaps.result("available", false);
        }
        return RuntimeMaps.result("tool", player.getToolController().status());
    }

    private Map<String, Object> feedbackStatus() {
        return context.findService(PlayerFeedbackRouter.class)
                .map(PlayerFeedbackRouter::status)
                .orElseGet(() -> RuntimeMaps.result("available", false));
    }

    private Map<String, Object> inputSensitivityStatus() {
        Player player = player();
        Map<String, Object> result = RuntimeMaps.result(
                "available",
                player != null
        );
        if (player == null) {
            return result;
        }
        PlayerOptions options = player.getPlayerOptions();
        result.put("sensitivity", options.getMouseSensitivity());
        result.put("minimum", options.getMinMouseSensitivity());
        result.put("maximum", options.getMaxMouseSensitivity());
        return result;
    }

    private Map<String, Object> setInputSensitivity(
            Map<String, Object> args
    ) {
        Player player = player();
        Map<String, Object> result = RuntimeMaps.result(
                "available",
                player != null
        );
        if (player == null) {
            return result;
        }
        PlayerOptions options = player.getPlayerOptions();
        float requested = RuntimeMaps.floating(
                args,
                "sensitivity",
                options.getMouseSensitivity()
        );
        float sensitivity = options.setMouseSensitivity(requested);
        result.put("sensitivity", sensitivity);
        result.put("minimum", options.getMinMouseSensitivity());
        result.put("maximum", options.getMaxMouseSensitivity());
        return result;
    }

    private Map<String, Object> warp(Map<String, Object> args) {
        Vector3f target = new Vector3f(RuntimeMaps.floating(args, "x", 0f), RuntimeMaps.floating(args, "y", 0f), RuntimeMaps.floating(args, "z", 0f));
        return manager().warp(target, RuntimeMaps.string(args, "reason", "module-command"));
    }

    private Player player() {
        return manager().activePlayer().orElseGet(() -> context.findService(Player.class).orElse(null));
    }

    private PlayerManager manager() {
        return context.requireService(PlayerManager.class);
    }



}
