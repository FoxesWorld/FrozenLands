package org.takesome.frozenlands.engine.world.terrain.gen.tree;

import static org.takesome.frozenlands.engine.config.Constants.RAY_DOWN;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.config.Constants;
import org.takesome.frozenlands.engine.gameplay.GameplayUserData;
import org.takesome.frozenlands.engine.utils.Utils;
import org.takesome.frozenlands.engine.world.terrain.TerrainPlacementCongruenceEvaluator;
import org.takesome.frozenlands.engine.world.terrain.TerrainPlacementCongruenceProfile;
import org.takesome.frozenlands.engine.world.terrain.TerrainPlacementGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class TerrainPlacementSolver {
    static final String GROUP_ID_USER_DATA
            = "frozenlands.terrain.asset.group";
    static final String FOOTPRINT_RADIUS_USER_DATA
            = "frozenlands.terrain.asset.footprintRadius";
    static final String CONGRUENCE_SCORE_USER_DATA
            = "frozenlands.terrain.asset.congruenceScore";
    static final String SURFACE_SCORE_USER_DATA
            = "frozenlands.terrain.asset.surfaceScore";
    static final String SPACING_SCORE_USER_DATA
            = "frozenlands.terrain.asset.spacingScore";
    static final String EDGE_SCORE_USER_DATA
            = "frozenlands.terrain.asset.edgeScore";
    static final String SURFACE_SLOPE_USER_DATA
            = "frozenlands.terrain.asset.surfaceSlopeDegrees";
    static final String SUPPORT_RATIO_USER_DATA
            = "frozenlands.terrain.asset.supportRatio";
    static final String PLANE_RESIDUAL_USER_DATA
            = "frozenlands.terrain.asset.planeResidualRms";
    static final String GROUNDING_OFFSET_USER_DATA
            = "frozenlands.terrain.asset.groundingOffset";
    static final String NEAREST_CLEARANCE_USER_DATA
            = "frozenlands.terrain.asset.nearestClearance";
    static final String LOCAL_NEIGHBORS_USER_DATA
            = "frozenlands.terrain.asset.localNeighbors";
    static final String SURFACE_NORMAL_X_USER_DATA
            = "frozenlands.terrain.asset.surfaceNormalX";
    static final String SURFACE_NORMAL_Y_USER_DATA
            = "frozenlands.terrain.asset.surfaceNormalY";
    static final String SURFACE_NORMAL_Z_USER_DATA
            = "frozenlands.terrain.asset.surfaceNormalZ";

    private final EngineContext context;

    TerrainPlacementSolver(EngineContext context) {
        this.context = context;
    }

    boolean placeOnQuad(
            TerrainQuad quad,
            Spatial asset,
            List<Spatial> quadAssets,
            TerrainPlacementGroup group
    ) {
        BoundingBox quadBounds = getQuadBounds(quad);
        if (quadBounds == null) {
            context.getLogger().warn(
                    "Cannot place terrain assets: TerrainQuad has no world bounds: {}",
                    quad.getName()
            );
            return false;
        }

        float initialRadius = footprintRadius(asset, group);
        float margin = initialRadius
                + group.footprintPadding()
                + group.edgePadding();
        Vector3f center = quadBounds.getCenter();
        float minX = center.x - quadBounds.getXExtent() + margin;
        float maxX = center.x + quadBounds.getXExtent() - margin;
        float minZ = center.z - quadBounds.getZExtent() + margin;
        float maxZ = center.z + quadBounds.getZExtent() - margin;
        if (minX >= maxX || minZ >= maxZ) {
            context.getLogger().debug(
                    "Cannot place terrain asset: group={} footprint={} does not fit quad={}",
                    group.id(),
                    initialRadius,
                    quad.getName()
            );
            return false;
        }

        float rayStartY = center.y
                + Math.max(quadBounds.getYExtent(), 1f)
                + group.rayStartHeight();
        PlacementCandidate best = null;
        for (int attempt = 0; attempt < group.placementAttempts(); attempt++) {
            PlacementCandidate candidate = evaluateCandidate(
                    quad,
                    quadBounds,
                    asset,
                    quadAssets,
                    group,
                    rayStartY,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    attempt
            );
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        if (best == null) {
            return false;
        }
        applyCandidate(asset, best);
        markPlacementMetadata(asset, best);
        return true;
    }

    private PlacementCandidate evaluateCandidate(
            TerrainQuad quad,
            BoundingBox quadBounds,
            Spatial asset,
            List<Spatial> quadAssets,
            TerrainPlacementGroup group,
            float rayStartY,
            float minX,
            float maxX,
            float minZ,
            float maxZ,
            int attempt
    ) {
        float x = Utils.getRandomNumberInRange(minX, maxX);
        float z = Utils.getRandomNumberInRange(minZ, maxZ);
        CollisionResult centerHit = raycastTerrain(quad, x, z, rayStartY);
        if (centerHit == null
                || centerHit.getContactPoint().y
                <= Constants.WATER_LEVEL_HEIGHT) {
            return null;
        }

        float yaw = Utils.getRandomNumberInRange(
                group.yawMinDeg(),
                group.yawMaxDeg()
        ) * FastMath.DEG_TO_RAD;
        Quaternion rotation = new Quaternion().fromAngleAxis(
                yaw,
                Vector3f.UNIT_Y
        );
        asset.setLocalTranslation(centerHit.getContactPoint());
        asset.setLocalRotation(rotation);
        refreshBounds(asset);

        BoundingBox boundsBeforeGrounding = boundsOf(asset);
        if (boundsBeforeGrounding == null) {
            return null;
        }
        float candidateRadius = footprintRadius(boundsBeforeGrounding, group);
        TerrainPlacementCongruenceEvaluator.Assessment surface
                = TerrainPlacementCongruenceEvaluator.evaluate(
                        x,
                        z,
                        candidateRadius,
                        group,
                        (sampleX, sampleZ) -> terrainHeight(
                                quad,
                                sampleX,
                                sampleZ,
                                rayStartY
                        )
                );
        if (!surface.hardValid()) {
            return null;
        }

        float bottomY = boundsBeforeGrounding.getCenter().y
                - boundsBeforeGrounding.getYExtent();
        float groundingOffset = surface.recommendedGroundY() - bottomY;
        asset.move(0f, groundingOffset, 0f);
        refreshBounds(asset);
        BoundingBox groundedBounds = boundsOf(asset);
        if (groundedBounds == null
                || !fitsInsideQuadBounds(
                        quadBounds,
                        groundedBounds,
                        group.edgePadding()
                )) {
            return null;
        }

        NeighborAssessment neighbors = assessNeighbors(
                quadAssets,
                asset,
                candidateRadius,
                group
        );
        if (neighbors.overlap()) {
            return null;
        }
        float edgeScore = edgeComfortScore(
                quadBounds,
                groundedBounds,
                group
        );
        float totalScore = clamp01(
                surface.score() * 0.70f
                        + neighbors.score() * 0.22f
                        + edgeScore * 0.08f
        );
        if (totalScore < group.congruence().minScore()) {
            return null;
        }

        return new PlacementCandidate(
                asset.getLocalTranslation().clone(),
                asset.getLocalRotation().clone(),
                candidateRadius,
                totalScore,
                surface,
                neighbors,
                edgeScore,
                groundingOffset,
                attempt
        );
    }

    private Optional<Float> terrainHeight(
            TerrainQuad quad,
            float x,
            float z,
            float rayStartY
    ) {
        CollisionResult hit = raycastTerrain(quad, x, z, rayStartY);
        if (hit == null || !Float.isFinite(hit.getContactPoint().y)) {
            return Optional.empty();
        }
        return Optional.of(hit.getContactPoint().y);
    }

    private CollisionResult raycastTerrain(
            TerrainQuad quad,
            float x,
            float z,
            float rayStartY
    ) {
        CollisionResults results = new CollisionResults();
        quad.collideWith(
                new Ray(new Vector3f(x, rayStartY, z), RAY_DOWN),
                results
        );
        return results.getClosestCollision();
    }

    private boolean fitsInsideQuadBounds(
            BoundingBox quadBounds,
            BoundingBox assetBounds,
            float edgePadding
    ) {
        Vector3f quadCenter = quadBounds.getCenter();
        Vector3f assetCenter = assetBounds.getCenter();
        float quadMinX = quadCenter.x
                - quadBounds.getXExtent()
                + edgePadding;
        float quadMaxX = quadCenter.x
                + quadBounds.getXExtent()
                - edgePadding;
        float quadMinZ = quadCenter.z
                - quadBounds.getZExtent()
                + edgePadding;
        float quadMaxZ = quadCenter.z
                + quadBounds.getZExtent()
                - edgePadding;
        float assetMinX = assetCenter.x - assetBounds.getXExtent();
        float assetMaxX = assetCenter.x + assetBounds.getXExtent();
        float assetMinZ = assetCenter.z - assetBounds.getZExtent();
        float assetMaxZ = assetCenter.z + assetBounds.getZExtent();
        return assetMinX >= quadMinX
                && assetMaxX <= quadMaxX
                && assetMinZ >= quadMinZ
                && assetMaxZ <= quadMaxZ;
    }

    private NeighborAssessment assessNeighbors(
            List<Spatial> quadAssets,
            Spatial candidate,
            float candidateRadius,
            TerrainPlacementGroup group
    ) {
        Vector3f candidateLocation = candidate.getWorldTranslation();
        List<NeighborVector> sameGroupNeighbors = new ArrayList<>();
        float nearestClearance = Float.POSITIVE_INFINITY;
        int localNeighbors = 0;
        boolean repetitiveNeighbor = false;
        TerrainPlacementCongruenceProfile profile = group.congruence();
        float localRadius = profile.preferredClearance()
                + profile.clearanceTolerance();

        for (Spatial existing : quadAssets) {
            if (existing == null || existing == candidate) {
                continue;
            }
            if (Boolean.TRUE.equals(existing.getUserData(
                    GameplayUserData.GRINDABLE_DESTROYED
            ))) {
                continue;
            }
            float existingRadius = userDataFloat(
                    existing,
                    FOOTPRINT_RADIUS_USER_DATA,
                    footprintRadius(existing, group)
            );
            float dx = candidateLocation.x
                    - existing.getWorldTranslation().x;
            float dz = candidateLocation.z
                    - existing.getWorldTranslation().z;
            float centerDistance = (float) Math.sqrt(dx * dx + dz * dz);
            float clearance = centerDistance
                    - candidateRadius
                    - existingRadius;
            if (clearance < group.minSpacing()) {
                return new NeighborAssessment(
                        true,
                        0f,
                        clearance,
                        localNeighbors,
                        false
                );
            }
            nearestClearance = Math.min(nearestClearance, clearance);
            if (clearance <= localRadius) {
                localNeighbors++;
            }

            Object existingGroup = existing.getUserData(GROUP_ID_USER_DATA);
            if (existingGroup != null
                    && group.id().equals(String.valueOf(existingGroup))) {
                sameGroupNeighbors.add(new NeighborVector(
                        new Vector3f(dx, 0f, dz),
                        centerDistance,
                        clearance
                ));
                if (candidate.getName() != null
                        && candidate.getName().equals(existing.getName())
                        && clearance < profile.preferredClearance()) {
                    repetitiveNeighbor = true;
                }
            }
        }

        float spacingScore = spacingScore(
                nearestClearance,
                profile
        );
        float rowPenalty = rowPatternPenalty(sameGroupNeighbors);
        float crowdPenalty = Math.max(0f, localNeighbors - 5) * 0.035f;
        float repetitionPenalty = repetitiveNeighbor ? 0.06f : 0f;
        float score = clamp01(
                spacingScore
                        - rowPenalty
                        - crowdPenalty
                        - repetitionPenalty
        );
        return new NeighborAssessment(
                false,
                score,
                nearestClearance,
                localNeighbors,
                repetitiveNeighbor
        );
    }

    private float spacingScore(
            float nearestClearance,
            TerrainPlacementCongruenceProfile profile
    ) {
        if (!Float.isFinite(nearestClearance)) {
            return 0.82f;
        }
        float deviation = Math.abs(
                nearestClearance - profile.preferredClearance()
        );
        return 1f - clamp01(deviation / profile.clearanceTolerance());
    }

    private float rowPatternPenalty(List<NeighborVector> neighbors) {
        if (neighbors.size() < 2) {
            return 0f;
        }
        neighbors.sort(Comparator.comparingDouble(NeighborVector::distance));
        Vector3f first = neighbors.get(0).offset().normalize();
        Vector3f second = neighbors.get(1).offset().normalize();
        float alignment = Math.abs(first.dot(second));
        if (alignment <= 0.88f) {
            return 0f;
        }
        return clamp01((alignment - 0.88f) / 0.12f) * 0.18f;
    }

    private float edgeComfortScore(
            BoundingBox quadBounds,
            BoundingBox assetBounds,
            TerrainPlacementGroup group
    ) {
        Vector3f quadCenter = quadBounds.getCenter();
        Vector3f assetCenter = assetBounds.getCenter();
        float left = assetCenter.x - assetBounds.getXExtent()
                - (quadCenter.x - quadBounds.getXExtent());
        float right = quadCenter.x + quadBounds.getXExtent()
                - (assetCenter.x + assetBounds.getXExtent());
        float bottom = assetCenter.z - assetBounds.getZExtent()
                - (quadCenter.z - quadBounds.getZExtent());
        float top = quadCenter.z + quadBounds.getZExtent()
                - (assetCenter.z + assetBounds.getZExtent());
        float clearance = Math.min(
                Math.min(left, right),
                Math.min(bottom, top)
        ) - group.edgePadding();
        return clamp01(clearance / group.congruence().edgeComfort());
    }

    private void applyCandidate(
            Spatial asset,
            PlacementCandidate candidate
    ) {
        asset.setLocalTranslation(candidate.translation());
        asset.setLocalRotation(candidate.rotation());
        refreshBounds(asset);
    }

    private void markPlacementMetadata(
            Spatial asset,
            PlacementCandidate candidate
    ) {
        TerrainPlacementCongruenceEvaluator.Assessment surface
                = candidate.surface();
        NeighborAssessment neighbors = candidate.neighbors();
        Vector3f normal = surface.surfaceNormal();
        asset.setUserData(
                FOOTPRINT_RADIUS_USER_DATA,
                candidate.footprintRadius()
        );
        asset.setUserData(CONGRUENCE_SCORE_USER_DATA, candidate.score());
        asset.setUserData(SURFACE_SCORE_USER_DATA, surface.score());
        asset.setUserData(SPACING_SCORE_USER_DATA, neighbors.score());
        asset.setUserData(EDGE_SCORE_USER_DATA, candidate.edgeScore());
        asset.setUserData(SURFACE_SLOPE_USER_DATA, surface.slopeDegrees());
        asset.setUserData(SUPPORT_RATIO_USER_DATA, surface.supportRatio());
        asset.setUserData(
                PLANE_RESIDUAL_USER_DATA,
                surface.planeResidualRms()
        );
        asset.setUserData(
                GROUNDING_OFFSET_USER_DATA,
                candidate.groundingOffset()
        );
        asset.setUserData(
                NEAREST_CLEARANCE_USER_DATA,
                Float.isFinite(neighbors.nearestClearance())
                        ? neighbors.nearestClearance()
                        : -1f
        );
        asset.setUserData(
                LOCAL_NEIGHBORS_USER_DATA,
                neighbors.localNeighbors()
        );
        asset.setUserData(SURFACE_NORMAL_X_USER_DATA, normal.x);
        asset.setUserData(SURFACE_NORMAL_Y_USER_DATA, normal.y);
        asset.setUserData(SURFACE_NORMAL_Z_USER_DATA, normal.z);
    }

    private float footprintRadius(
            Spatial spatial,
            TerrainPlacementGroup group
    ) {
        BoundingBox bounds = boundsOf(spatial);
        return bounds == null
                ? Math.max(
                        0.5f,
                        group == null ? 0.5f : group.footprintPadding()
                )
                : footprintRadius(bounds, group);
    }

    private float footprintRadius(
            BoundingBox bounds,
            TerrainPlacementGroup group
    ) {
        float fromBounds = (float) Math.sqrt(
                bounds.getXExtent() * bounds.getXExtent()
                        + bounds.getZExtent() * bounds.getZExtent()
        );
        float padding = group == null ? 0f : group.footprintPadding();
        return Math.max(0.25f, fromBounds + padding);
    }

    private float userDataFloat(
            Spatial spatial,
            String key,
            float fallback
    ) {
        Object value = spatial.getUserData(key);
        return value instanceof Number number
                ? number.floatValue()
                : fallback;
    }

    private BoundingBox getQuadBounds(TerrainQuad quad) {
        refreshBounds(quad);
        if (quad.getWorldBound() instanceof BoundingBox box) {
            return box;
        }
        return null;
    }

    private BoundingBox boundsOf(Spatial spatial) {
        refreshBounds(spatial);
        BoundingVolume bound = spatial.getWorldBound();
        return bound instanceof BoundingBox box ? box : null;
    }

    private void refreshBounds(Spatial spatial) {
        spatial.updateModelBound();
        spatial.updateGeometricState();
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private record PlacementCandidate(
            Vector3f translation,
            Quaternion rotation,
            float footprintRadius,
            float score,
            TerrainPlacementCongruenceEvaluator.Assessment surface,
            NeighborAssessment neighbors,
            float edgeScore,
            float groundingOffset,
            int attempt
    ) {
    }

    private record NeighborAssessment(
            boolean overlap,
            float score,
            float nearestClearance,
            int localNeighbors,
            boolean repetitiveNeighbor
    ) {
    }

    private record NeighborVector(
            Vector3f offset,
            float distance,
            float clearance
    ) {
    }
}
