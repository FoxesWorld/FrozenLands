package org.takesome.frozenlands.engine.assets;

import com.jme3.texture.Texture;

import java.util.LinkedHashMap;
import java.util.Map;

final class TextureQualityPolicy {
    private volatile boolean mipmaps;
    private volatile int anisotropy;

    void configure(boolean mipmaps, int anisotropy) {
        if (anisotropy < 0 || anisotropy > 16) {
            throw new IllegalArgumentException(
                    "Texture anisotropy must be between 0 and 16: " + anisotropy
            );
        }
        this.mipmaps = mipmaps;
        this.anisotropy = anisotropy;
    }

    AssetLibrary.TextureOptions options(TextureProfile profile) {
        TextureProfile safeProfile = profile == null ? TextureProfile.WORLD : profile;
        return switch (safeProfile) {
            case UI -> options(
                    true,
                    false,
                    0,
                    Texture.WrapMode.EdgeClamp,
                    Texture.MinFilter.BilinearNoMipMaps,
                    Texture.MagFilter.Bilinear
            );
            case CHARACTER -> filtered(true, null);
            case TERRAIN -> filtered(true, Texture.WrapMode.Repeat);
            case WORLD -> filtered(true, null);
            case PARTICLE -> options(
                    true,
                    false,
                    0,
                    Texture.WrapMode.EdgeClamp,
                    Texture.MinFilter.BilinearNoMipMaps,
                    Texture.MagFilter.Bilinear
            );
            case HEIGHTMAP, DATA -> options(
                    false,
                    false,
                    0,
                    Texture.WrapMode.EdgeClamp,
                    Texture.MinFilter.NearestNoMipMaps,
                    Texture.MagFilter.Nearest
            );
        };
    }

    Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mipmaps", mipmaps);
        result.put("anisotropy", anisotropy);
        return Map.copyOf(result);
    }

    private AssetLibrary.TextureOptions filtered(
            boolean flipY,
            Texture.WrapMode wrapMode
    ) {
        return options(
                flipY,
                mipmaps,
                anisotropy,
                wrapMode,
                mipmaps
                        ? Texture.MinFilter.Trilinear
                        : Texture.MinFilter.BilinearNoMipMaps,
                Texture.MagFilter.Bilinear
        );
    }

    private AssetLibrary.TextureOptions options(
            boolean flipY,
            boolean generateMips,
            int anisotropy,
            Texture.WrapMode wrapMode,
            Texture.MinFilter minFilter,
            Texture.MagFilter magFilter
    ) {
        return new AssetLibrary.TextureOptions(
                flipY,
                generateMips,
                anisotropy,
                wrapMode,
                minFilter,
                magFilter
        );
    }
}
