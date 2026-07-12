package org.takesome.frozenlands.engine.world.terrain;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;
import org.takesome.frozenlands.engine.terrain.TerrainService;
import org.takesome.frozenlands.engine.terrain.module.TerrainServiceFactory;
import org.takesome.frozenlands.engine.world.terrain.chunk.TerrainChunkTracker;
import org.takesome.frozenlands.engine.world.terrain.gen.GenAdaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TerrainManager {
    private static final float SPAWN_COLLISION_INSET = 1.0f;

    private final TerrainChunkTracker chunkTracker = new TerrainChunkTracker();
    private final TerrainRuntimeSettings settings = new TerrainRuntimeSettings();
    private final GenAdaptor terrainBuilder;
    private final TerrainService terrainService;
    private TerrainQuad terrain;
    private TerrainQuad mountains;

    public TerrainManager(EngineContext app) {
        this.terrainService = app.findService(TerrainService.class)
                .orElseGet(() -> TerrainServiceFactory.create(app));
        this.terrainBuilder = new GenAdaptor(app, chunkTracker);
        generateTerrain();
    }

    private void generateTerrain() {
        terrain = terrainBuilder.generateTerrain();
        mountains = terrainBuilder.generateMountains();
    }

    public TerrainQuad getTerrain() {
        return terrain;
    }

    public TerrainQuad getMountains() {
        return mountains;
    }

    public TerrainChunkTracker getChunkTracker() {
        return chunkTracker;
    }

    public TerrainService getTerrainService() {
        return terrainService;
    }

    public Optional<Float> getHeightAt(float x, float z) {
        if (terrain == null) {
            return Optional.empty();
        }
        float height = terrain.getHeight(new Vector2f(x, z));
        if (Float.isNaN(height) || Float.isInfinite(height)) {
            return Optional.empty();
        }
        return Optional.of(height);
    }

    public boolean isTerrainCollisionReady() {
        return chunkTracker.hasCollisionReadyChunks();
    }

    public boolean isTerrainCollisionReadyAt(float x, float z) {
        return chunkTracker.hasCollisionReadyAt(
                x,
                z,
                SPAWN_COLLISION_INSET
        );
    }

    public Optional<Float> getWalkableHeightAt(float x, float z) {
        Optional<Float> visualHeight = getHeightAt(x, z);
        if (visualHeight.isPresent()) {
            return visualHeight;
        }
        try {
            return Optional.of(terrainService.walkableHeightAt(
                    TerrainService.DEFAULT_SEED,
                    x,
                    z
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> sampleGameplayTerrain(float x, float z) {
        try {
            return Optional.of(terrainService.sample(
                    TerrainService.DEFAULT_SEED,
                    x,
                    z
            ).toMap());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Vector3f> findSafeSpawnLocation(
            float x,
            float z,
            float clearance
    ) {
        return getWalkableHeightAt(x, z)
                .map(height -> new Vector3f(x, height + clearance, z));
    }

    public Optional<Vector3f> findCollisionSafeSpawnLocation(
            float x,
            float z,
            float clearance
    ) {
        return chunkTracker.resolveCollisionReadyPoint(
                x,
                z,
                SPAWN_COLLISION_INSET
        ).flatMap(point -> getHeightAt(point.x, point.y)
                .map(height -> new Vector3f(
                        point.x,
                        height + clearance,
                        point.y
                )));
    }

    public Map<String, Object> settingsSnapshot() {
        return settings.toMap();
    }

    public List<Map<String, Object>> placementGroups() {
        return settings.placementGroups().stream()
                .map(TerrainPlacementGroup::toMap)
                .toList();
    }

    public Map<String, Object> validatePlacement(Map<String, Object> args) {
        List<TerrainPlacementGroup> groups = settings.placementGroups();
        TerrainPlacementGroup group = selectGroup(
                groups,
                RuntimeMaps.string(
                        args,
                        "group",
                        RuntimeMaps.string(args, "groupId", "")
                )
        );
        float x = RuntimeMaps.floating(args, "x", 0f);
        float z = RuntimeMaps.floating(args, "z", 0f);
        float radius = RuntimeMaps.floating(
                args,
                "radius",
                Math.max(
                        0.5f,
                        group.footprintPadding() + group.edgePadding()
                )
        );
        TerrainPlacementCongruenceEvaluator.Assessment assessment
                = TerrainPlacementCongruenceEvaluator.evaluate(
                        x,
                        z,
                        Math.max(0.1f, radius),
                        group,
                        this::getWalkableHeightAt
                );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("valid", assessment.congruent());
        result.put("group", group.id());
        result.put("kind", group.kind());
        result.put("x", x);
        result.put("z", z);
        result.put("radius", radius);
        result.put("maxSurfaceDelta", group.maxSurfaceDelta());
        result.put("congruenceProfile", group.congruence().toMap());
        result.put("assessment", assessment.toMap());
        result.put("recommendedTransform", Map.of(
                "x", x,
                "y", assessment.resolved()
                        ? assessment.recommendedGroundY()
                        : 0f,
                "z", z,
                "surfaceNormal", vectorMap(assessment.surfaceNormal())
        ));
        if (!assessment.congruent()) {
            result.put(
                    "reason",
                    assessment.reasons().isEmpty()
                            ? "placement-not-congruent"
                            : assessment.reasons().get(0)
            );
            result.put("reasons", assessment.reasons());
        }
        return result;
    }

    private Map<String, Object> vectorMap(Vector3f vector) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", vector.x);
        result.put("y", vector.y);
        result.put("z", vector.z);
        return result;
    }

    private TerrainPlacementGroup selectGroup(
            List<TerrainPlacementGroup> groups,
            String groupId
    ) {
        if (groupId != null && !groupId.isBlank()) {
            for (TerrainPlacementGroup group : groups) {
                if (group.id().equals(groupId)) {
                    return group;
                }
            }
        }
        return groups.isEmpty()
                ? new TerrainRuntimeSettings().placementGroups().get(0)
                : groups.get(0);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("terrain", terrain != null);
        status.put("mountains", mountains != null);
        status.put("chunks", chunkTracker.status());
        status.put("terrainCollisionReady", isTerrainCollisionReady());
        status.put("terrainService", terrainService.status());
        status.put("settings", settingsSnapshot());
        return status;
    }

    public void update(float tpf) {
        terrainBuilder.update();
    }
}
