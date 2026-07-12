package org.takesome.frozenlands.engine.world.terrain;

import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record TerrainPlacementProfile(List<TerrainPlacementGroup> groups) {
    public TerrainPlacementProfile {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public static TerrainPlacementProfile from(
            Map<String, Object> placements,
            Map<String, Object> legacyTrees,
            Map<String, Object> legacyTreeCollision,
            Map<String, Object> legacyTreeHealth
    ) {
        List<TerrainPlacementGroup> groups = parseGroups(placements);
        if (groups.isEmpty()) {
            groups.add(legacyTreePlacementGroup(
                    legacyTrees,
                    legacyTreeCollision,
                    legacyTreeHealth
            ));
        }
        return new TerrainPlacementProfile(groups);
    }

    private static List<TerrainPlacementGroup> parseGroups(
            Map<String, Object> placements
    ) {
        List<TerrainPlacementGroup> groups = new ArrayList<>();
        Object rawGroups = placements == null ? null : placements.get("groups");
        for (Object raw : RuntimeMaps.list(rawGroups)) {
            Map<String, Object> map = RuntimeMaps.map(raw);
            if (map.isEmpty()) {
                continue;
            }
            TerrainPlacementGroup group = placementGroup(map, groups.size());
            if (!group.modelPaths().isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    private static TerrainPlacementGroup placementGroup(
            Map<String, Object> source,
            int index
    ) {
        Map<String, Object> scaleMap = RuntimeMaps.map(source.get("scale"));
        Map<String, Object> rotationMap = RuntimeMaps.map(source.get("rotation"));
        Map<String, Object> fitMap = RuntimeMaps.map(source.get("fit"));
        Map<String, Object> congruenceMap = RuntimeMaps.map(
                source.containsKey("congruence")
                        ? source.get("congruence")
                        : source.get("aesthetics")
        );
        Map<String, Object> collisionMap = RuntimeMaps.map(
                source.get("collision")
        );
        Map<String, Object> gameplayMap = RuntimeMaps.map(
                source.get("gameplay")
        );
        List<String> models = modelPaths(source);
        String id = RuntimeMaps.string(
                source,
                "id",
                "terrain_asset_group_" + index
        );
        String kind = RuntimeMaps.string(source, "kind", id);
        float scaleMin = positive(
                RuntimeMaps.floating(scaleMap, "min", 1f),
                1f
        );
        float scaleMax = Math.max(
                scaleMin,
                RuntimeMaps.floating(scaleMap, "max", scaleMin)
        );
        float maxSurfaceDelta = Math.max(
                0f,
                RuntimeMaps.floating(fitMap, "maxSurfaceDelta", 1.5f)
        );
        float minSpacing = Math.max(
                0f,
                RuntimeMaps.floating(fitMap, "minSpacing", 2f)
        );
        TerrainPlacementCongruenceProfile congruence = congruence(
                congruenceMap,
                maxSurfaceDelta,
                minSpacing
        );
        return new TerrainPlacementGroup(
                id,
                RuntimeMaps.bool(source, "enabled", true),
                kind,
                models,
                Math.max(0, RuntimeMaps.integer(source, "minPerTile", 0)),
                Math.max(0, RuntimeMaps.integer(source, "maxPerTile", 0)),
                Math.max(
                        1,
                        RuntimeMaps.integer(source, "placementAttempts", 8)
                ),
                positive(
                        RuntimeMaps.floating(source, "rayStartHeight", 800f),
                        800f
                ),
                scaleMin,
                scaleMax,
                RuntimeMaps.floating(rotationMap, "yawMinDeg", -180f),
                RuntimeMaps.floating(rotationMap, "yawMaxDeg", 180f),
                Math.max(
                        0f,
                        RuntimeMaps.floating(fitMap, "edgePadding", 1f)
                ),
                Math.max(
                        0f,
                        RuntimeMaps.floating(fitMap, "footprintPadding", 0.5f)
                ),
                maxSurfaceDelta,
                minSpacing,
                congruence,
                RuntimeMaps.bool(collisionMap, "enabled", false),
                RuntimeMaps.bool(collisionMap, "mesh", false),
                Math.max(
                        0f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyRadiusFactor",
                                0.14f
                        )
                ),
                Math.max(
                        0.01f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyRadiusMin",
                                0.22f
                        )
                ),
                Math.max(
                        0.01f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyRadiusMax",
                                0.55f
                        )
                ),
                Math.max(
                        0f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyHalfHeightFactor",
                                0.38f
                        )
                ),
                Math.max(
                        0.01f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyHalfHeightMin",
                                1.35f
                        )
                ),
                Math.max(
                        0.01f,
                        RuntimeMaps.floating(
                                collisionMap,
                                "proxyHalfHeightMax",
                                3.6f
                        )
                ),
                RuntimeMaps.floating(
                        collisionMap,
                        "proxyYOffset",
                        0.08f
                ),
                RuntimeMaps.bool(gameplayMap, "grindable", false),
                RuntimeMaps.integer(gameplayMap, "kindCode", 0),
                RuntimeMaps.floating(gameplayMap, "baseHealth", 0f),
                RuntimeMaps.floating(gameplayMap, "healthPerScale", 0f)
        );
    }

    private static TerrainPlacementCongruenceProfile congruence(
            Map<String, Object> source,
            float maxSurfaceDelta,
            float minSpacing
    ) {
        TerrainPlacementCongruenceProfile defaults
                = TerrainPlacementCongruenceProfile.defaults(
                        maxSurfaceDelta,
                        minSpacing
                );
        return new TerrainPlacementCongruenceProfile(
                RuntimeMaps.floating(
                        source,
                        "minScore",
                        defaults.minScore()
                ),
                RuntimeMaps.floating(
                        source,
                        "maxSlopeDegrees",
                        defaults.maxSlopeDegrees()
                ),
                RuntimeMaps.floating(
                        source,
                        "maxPlaneResidual",
                        defaults.maxPlaneResidual()
                ),
                RuntimeMaps.floating(
                        source,
                        "minSupportRatio",
                        defaults.minSupportRatio()
                ),
                RuntimeMaps.floating(
                        source,
                        "preferredClearance",
                        defaults.preferredClearance()
                ),
                RuntimeMaps.floating(
                        source,
                        "clearanceTolerance",
                        defaults.clearanceTolerance()
                ),
                RuntimeMaps.floating(
                        source,
                        "edgeComfort",
                        defaults.edgeComfort()
                ),
                RuntimeMaps.floating(
                        source,
                        "groundingEmbed",
                        defaults.groundingEmbed()
                )
        );
    }

    private static List<String> modelPaths(Map<String, Object> source) {
        List<String> models = RuntimeMaps.stringList(source.get("models"));
        if (!models.isEmpty()) {
            return models;
        }
        String single = RuntimeMaps.string(source, "model", "");
        return single == null || single.isBlank()
                ? List.of()
                : List.of(single);
    }

    private static TerrainPlacementGroup legacyTreePlacementGroup(
            Map<String, Object> trees,
            Map<String, Object> treeCollision,
            Map<String, Object> treeHealth
    ) {
        float maxSurfaceDelta = 1.65f;
        float minSpacing = 2.75f;
        TerrainPlacementCongruenceProfile congruence
                = new TerrainPlacementCongruenceProfile(
                        0.64f,
                        20f,
                        0.48f,
                        0.76f,
                        5.5f,
                        4.0f,
                        3.5f,
                        0.06f
                );
        return new TerrainPlacementGroup(
                "legacy_trees",
                true,
                "tree",
                treeModelPaths(trees),
                Math.max(
                        0,
                        RuntimeMaps.integer(trees, "minPerTile", 80)
                ),
                Math.max(
                        Math.max(
                                0,
                                RuntimeMaps.integer(trees, "minPerTile", 80)
                        ),
                        RuntimeMaps.integer(trees, "maxPerTile", 160)
                ),
                Math.max(
                        1,
                        RuntimeMaps.integer(trees, "placementAttempts", 16)
                ),
                positive(
                        RuntimeMaps.floating(trees, "rayStartHeight", 800f),
                        800f
                ),
                0.42f,
                1.35f,
                -180f,
                180f,
                1.25f,
                0.65f,
                maxSurfaceDelta,
                minSpacing,
                congruence,
                RuntimeMaps.bool(treeCollision, "enabled", true),
                RuntimeMaps.bool(treeCollision, "mesh", false),
                0.14f,
                0.22f,
                0.55f,
                0.38f,
                1.35f,
                3.6f,
                0.08f,
                true,
                1,
                RuntimeMaps.floating(treeHealth, "base", 80f),
                RuntimeMaps.floating(treeHealth, "perScale", 45f)
        );
    }

    private static List<String> treeModelPaths(Map<String, Object> trees) {
        List<String> paths = RuntimeMaps.stringList(
                trees == null ? null : trees.get("models")
        );
        if (paths.isEmpty()) {
            throw new IllegalStateException(
                    "Terrain tree asset model list is empty: terrain.trees.models"
            );
        }
        return paths;
    }

    private static float positive(float value, float fallback) {
        return value > 0f ? value : fallback;
    }
}
