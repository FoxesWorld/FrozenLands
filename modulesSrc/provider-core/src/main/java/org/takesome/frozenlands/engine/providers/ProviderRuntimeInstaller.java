package org.takesome.frozenlands.engine.providers;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ProviderModuleAdapter;
import org.takesome.frozenlands.engine.providers.material.MaterialProvider;
import org.takesome.frozenlands.engine.providers.model.ModelProvider;
import org.takesome.frozenlands.engine.providers.sound.SoundProvider;
import org.takesome.frozenlands.engine.providers.sound.SoundRegistry;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;

public final class ProviderRuntimeInstaller implements EngineRuntimeInstaller {
    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String id() {
        return "engine.providers.runtime";
    }

    @Override
    public void install(EngineContext context) {
        ProviderRegistry registry = context.getProviderRegistry();
        context.getModuleRegistry().register(EngineModule.descriptor("engine.providers", "Provider registry exposed to Lua bridge"), context);

        AssetLibraryProvider assetProvider = (AssetLibraryProvider) registry.register(new AssetLibraryProvider(context), context);
        context.getModuleRegistry().register(new ProviderModuleAdapter(assetProvider), context);
        context.registerService(AssetLibraryProvider.class, assetProvider);

        SoundProvider soundProvider = (SoundProvider) registry.register(new SoundProvider(context), context);
        context.getModuleRegistry().register(new ProviderModuleAdapter(soundProvider), context);
        context.registerService(SoundProvider.class, soundProvider);
        context.registerService("provider.sound.registry", SoundRegistry.class, soundProvider.registry());

        MaterialProvider materialProvider = (MaterialProvider) registry.register(new MaterialProvider(context), context);
        context.getModuleRegistry().register(new ProviderModuleAdapter(materialProvider), context);
        context.registerService(MaterialProvider.class, materialProvider);

        ModelProvider modelProvider = (ModelProvider) registry.register(new ModelProvider(context), context);
        context.getModuleRegistry().register(new ProviderModuleAdapter(modelProvider), context);
        context.registerService(ModelProvider.class, modelProvider);
    }
}
