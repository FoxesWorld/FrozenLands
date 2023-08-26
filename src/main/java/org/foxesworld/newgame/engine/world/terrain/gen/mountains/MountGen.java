package org.foxesworld.newgame.engine.world.terrain.gen.mountains;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import org.foxesworld.newgame.engine.KernelInterface;
import org.foxesworld.newgame.engine.config.Constants;

public class MountGen {

    private  KernelInterface kernelInterface;
    private AssetManager assetManager;
    private TerrainQuad distantTerrain;

    public  MountGen(KernelInterface kernelInterface){
        this.kernelInterface = kernelInterface;
        this.assetManager = kernelInterface.getAssetManager();
    }

    public TerrainQuad generateMountains() {
        Material matTerrain = kernelInterface.getMaterialManager().getMaterial("terrain");
        matTerrain.setFloat("Tex1Scale", 64f);
        matTerrain.setFloat("Tex2Scale", 64f);
        matTerrain.setFloat("Tex3Scale", 64f);

        AbstractHeightMap heightmap;
        Texture heightMapImage = assetManager.loadTexture("textures/terrain/horizon.png");
        heightmap = new ImageBasedHeightMap(heightMapImage.getImage());
        heightmap.load();
        heightmap.smooth(0.65f, 1);
        heightmap.flatten((byte) 2);

        int patchSize = 65;
        distantTerrain = new TerrainQuad("Distant Terrain", patchSize, 2049, heightmap.getHeightMap());

        distantTerrain.setMaterial(matTerrain);
        distantTerrain.setLocalTranslation(0, Constants.MOUNTAINS_HEIGHT_OFFSET, 0);
        distantTerrain.setLocalScale(6f, 19f, 6f);

        TerrainLodControl control = new TerrainLodControl(distantTerrain, kernelInterface.getCamera());
        distantTerrain.addControl(control);
        distantTerrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return distantTerrain;
    }


}
