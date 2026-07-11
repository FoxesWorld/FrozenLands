package org.takesome.frozenlands.engine.player;

import org.takesome.frozenlands.engine.assets.AssetLibrary;
import com.jme3.material.Material;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;

public class PlayerModel extends Node {
    private final PlayerOptions playerOptions;
    private Spatial playerSpatial;
    private Geometry playerGeometry;
    private Spatial collisionSpatial;
    private PlayerCollisionProfile collisionProfile;
    private int sanitizedTangentGeometryCount;
    private int sanitizedTangentBufferCount;
    private int sanitizedNormalMapCount;
    private PlayerSkinningGuard.Result skinningGuardResult = PlayerSkinningGuard.Result.notSkinned(0);
    private PlayerSkinningGuard.RepairResult skinningRepairResult = PlayerSkinningGuard.RepairResult.none("not-run");

    public PlayerModel(AssetLibrary assets, PlayerOptions playerOptions) {
        this.playerOptions = playerOptions;
        this.playerSpatial = assets.model(playerOptions.getModelPath());
        sanitizeModelPipeline();
        this.playerSpatial.setLocalScale(playerOptions.getScale());
        attachChild(playerSpatial);
        refreshModelBounds();
        alignVisualFeetToNodeOrigin();
        refreshModelBounds();
        this.playerGeometry = findFirstGeometry(playerSpatial);
        this.collisionSpatial = findNamedSpatial(playerSpatial, playerOptions.getCollisionSourceNode());
        this.collisionProfile = createCollisionProfile();
        if (isVisualGuardedOff()) {
            playerSpatial.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void sanitizeModelPipeline() {
        if (playerOptions.sanitizeSkinnedTangents()) {
            sanitizeTangents(playerSpatial);
        }
        skinningRepairResult = PlayerSkinningGuard.repairMultiSkinTargets(playerSpatial);
        skinningGuardResult = PlayerSkinningGuard.inspect(playerSpatial);
    }

    private void sanitizeTangents(Spatial spatial) {
        if (spatial instanceof Geometry geometry) {
            int removed = sanitizeGeometryTangents(geometry);
            if (removed > 0) {
                sanitizedTangentGeometryCount++;
                sanitizedTangentBufferCount += removed;
            }
            return;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                sanitizeTangents(child);
            }
        }
    }

    private int sanitizeGeometryTangents(Geometry geometry) {
        Mesh mesh = geometry.getMesh();
        if (mesh == null) {
            return 0;
        }
        int removed = 0;
        if (clearBufferIfPresent(mesh, VertexBuffer.Type.Tangent)) {
            removed++;
        }
        if (clearBufferIfTypeExists(mesh, "BindPoseTangent")) {
            removed++;
        }
        if (removed > 0) {
            sanitizedNormalMapCount += clearTangentSpaceTextureParams(geometry);
            mesh.updateCounts();
            mesh.updateBound();
            geometry.updateModelBound();
        }
        return removed;
    }

    private int clearTangentSpaceTextureParams(Geometry geometry) {
        Material material = geometry.getMaterial();
        if (material == null) {
            return 0;
        }
        int removed = 0;
        removed += clearMaterialParamIfPresent(material, "NormalMap");
        removed += clearMaterialParamIfPresent(material, "NormalTexture");
        return removed;
    }

    private int clearMaterialParamIfPresent(Material material, String name) {
        if (material.getParam(name) == null) {
            return 0;
        }
        material.clearParam(name);
        return 1;
    }

    private boolean clearBufferIfTypeExists(Mesh mesh, String typeName) {
        try {
            return clearBufferIfPresent(mesh, VertexBuffer.Type.valueOf(typeName));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean clearBufferIfPresent(Mesh mesh, VertexBuffer.Type type) {
        if (mesh.getBuffer(type) == null) {
            return false;
        }
        mesh.clearBuffer(type);
        return true;
    }

    private void refreshModelBounds() {
        playerSpatial.updateModelBound();
        playerSpatial.updateGeometricState();
        updateModelBound();
        updateGeometricState();
    }

    private void alignVisualFeetToNodeOrigin() {
        BoundingBox bounds = boundsOf(playerSpatial);
        if (bounds == null) {
            return;
        }
        float footY = bounds.getCenter().y - bounds.getYExtent();
        if (Float.isNaN(footY) || Float.isInfinite(footY) || Math.abs(footY) < 0.001f) {
            return;
        }
        playerSpatial.move(0f, -footY, 0f);
    }

    private PlayerCollisionProfile createCollisionProfile() {
        Spatial sourceSpatial = collisionSpatial == null ? playerSpatial : collisionSpatial;
        BoundingBox bounds = boundsOf(sourceSpatial);
        if (bounds == null) {
            return fallbackCollisionProfile(sourceSpatial.getName());
        }

        float modelRadius = Math.max(bounds.getXExtent(), bounds.getZExtent());
        float modelHeight = bounds.getYExtent() * 2f;
        float radius = explicitOrModel(playerOptions.getCollisionRadiusOverride(), modelRadius, playerOptions.getCollisionRadiusScale(), playerOptions.getMinCollisionRadius());
        float height = explicitOrModel(playerOptions.getCollisionHeightOverride(), modelHeight, playerOptions.getCollisionHeightScale(), playerOptions.getMinCollisionHeight());
        Vector3f center = bounds.getCenter().clone();
        Vector3f extent = new Vector3f(bounds.getXExtent(), bounds.getYExtent(), bounds.getZExtent());
        return new PlayerCollisionProfile(sourceName(sourceSpatial), radius, height, playerOptions.getMass(), center, extent);
    }

    private PlayerCollisionProfile fallbackCollisionProfile(String source) {
        float radius = Math.max(playerOptions.getMinCollisionRadius(), playerOptions.getCollisionRadiusOverride() > 0f ? playerOptions.getCollisionRadiusOverride() : 0.35f);
        float height = Math.max(playerOptions.getMinCollisionHeight(), playerOptions.getCollisionHeightOverride() > 0f ? playerOptions.getCollisionHeightOverride() : 1.8f);
        return new PlayerCollisionProfile(source == null ? "fallback" : source, radius, height, playerOptions.getMass(), Vector3f.ZERO, new Vector3f(radius, height * 0.5f, radius));
    }

    private float explicitOrModel(float explicitValue, float modelValue, float modelScale, float minimum) {
        if (explicitValue > 0f) {
            return Math.max(minimum, explicitValue);
        }
        return Math.max(minimum, modelValue * modelScale);
    }

    private String sourceName(Spatial sourceSpatial) {
        if (collisionSpatial == null) {
            return "model-bounds:" + nullSafeName(sourceSpatial);
        }
        return "collision-node:" + nullSafeName(sourceSpatial);
    }

    private String nullSafeName(Spatial spatial) {
        return spatial == null || spatial.getName() == null ? "unnamed" : spatial.getName();
    }

    private BoundingBox boundsOf(Spatial spatial) {
        spatial.updateModelBound();
        spatial.updateGeometricState();
        BoundingVolume bound = spatial.getWorldBound();
        return bound instanceof BoundingBox box ? box : null;
    }

    private Geometry findFirstGeometry(Spatial spatial) {
        if (spatial instanceof Geometry geometry) {
            return geometry;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Geometry geometry = findFirstGeometry(child);
                if (geometry != null) {
                    return geometry;
                }
            }
        }
        return null;
    }

    private Spatial findNamedSpatial(Spatial spatial, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.equals(spatial.getName())) {
            return spatial;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Spatial found = findNamedSpatial(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public void setCullHint(Spatial.CullHint cullHint) {
        playerSpatial.setCullHint(isVisualGuardedOff() ? Spatial.CullHint.Always : cullHint);
    }

    public void setShadowMode(RenderQueue.ShadowMode shadowMode) {
        playerSpatial.setShadowMode(shadowMode);
    }

    public boolean setVisualVisible(boolean visible, Spatial.CullHint visibleCullHint) {
        if (visible && isVisualGuardedOff()) {
            playerSpatial.setCullHint(Spatial.CullHint.Always);
            return false;
        }
        playerSpatial.setCullHint(visible ? visibleCullHint : Spatial.CullHint.Always);
        return visible;
    }

    public boolean isVisualGuardedOff() {
        return skinningGuardResult.disablesVisual();
    }

    public String getVisualGuardReason() {
        return skinningGuardResult.summary();
    }

    public Spatial getPlayerSpatial() { return playerSpatial; }
    public Geometry getPlayerGeometry() { return playerGeometry; }
    public Spatial getCollisionSpatial() { return collisionSpatial; }
    public PlayerCollisionProfile getCollisionProfile() { return collisionProfile; }
    public int getSanitizedTangentGeometryCount() { return sanitizedTangentGeometryCount; }
    public int getSanitizedTangentBufferCount() { return sanitizedTangentBufferCount; }
    public int getSanitizedNormalMapCount() { return sanitizedNormalMapCount; }
    public PlayerSkinningGuard.Result getSkinningGuardResult() { return skinningGuardResult; }
    public PlayerSkinningGuard.RepairResult getSkinningRepairResult() { return skinningRepairResult; }
}
