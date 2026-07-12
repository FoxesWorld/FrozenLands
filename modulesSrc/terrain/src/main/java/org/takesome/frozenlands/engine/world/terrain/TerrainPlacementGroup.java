package org.takesome.frozenlands.engine.world.terrain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TerrainPlacementGroup(
        String id,
        boolean enabled,
        String kind,
        List<String> modelPaths,
        int minPerTile,
        int maxPerTile,
        int placementAttempts,
        float rayStartHeight,
        float scaleMin,
        float scaleMax,
        float yawMinDeg,
        float yawMaxDeg,
        float edgePadding,
        float footprintPadding,
        float maxSurfaceDelta,
        float minSpacing,
        TerrainPlacementCongruenceProfile congruence,
        boolean collisionEnabled,
        boolean collisionMesh,
        float proxyRadiusFactor,
        float proxyRadiusMin,
        float proxyRadiusMax,
        float proxyHalfHeightFactor,
        float proxyHalfHeightMin,
        float proxyHalfHeightMax,
        float proxyYOffset,
        boolean grindable,
        int gameplayKindCode,
        float baseHealth,
        float healthPerScale
) {
    public TerrainPlacementGroup {
        id = id == null || id.isBlank() ? "terrain_asset_group" : id;
        kind = kind == null || kind.isBlank() ? id : kind;
        modelPaths = modelPaths == null ? List.of() : List.copyOf(modelPaths);
        minPerTile = Math.max(0, minPerTile);
        maxPerTile = Math.max(minPerTile, maxPerTile);
        placementAttempts = Math.max(1, placementAttempts);
        rayStartHeight = rayStartHeight > 0f ? rayStartHeight : 800f;
        scaleMin = scaleMin > 0f ? scaleMin : 1f;
        scaleMax = Math.max(scaleMin, scaleMax);
        edgePadding = Math.max(0f, edgePadding);
        footprintPadding = Math.max(0f, footprintPadding);
        maxSurfaceDelta = Math.max(0f, maxSurfaceDelta);
        minSpacing = Math.max(0f, minSpacing);
        congruence = congruence == null
                ? TerrainPlacementCongruenceProfile.defaults(
                        maxSurfaceDelta,
                        minSpacing
                )
                : congruence;
        proxyRadiusFactor = Math.max(0f, proxyRadiusFactor);
        proxyRadiusMin = Math.max(0.01f, proxyRadiusMin);
        proxyRadiusMax = Math.max(proxyRadiusMin, proxyRadiusMax);
        proxyHalfHeightFactor = Math.max(0f, proxyHalfHeightFactor);
        proxyHalfHeightMin = Math.max(0.01f, proxyHalfHeightMin);
        proxyHalfHeightMax = Math.max(proxyHalfHeightMin, proxyHalfHeightMax);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("enabled", enabled);
        result.put("kind", kind);
        result.put("models", modelPaths);
        result.put("minPerTile", minPerTile);
        result.put("maxPerTile", maxPerTile);
        result.put("placementAttempts", placementAttempts);
        result.put("rayStartHeight", rayStartHeight);
        result.put("scale", Map.of("min", scaleMin, "max", scaleMax));
        result.put("rotation", Map.of("yawMinDeg", yawMinDeg, "yawMaxDeg", yawMaxDeg));
        result.put("fit", Map.of(
                "edgePadding", edgePadding,
                "footprintPadding", footprintPadding,
                "maxSurfaceDelta", maxSurfaceDelta,
                "minSpacing", minSpacing
        ));
        result.put("congruence", congruence.toMap());
        result.put("collision", Map.of(
                "enabled", collisionEnabled,
                "mesh", collisionMesh,
                "proxyRadiusFactor", proxyRadiusFactor,
                "proxyRadiusMin", proxyRadiusMin,
                "proxyRadiusMax", proxyRadiusMax,
                "proxyHalfHeightFactor", proxyHalfHeightFactor,
                "proxyHalfHeightMin", proxyHalfHeightMin,
                "proxyHalfHeightMax", proxyHalfHeightMax,
                "proxyYOffset", proxyYOffset
        ));
        result.put("gameplay", Map.of(
                "grindable", grindable,
                "kindCode", gameplayKindCode,
                "baseHealth", baseHealth,
                "healthPerScale", healthPerScale
        ));
        return result;
    }
}
