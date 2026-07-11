package org.takesome.frozenlands.engine.weather;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;

public final class WeatherRuntimeInstaller implements EngineRuntimeInstaller {
    @Override
    public int priority() {
        return 700;
    }

    @Override
    public String id() {
        return WeatherModule.ID;
    }

    @Override
    public void install(EngineContext context) {
        context.getModuleRegistry().register(new WeatherModule(), context);
    }
}
