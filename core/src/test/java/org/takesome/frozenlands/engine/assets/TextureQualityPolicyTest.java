package org.takesome.frozenlands.engine.assets;

import com.jme3.texture.Texture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureQualityPolicyTest {
    @Test
    void terrainProfileUsesConfiguredMipmapsAndAnisotropy() {
        TextureQualityPolicy policy = new TextureQualityPolicy();
        policy.configure(true, 8);

        AssetLibrary.TextureOptions options = policy.options(TextureProfile.TERRAIN);

        assertTrue(options.generateMips());
        assertEquals(8, options.anisotropy());
        assertEquals(Texture.WrapMode.Repeat, options.wrapMode());
        assertEquals(Texture.MinFilter.Trilinear, options.minFilter());
    }

    @Test
    void uiAndDataProfilesIgnoreExpensiveWorldFiltering() {
        TextureQualityPolicy policy = new TextureQualityPolicy();
        policy.configure(true, 16);

        AssetLibrary.TextureOptions ui = policy.options(TextureProfile.UI);
        AssetLibrary.TextureOptions data = policy.options(TextureProfile.DATA);

        assertFalse(ui.generateMips());
        assertEquals(0, ui.anisotropy());
        assertEquals(Texture.WrapMode.EdgeClamp, ui.wrapMode());
        assertFalse(data.flipY());
        assertEquals(Texture.MinFilter.NearestNoMipMaps, data.minFilter());
    }

    @Test
    void rejectsUnsupportedAnisotropy() {
        TextureQualityPolicy policy = new TextureQualityPolicy();
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.configure(true, 17)
        );
    }
}
