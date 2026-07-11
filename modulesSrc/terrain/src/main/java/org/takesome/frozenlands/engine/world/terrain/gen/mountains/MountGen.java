package org.takesome.frozenlands.engine.world.terrain.gen.mountains;

import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.providers.material.MaterialProvider;
import org.takesome.frozenlands.engine.world.terrain.TerrainRuntimeSettings;

public class MountGen {
    private final EngineContext kernelInterface;
    private final TerrainRuntimeSettings settings;
    private TerrainQuad distantTerrain;

    public MountGen(EngineContext kernelInterface) {
        this.kernelInterface = kernelInterface;
        this.settings = new TerrainRuntimeSettings();
    }

    public TerrainQuad generateMountains() {
        Material matTerrain = kernelInterface.requireService(MaterialProvider.class).getMaterial(settings.mountainMaterial());

        AbstractHeightMap heightmap;
        Texture heightMapImage = kernelInterface.assets().texture(settings.mountainHeightMap());
        heightmap = new ImageBasedHeightMap(heightMapImage.getImage());
        heightmap.load();
        heightmap.smooth(settings.mountainSmoothAmount(), settings.mountainSmoothRadius());
        heightmap.flatten(settings.mountainFlatten());

        distantTerrain = new TerrainQuad("Distant Terrain", settings.mountainPatchSize(), settings.mountainQuadSize(), heightmap.getHeightMap());

        distantTerrain.setMaterial(matTerrain);
        distantTerrain.setLocalTranslation(0, settings.mountainHeightOffset(), 0);
        distantTerrain.setLocalScale(settings.mountainScaleX(), settings.mountainScaleY(), settings.mountainScaleZ());

        TerrainLodControl control = new TerrainLodControl(distantTerrain, kernelInterface.getCamera());
        distantTerrain.addControl(control);
        distantTerrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return distantTerrain;
    }
}
