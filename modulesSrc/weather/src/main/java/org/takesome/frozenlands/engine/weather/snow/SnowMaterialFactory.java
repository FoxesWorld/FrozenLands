package org.takesome.frozenlands.engine.weather.snow;

import org.takesome.frozenlands.engine.assets.AssetLibrary;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;

final class SnowMaterialFactory {
    private SnowMaterialFactory() {
    }

    static Material create(AssetLibrary assets, String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            throw new IllegalStateException(
                    "Required weather snow asset path is not configured: weather.snow.texture"
            );
        }
        Material material = assets.createMaterial("Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        material.setColor("Color", ColorRGBA.White);
        Texture texture = assets.texture(texturePath);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }
}
