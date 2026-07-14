package org.takesome.frozenlands.engine.core;

import org.takesome.frozenlands.engine.EngineApplicationHost;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.modules.ModuleCommand;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;
import org.takesome.frozenlands.engine.ui.screen.UiScreenModel;

import java.util.Map;

final class CoreUiScreenCommands {
    private final EngineContext context;

    CoreUiScreenCommands(EngineContext context) {
        this.context = context;
    }

    void register(Map<String, ModuleCommand> commands) {
        commands.put("ui.screen.show", ModuleCommand.of(
                "ui.screen.show",
                "Show an application-hosted UI screen.",
                this::show
        ));
        commands.put("ui.screen.update", ModuleCommand.of(
                "ui.screen.update",
                "Update an application-hosted UI screen.",
                this::update
        ));
        commands.put("ui.screen.hide", ModuleCommand.of(
                "ui.screen.hide",
                "Hide an application-hosted UI screen.",
                this::hide
        ));
    }

    private Map<String, Object> show(Map<String, Object> args) {
        UiScreenModel model = uiScreenModel(args);
        host().showUiScreen(model);
        return result(model);
    }

    private Map<String, Object> update(Map<String, Object> args) {
        UiScreenModel model = uiScreenModel(args);
        host().updateUiScreen(model);
        return result(model);
    }

    private Map<String, Object> hide(Map<String, Object> args) {
        String id = RuntimeMaps.string(args, "id", RuntimeMaps.string(args, "screen", UiScreenModel.LOADING));
        host().hideUiScreen(id);
        return RuntimeMaps.result("screen", id);
    }

    private UiScreenModel uiScreenModel(Map<String, Object> args) {
        String id = RuntimeMaps.string(args, "id", RuntimeMaps.string(args, "screen", UiScreenModel.LOADING));
        String stage = RuntimeMaps.string(args, "stage", RuntimeMaps.string(args, "message", ""));
        float progress = RuntimeMaps.floating(args, "progress", 0f);
        Map<String, Object> properties = RuntimeMaps.map(args, "properties");
        return new UiScreenModel(id, stage, progress, properties);
    }

    private EngineApplicationHost host() {
        if (context instanceof Kernel kernel) {
            return kernel.applicationHost();
        }
        throw new IllegalStateException("Engine context does not expose an application host");
    }

    private Map<String, Object> result(UiScreenModel model) {
        Map<String, Object> result = RuntimeMaps.result("screen", model.id());
        result.put("stage", model.stage());
        result.put("progress", model.progress());
        return result;
    }
}
