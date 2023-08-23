package org.foxesworld.newgame.engine.providers.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.math.ColorRGBA;
import org.foxesworld.newgame.engine.KernelInterface;

public abstract class MaterialAbstract {
    private KernelInterface kernelInterface;
    private Material material;
    private MaterialDef materialDef;
    public abstract Material createMat(String path);
    public void setMaterialBoolean(String map, boolean val) {
        getMaterial().setBoolean(map, val);
    }
    public void setMaterialFloat(String map, float val) {
        getMaterial().setFloat(map, val);
    }
    public void setMaterialColor(String map, ColorRGBA color) {
        getMaterial().setColor(map, color);
    }

    public MaterialDef getMaterialDef() {
        return materialDef;
    }
    protected void initMaterial(String matDef) {
        materialDef = (MaterialDef) kernelInterface.getAssetManager().loadAsset(matDef);
        material = new Material(getMaterialDef());
    }
    public KernelInterface getKernelInterface() {
        return kernelInterface;
    }
    public void setAssetManager(KernelInterface kernelInterface) {
        this.kernelInterface = kernelInterface;
    }
    public Material getMaterial() {
        return material;
    }
}