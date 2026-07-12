package org.takesome.frozenlands.engine.world.spawn;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.lua.LuaRuntimeConfig;
import org.takesome.frozenlands.engine.player.Player;
import org.takesome.frozenlands.engine.player.PlayerManager;
import org.takesome.frozenlands.engine.world.terrain.TerrainManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SpawnManager extends BaseAppState {
    private static final String READY_VISUAL_HEIGHT = "visual_height";
    private static final String READY_PHYSICS_COLLISION = "physics_collision";
    private static final String READY_IMMEDIATE = "immediate";

    private final EngineContext context;
    private final TerrainManager terrainManager;
    private final float spawnX;
    private final float spawnZ;
    private final float spawnClearance;
    private final Vector3f fallbackSpawn;
    private final String terrainReadyMode;
    private final String terrainReadyEvent;
    private final boolean spawnOnTerrainReadyEvent;
    private final boolean replayTerrainReadyEvent;
    private final boolean autoSpawn;
    private Player player;
    private boolean spawnRequested;
    private int framesWaited;
    private boolean terrainReadyEventReceived;
    private AutoCloseable terrainReadySubscription;

    public SpawnManager(EngineContext context, TerrainManager terrainManager) {
        this.context = context;
        this.terrainManager = terrainManager;

        LuaRuntimeConfig loader = new LuaRuntimeConfig();
        Map<String, Object> config = loader.read("engine.world");
        Map<String, Object> spawn = loader.map(config, "spawn");
        this.spawnX = loader.floating(spawn, "x", 0f);
        this.spawnZ = loader.floating(spawn, "z", 0f);
        this.spawnClearance = loader.floating(spawn, "clearance", 4f);
        this.fallbackSpawn = new Vector3f(loader.floating(spawn, "fallbackX", 0f),
                loader.floating(spawn, "fallbackY", 150f),
                loader.floating(spawn, "fallbackZ", 0f));
        this.terrainReadyMode = loader.string(spawn, "terrainReadyMode", READY_PHYSICS_COLLISION);
        this.terrainReadyEvent = loader.string(spawn, "terrainReadyEvent", "terrain." + "collision.ready");
        this.spawnOnTerrainReadyEvent = loader.bool(spawn, "spawnOnTerrainReadyEvent", true);
        this.replayTerrainReadyEvent = loader.bool(spawn, "replayTerrainReadyEvent", true);
        this.autoSpawn = loader.bool(spawn, "autoSpawn", false);
        this.spawnRequested = autoSpawn;
    }

    @Override
    protected void initialize(Application application) {
        subscribeTerrainReadyEvent();
    }

    @Override
    protected void cleanup(Application application) {
        closeTerrainReadySubscription();
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void update(float tpf) {
        if (player != null) {
            return;
        }
        framesWaited++;

        if (spawnRequested) {
            attemptTerrainSpawn("terrain-ready-poll");
        }
    }

    public Player spawnNow() {
        spawnRequested = true;
        if (player != null) {
            return player;
        }
        if (!isTerrainReadyForSpawn()) {
            context.getLogger().info("Player spawn requested but terrain is not ready yet mode={} collisionReady={} collisionReadyAtSpawn={} visualReady={}",
                    terrainReadyMode,
                    terrainManager.isTerrainCollisionReady(),
                    terrainManager.isTerrainCollisionReadyAt(spawnX, spawnZ),
                    terrainManager.findSafeSpawnLocation(spawnX, spawnZ, spawnClearance).isPresent());
            return null;
        }
        Optional<Vector3f> spawnLocation = resolveSpawnLocation();
        if (spawnLocation.isEmpty()) {
            context.getLogger().warn("Player spawn requested but no safe terrain location resolved x={} z={} clearance={}", spawnX, spawnZ, spawnClearance);
            return null;
        }
        spawn(spawnLocation.get(), "manual");
        return player;
    }

    public boolean isSpawned() {
        return player != null;
    }

    public Player getPlayer() {
        return player;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("spawned", isSpawned());
        status.put("framesWaited", framesWaited);
        status.put("terrainReadyMode", terrainReadyMode);
        status.put("terrainReadyEvent", terrainReadyEvent);
        status.put("terrainReadyEventReceived", terrainReadyEventReceived);
        status.put("autoSpawn", autoSpawn);
        status.put("spawnRequested", spawnRequested);
        status.put("terrainVisualReady", terrainManager.findSafeSpawnLocation(spawnX, spawnZ, spawnClearance).isPresent());
        status.put("terrainCollisionReady", terrainManager.isTerrainCollisionReady());
        status.put("terrainCollisionReadyAtSpawn", terrainManager.isTerrainCollisionReadyAt(spawnX, spawnZ));
        status.put("terrainReady", isTerrainReadyForSpawn());
        if (player != null) {
            Vector3f position = player.getPlayerPosition();
            status.put("x", position.x);
            status.put("y", position.y);
            status.put("z", position.z);
        }
        return status;
    }


    private void subscribeTerrainReadyEvent() {
        if (!spawnOnTerrainReadyEvent || terrainReadyEvent == null || terrainReadyEvent.isBlank()) {
            return;
        }
        terrainReadySubscription = context.getModuleRegistry().getEventBus().subscribe(terrainReadyEvent, event -> {
            terrainReadyEventReceived = true;
            attemptTerrainSpawn("terrain-ready-event");
        }, replayTerrainReadyEvent);
    }

    private void closeTerrainReadySubscription() {
        if (terrainReadySubscription == null) {
            return;
        }
        try {
            terrainReadySubscription.close();
        } catch (Exception ignored) {
        }
        terrainReadySubscription = null;
    }

    private void attemptTerrainSpawn(String reason) {
        if (!spawnRequested || player != null || !isTerrainReadyForSpawn()) {
            return;
        }
        Optional<Vector3f> terrainSpawn = resolveSpawnLocation();
        terrainSpawn.ifPresent(location -> spawn(location, reason));
    }

    private Optional<Vector3f> resolveSpawnLocation() {
        if (READY_PHYSICS_COLLISION.equals(terrainReadyMode)) {
            return terrainManager.findCollisionSafeSpawnLocation(spawnX, spawnZ, spawnClearance);
        }
        return terrainManager.findSafeSpawnLocation(spawnX, spawnZ, spawnClearance);
    }

    private boolean isTerrainReadyForSpawn() {
        if (READY_IMMEDIATE.equals(terrainReadyMode)) {
            return true;
        }
        if (READY_VISUAL_HEIGHT.equals(terrainReadyMode)) {
            return terrainManager.findSafeSpawnLocation(spawnX, spawnZ, spawnClearance).isPresent();
        }
        return terrainManager.findCollisionSafeSpawnLocation(spawnX, spawnZ, spawnClearance).isPresent();
    }

    private void spawn(Vector3f spawnLocation, String reason) {
        PlayerManager playerManager = context.requireService(PlayerManager.class);
        player = playerManager.spawnPlayer(spawnLocation, reason);
        context.getModuleRegistry().publishEvent("world.player.spawned", Map.of(
                "reason", reason,
                "x", spawnLocation.x,
                "y", spawnLocation.y,
                "z", spawnLocation.z
        ));
    }
}
