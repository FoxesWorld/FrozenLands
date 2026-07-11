package org.takesome.frozenlands.engine.providers.model;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetCatalogLoader;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.providers.EngineProviders;

import java.util.Map;

final class ModelCatalogLoader {
    private static final Map<String, AssetLibrary.Type> SECTIONS = Map.of(
            "models",
            AssetLibrary.Type.MODEL
    );

    private final EngineContext context;
    private final AssetCatalogLoader loader;

    ModelCatalogLoader(EngineContext context) {
        this.context = context;
        this.loader = new AssetCatalogLoader(context.assets());
    }

    int load(String path) {
        AssetCatalogLoader.LoadResult result = loader.load(
                path,
                SECTIONS,
                Map.of("provider", EngineProviders.MODEL_PROVIDER)
        );
        context.getLogger().info(
                "Model catalog registered path={} models={}",
                path,
                result.models()
        );
        context.getProviderRegistry().publishEvent(
                "provider.model.catalog.loaded",
                Map.of("path", path, "count", result.models())
        );
        return result.models();
    }
}
