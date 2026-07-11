package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;
import org.takesome.frozenlands.engine.shaders.Shaders;

public final class GraphicsQualityRuntimeInstaller implements EngineRuntimeInstaller {
    @Override
    public int priority() {
        return 750;
    }

    @Override
    public String id() {
        return GraphicsQualityModule.ID;
    }

    @Override
    public void install(EngineContext context) {
        GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();
        GraphicsQualityService quality = new GraphicsQualityService(context, catalog);
        quality.registerTarget(new AssetQualityTarget(context.assets()));
        context.findService(Shaders.class)
                .ifPresent(shaders -> quality.registerTarget(new ShaderQualityTarget(shaders)));

        context.registerService(GraphicsQualityService.class, quality);
        context.getModuleRegistry().register(new GraphicsQualityModule(quality), context);
        quality.apply(catalog.defaultPreset());
    }
}
