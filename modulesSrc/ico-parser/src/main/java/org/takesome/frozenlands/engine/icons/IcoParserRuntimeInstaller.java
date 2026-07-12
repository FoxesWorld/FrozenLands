package org.takesome.frozenlands.engine.icons;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;

public final class IcoParserRuntimeInstaller implements EngineRuntimeInstaller {
    @Override
    public int priority() {
        return 900;
    }

    @Override
    public String id() {
        return IcoParserModule.MODULE_ID;
    }

    @Override
    public void install(EngineContext context) {
        IcoFileParser parser = new IcoFileParser();
        EngineIconLoader loader = new EngineIconLoader(parser);
        IcoParserModule module = new IcoParserModule(parser, loader);
        context.getModuleRegistry().register(module, context);
        context.registerService(IcoFileParser.class, parser);
        context.registerService(EngineIconLoader.class, loader);
        context.registerService(IcoParserModule.LOADER_SERVICE_ID, EngineIconLoader.class, loader);
    }
}
