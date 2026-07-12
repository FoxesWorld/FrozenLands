package org.takesome.frozenlands.engine.world.terrain;

import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TerrainModule implements EngineModule {
    public static final String ID = "engine.terrain";

    private final TerrainManager terrainManager;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public TerrainModule(TerrainManager terrainManager) {
        this.terrainManager = terrainManager;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Terrain, chunks and congruent map-entity placement control";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of(
                "status",
                "Return terrain/chunk status",
                args -> terrainManager.status()
        ));
        commands.put("settings", ModuleCommand.of(
                "settings",
                "Return terrain runtime settings",
                args -> terrainManager.settingsSnapshot()
        ));
        commands.put("placementGroups", ModuleCommand.of(
                "placementGroups",
                "Return data-driven map-entity placement groups",
                args -> result("groups", terrainManager.placementGroups())
        ));
        commands.put("validatePlacement", ModuleCommand.of(
                "validatePlacement",
                "Score footprint congruence and return a grounded transform",
                terrainManager::validatePlacement
        ));
        commands.put("chunks", ModuleCommand.of(
                "chunks",
                "Return terrain chunk snapshots",
                args -> result(
                        "chunks",
                        terrainManager.getChunkTracker().snapshotMaps()
                )
        ));
        commands.put("heightAt", ModuleCommand.of(
                "heightAt",
                "Return terrain height at x/z",
                this::heightAt
        ));
        commands.put("sample", ModuleCommand.of(
                "sample",
                "Return gameplay terrain sample at x/z",
                this::sample
        ));
        commands.put("spawnLocation", ModuleCommand.of(
                "spawnLocation",
                "Resolve safe terrain spawn location",
                this::spawnLocation
        ));
    }

    private Map<String, Object> heightAt(Map<String, Object> args) {
        float x = RuntimeMaps.floating(args, "x", 0f);
        float z = RuntimeMaps.floating(args, "z", 0f);
        Optional<Float> height = terrainManager.getHeightAt(x, z);
        Map<String, Object> result = result("ready", height.isPresent());
        height.ifPresent(value -> result.put("height", value));
        return result;
    }

    private Map<String, Object> sample(Map<String, Object> args) {
        float x = RuntimeMaps.floating(args, "x", 0f);
        float z = RuntimeMaps.floating(args, "z", 0f);
        Optional<Map<String, Object>> sample
                = terrainManager.sampleGameplayTerrain(x, z);
        Map<String, Object> result = result("ready", sample.isPresent());
        sample.ifPresent(value -> result.put("sample", value));
        return result;
    }

    private Map<String, Object> spawnLocation(Map<String, Object> args) {
        float x = RuntimeMaps.floating(args, "x", 0f);
        float z = RuntimeMaps.floating(args, "z", 0f);
        float clearance = RuntimeMaps.floating(args, "clearance", 4f);
        Optional<Vector3f> location = terrainManager.findSafeSpawnLocation(
                x,
                z,
                clearance
        );
        Map<String, Object> result = result("ready", location.isPresent());
        location.ifPresent(vector -> {
            result.put("x", vector.x);
            result.put("y", vector.y);
            result.put("z", vector.z);
        });
        return result;
    }

    private Map<String, Object> result(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put(key, value);
        return result;
    }
}
