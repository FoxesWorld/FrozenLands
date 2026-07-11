package org.takesome.frozenlands.engine.quality;

import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.takesome.frozenlands.engine.providers.ProviderArguments.string;
import static org.takesome.frozenlands.engine.providers.ProviderResponses.ok;

public final class GraphicsQualityModule implements EngineModule {
    public static final String ID = "engine.quality";

    private final GraphicsQualityService quality;
    private final QualityDiagnosticsService diagnostics;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public GraphicsQualityModule(
            GraphicsQualityService quality,
            QualityDiagnosticsService diagnostics
    ) {
        this.quality = quality;
        this.diagnostics = diagnostics;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Centralized graphics quality profiles and runtime diagnostics.";
    }

    @Override
    public List<String> dependencies() {
        return List.of("engine.core", "engine.assets", "engine.shaders");
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of(
                "status",
                "Return the complete runtime quality dashboard.",
                arguments -> diagnostics.status()
        ));
        commands.put("profile.status", ModuleCommand.of(
                "profile.status",
                "Return the active quality profile and target state.",
                arguments -> quality.status()
        ));
        commands.put("preset.list", ModuleCommand.of(
                "preset.list",
                "List configured graphics presets.",
                arguments -> ok(
                        "presets",
                        quality.presets().stream().map(Enum::name).toList()
                )
        ));
        commands.put("preset.apply", ModuleCommand.of(
                "preset.apply",
                "Apply LOW, MEDIUM, HIGH or ULTRA.",
                arguments -> {
                    GraphicsPreset preset = GraphicsPreset.parse(
                            string(arguments, "preset", "")
                    );
                    return ok("profile", quality.apply(preset).toMap());
                }
        ));
        commands.put("custom.apply", ModuleCommand.of(
                "custom.apply",
                "Apply validated custom overrides on top of the active profile.",
                arguments -> ok(
                        "profile",
                        quality.applyCustom(arguments).toMap()
                )
        ));
    }
}
