package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.assets.AssetLibrary;

import java.util.Map;
import java.util.Objects;

final class AssetQualityTarget implements GraphicsQualityTarget {
    private final AssetLibrary assets;

    AssetQualityTarget(AssetLibrary assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @Override
    public String id() {
        return "assets";
    }

    @Override
    public Map<String, Object> apply(GraphicsQualityProfile profile) {
        assets.configureTextureQuality(
                profile.textures().mipmaps(),
                profile.textures().anisotropy()
        );
        return assets.textureQualityStatus();
    }

    @Override
    public Map<String, Object> status() {
        return assets.textureQualityStatus();
    }
}
