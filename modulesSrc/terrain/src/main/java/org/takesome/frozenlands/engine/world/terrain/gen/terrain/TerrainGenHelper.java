package org.takesome.frozenlands.engine.world.terrain.gen.terrain;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.HeightfieldCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainGrid;
import com.jme3.terrain.geomipmap.TerrainGridListener;
import com.jme3.terrain.geomipmap.TerrainGridLodControl;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.geomipmap.lodcalc.DistanceLodCalculator;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.gameplay.GrindableCollisionProxyControl;
import org.takesome.frozenlands.engine.world.terrain.TerrainRuntimeSettings;
import org.takesome.frozenlands.engine.world.terrain.chunk.TerrainChunkTracker;
import org.takesome.frozenlands.engine.world.terrain.gen.tree.TreeGen;

import java.util.List;
import java.util.Map;

public class TerrainGenHelper {
    private final EngineContext kernelInterface;
    private final TerrainQuad terrain;
    private final TerrainChunkTracker chunkTracker;
    private final TerrainRuntimeSettings settings;

    public TerrainGenHelper(EngineContext kernelInterface, TerrainQuad terrain, TerrainChunkTracker chunkTracker) {
        this.kernelInterface = kernelInterface;
        this.terrain = terrain;
        this.chunkTracker = chunkTracker;
        this.settings = new TerrainRuntimeSettings();
    }

    void setupScale() {
        terrain.setLocalScale(settings.scaleX(), settings.scaleY(), settings.scaleZ());
    }

    void setupPosition() {
        terrain.setLocalTranslation(0, 0, 0);
    }

    void setUpLODControl() {
        TerrainLodControl control = new TerrainGridLodControl(this.terrain, kernelInterface.getCamera());
        control.setLodCalculator(new DistanceLodCalculator(settings.lodPatchSize(), settings.lodMultiplier()));
        this.terrain.addControl(control);
    }

    void setUpCollision() {
        TreeGen treeGen = new TreeGen(kernelInterface);
        ((TerrainGrid) terrain).addListener(new TerrainGridListener() {
            @Override
            public void gridMoved(Vector3f newCenter) {
            }

            @Override
            public void tileAttached(Vector3f cell, TerrainQuad quad) {
                chunkTracker.tileAttached(cell, quad);
                publishTerrainEvent(settings.tileAttachedTopic(), cell, quad, "attached");
                installTileRuntime(cell, quad, treeGen, "attached");
            }

            @Override
            public void tileDetached(Vector3f cell, TerrainQuad quad) {
                chunkTracker.tileDetached(cell, quad);
                publishTerrainEvent(settings.tileDetachedTopic(), cell, quad, "detached");
                uninstallTileRuntime(quad);
            }
        });
        installExistingTileRuntime(treeGen);
    }

    private void installExistingTileRuntime(TreeGen treeGen) {
        for (Spatial child : terrain.getChildren()) {
            if (child instanceof TerrainQuad quad) {
                installTileRuntime(child.getWorldTranslation(), quad, treeGen, "existing");
            }
        }
    }

    private void installTileRuntime(Vector3f cell, TerrainQuad quad, TreeGen treeGen, String reason) {
        replaceTerrainCollision(cell, quad, reason);
        treeGen.positionTrees(quad);
        kernelInterface.getLogger().debug("Terrain tile runtime installed reason={} quad={}", reason, quad.getName());
    }

    private void replaceTerrainCollision(Vector3f cell, TerrainQuad quad, String reason) {
        RigidBodyControl existingControl = quad.getControl(RigidBodyControl.class);
        if (existingControl != null) {
            kernelInterface.getBulletAppState().getPhysicsSpace().remove(existingControl);
            quad.removeControl(RigidBodyControl.class);
        }

        quad.updateGeometricState();
        Vector3f collisionScale = resolveCollisionScale(quad);
        CollisionShape shape = createTerrainCollisionShape(quad, collisionScale);
        RigidBodyControl control = new RigidBodyControl(shape, 0);
        control.setApplyScale(false);
        quad.addControl(control);
        kernelInterface.getBulletAppState().getPhysicsSpace().add(control);
        chunkTracker.tileCollisionInstalled(cell, quad);

        if (settings.terrainCollisionLogReady()) {
            kernelInterface.getLogger().info(
                    "Terrain collision ready mode={} reason={} quad={} cell={} world={} localScale={} worldScale={} collisionScale={} physics={} samples={}",
                    settings.terrainCollisionMode(),
                    reason,
                    quad.getName(),
                    cell,
                    quad.getWorldTranslation(),
                    quad.getLocalScale(),
                    quad.getWorldScale(),
                    collisionScale,
                    control.getPhysicsLocation(null),
                    quad.getHeightMap() == null ? 0 : quad.getHeightMap().length
            );
        }
        publishTerrainEvent(settings.tileCollisionReadyTopic(), cell, quad, reason);
        publishTerrainEvent(settings.collisionReadyTopic(), cell, quad, reason);
    }

    private CollisionShape createTerrainCollisionShape(TerrainQuad quad, Vector3f collisionScale) {
        // CollisionShapeFactory.createMeshShape(TerrainQuad) internally creates a
        // HeightfieldCollisionShape with quad.getLocalScale(). TerrainGrid tiles
        // inherit their scale from the grid, so their local scale is identity and
        // that path leaves wide gaps between physics tiles. Build the heightfield
        // explicitly with the resolved world scale instead.
        return new HeightfieldCollisionShape(quad.getHeightMap(), collisionScale);
    }

    static Vector3f resolveCollisionScale(TerrainQuad quad) {
        quad.updateGeometricState();
        Vector3f worldScale = quad.getWorldScale();
        if (worldScale.x <= 0f || worldScale.y <= 0f || worldScale.z <= 0f
                || !Float.isFinite(worldScale.x)
                || !Float.isFinite(worldScale.y)
                || !Float.isFinite(worldScale.z)) {
            throw new IllegalStateException("Invalid terrain collision scale: " + worldScale);
        }
        return worldScale.clone();
    }

    private void publishTerrainEvent(String topic, Vector3f cell, TerrainQuad quad, String reason) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        Vector3f safeCell = cell == null ? quad.getWorldTranslation() : cell;
        kernelInterface.getModuleRegistry().publishLiveEvent(topic, Map.of(
                "ready", true,
                "reason", reason,
                "quad", quad.getName(),
                "cellX", safeCell.x,
                "cellY", safeCell.y,
                "cellZ", safeCell.z,
                "loadedChunks", chunkTracker.getLoadedChunkCount()
        ));
    }

    private void uninstallTileRuntime(TerrainQuad quad) {
        RigidBodyControl terrainControl = quad.getControl(RigidBodyControl.class);
        if (terrainControl != null) {
            kernelInterface.getBulletAppState().getPhysicsSpace().remove(terrainControl);
            quad.removeControl(RigidBodyControl.class);
        }

        List<Spatial> quadAssets = quad.getUserData(TreeGen.QUAD_ASSET_USER_DATA);
        if (quadAssets == null || quadAssets.isEmpty()) {
            quadAssets = quad.getUserData(TreeGen.LEGACY_QUAD_FOREST_USER_DATA);
        }
        if (quadAssets == null || quadAssets.isEmpty()) {
            return;
        }

        for (Spatial assetNode : quadAssets) {
            RigidBodyControl treeControl = assetNode.getControl(RigidBodyControl.class);
            if (treeControl != null) {
                kernelInterface.getBulletAppState().getPhysicsSpace().remove(treeControl);
                assetNode.removeControl(RigidBodyControl.class);
            }
            GrindableCollisionProxyControl proxyControl = assetNode.getControl(GrindableCollisionProxyControl.class);
            if (proxyControl != null) {
                proxyControl.detachProxy();
                assetNode.removeControl(proxyControl);
            }
            assetNode.removeFromParent();
            kernelInterface.getLogger().debug("Detached terrain asset {} at {}", assetNode.hashCode(), assetNode.getLocalTranslation());
        }
    }
}
