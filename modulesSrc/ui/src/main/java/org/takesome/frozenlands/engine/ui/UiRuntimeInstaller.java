package org.takesome.frozenlands.engine.ui;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;
import org.takesome.frozenlands.engine.ui.html.HtmlUiState;

public final class UiRuntimeInstaller implements EngineRuntimeInstaller {
    @Override
    public int priority() {
        return 860;
    }

    @Override
    public String id() {
        return UiRuntimeModule.ID;
    }

    @Override
    public void install(EngineContext context) {
        UiRuntimeModule module = new UiRuntimeModule(context);
        context.getModuleRegistry().register(module, context);
        context.registerService(UiRuntimeModule.class, module);
        context.appStateManager().attach(new HtmlUiState(context));
    }
}
