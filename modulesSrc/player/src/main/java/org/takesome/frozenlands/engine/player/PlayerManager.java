package org.takesome.frozenlands.engine.player;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PlayerManager extends BaseAppState {
    private final EngineContext context;
    private final PlayerRuntimeSettings settings = new PlayerRuntimeSettings();
    private Player activePlayer;
    private float telemetryTimer;

    public PlayerManager(EngineContext context) {
        this.context = context;
    }

    @Override
    protected void initialize(Application application) {
        publish(EngineEventTopics.PLAYER_MANAGER_READY, Map.of("ready", true));
    }

    @Override
    protected void cleanup(Application application) {
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    @Override
    public void update(float tpf) {
        if (!settings.managerPublishesState() || activePlayer == null) {
            return;
        }
        telemetryTimer += tpf;
        if (telemetryTimer >= Math.max(0.05f, settings.managerTelemetryInterval())) {
            telemetryTimer = 0f;
            context.getModuleRegistry().publishLiveEvent(EngineEventTopics.PLAYER_MANAGER_TELEMETRY, status());
        }
    }

    public Player spawnPlayer(Vector3f spawnLocation, String reason) {
        if (activePlayer != null) {
            return activePlayer;
        }
        Player player = new Player(context);
        player.addPlayer(context.getCamera(), spawnLocation);
        bindActivePlayer(player, reason == null ? "spawn" : reason);
        return player;
    }

    public void bindActivePlayer(Player player, String reason) {
        if (player == null) {
            return;
        }
        Player previous = activePlayer;
        activePlayer = player;
        context.registerService(Player.class, player);
        publish(EngineEventTopics.PLAYER_ACTIVE_CHANGED, Map.of(
                "playerRef", player.runtimeId(),
                "previousPlayerRef", previous == null ? 0 : previous.runtimeId(),
                "reason", reason == null ? "bind" : reason
        ));
        Vector3f position = player.getPlayerPosition();
        publish(EngineEventTopics.PLAYER_SPAWNED, Map.of(
                "playerRef", player.runtimeId(),
                "reason", reason == null ? "spawn" : reason,
                "x", position.x,
                "y", position.y,
                "z", position.z
        ));
    }

    public Optional<Player> activePlayer() {
        return Optional.ofNullable(activePlayer);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ok", true);
        status.put("spawned", activePlayer != null);
        status.put("enabled", isEnabled());
        if (activePlayer != null) {
            status.put("playerRef", activePlayer.runtimeId());
            status.put("hasCharacterControl", activePlayer.getPlayerOptions().getCharacterControl() != null);
            Vector3f position = activePlayer.getPlayerPosition();
            status.put("x", position.x);
            status.put("y", position.y);
            status.put("z", position.z);
            status.put("locomotion", activePlayer.getLocomotionState().toMap(activePlayer.runtimeId()));
            if (activePlayer.getCameraRig() != null) {
                status.put("camera", activePlayer.getCameraRig().status());
            }
        }
        return status;
    }

    public Map<String, Object> warp(Vector3f target, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("warped", false);
        if (activePlayer == null) {
            return result;
        }
        BetterCharacterControl control = activePlayer.getPlayerOptions().getCharacterControl();
        if (control == null) {
            return result;
        }
        control.warp(target);
        result.put("warped", true);
        result.put("x", target.x);
        result.put("y", target.y);
        result.put("z", target.z);
        publish(EngineEventTopics.PLAYER_WARPED, Map.of(
                "playerRef", activePlayer.runtimeId(),
                "reason", reason == null ? "manual" : reason,
                "x", target.x,
                "y", target.y,
                "z", target.z
        ));
        return result;
    }

    private void publish(String topic, Map<String, Object> payload) {
        context.getModuleRegistry().publishEvent(topic, payload);
    }
}
