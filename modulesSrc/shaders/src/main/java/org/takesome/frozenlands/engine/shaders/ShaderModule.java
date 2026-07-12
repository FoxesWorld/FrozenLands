package org.takesome.frozenlands.engine.shaders;

import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderModule implements EngineModule {
    public static final String ID = "engine.shaders";

    private final Shaders shaders;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public ShaderModule(Shaders shaders) {
        this.shaders = shaders;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Centralized shader, shading and post-processing pipeline control";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of("status", "Return centralized shader pipeline status", args -> shaders.status()));
        commands.put("setEnabled", ModuleCommand.of("setEnabled", "Enable or disable whole shader pipeline", this::setPipelineEnabled));
        commands.put("effects", ModuleCommand.of("effects", "List shader pipeline effects", args -> result("effects", shaders.effectStatuses())));
        commands.put("effect.get", ModuleCommand.of("effect.get", "Return one shader effect status", this::effectGet));
        commands.put("effect.set", ModuleCommand.of("effect.set", "Enable or disable one shader effect", this::effectSet));
        commands.put("shadowSettings", ModuleCommand.of("shadowSettings", "Return current shadow settings", args -> shadowSettings()));
        commands.put("shadowSettings.set", ModuleCommand.of("shadowSettings.set", "Update shadow settings", args -> {
            shaders.applyShadowSettings(args);
            return shadowSettings();
        }));
        commands.put("solarShadows.status", ModuleCommand.of(
                "solarShadows.status",
                "Return solar shadow runtime status.",
                args -> result("solarShadows", shaders.solarShadowStatus())
        ));
        commands.put("solarShadows.set", ModuleCommand.of(
                "solarShadows.set",
                "Update solar shadow settings.",
                args -> {
                    shaders.applyShadowSettings(args);
                    return result("solarShadows", shaders.solarShadowStatus());
                }
        ));
        commands.put("solarShadows.preset", ModuleCommand.of(
                "solarShadows.preset",
                "Apply a bounded GPU-budget shadow preset.",
                args -> {
                    shaders.applyShadowSettings(Map.of(
                            "preset",
                            stringArg(args, "preset", "balanced")
                    ));
                    return result("solarShadows", shaders.solarShadowStatus());
                }
        ));
        commands.put("solarShadows.benchmark.start", ModuleCommand.of(
                "solarShadows.benchmark.start",
                "Start a 300-1000 frame solar shadow benchmark.",
                args -> result("benchmark", shaders.startSolarShadowBenchmark(
                        intArg(args, "frames", 600),
                        intArg(args, "warmupFrames", 60)
                ))
        ));
        commands.put("solarShadows.benchmark.status", ModuleCommand.of(
                "solarShadows.benchmark.status",
                "Return current solar shadow benchmark progress and statistics.",
                args -> result("benchmark", shaders.solarShadowBenchmarkStatus())
        ));
        commands.put("solarShadows.benchmark.cancel", ModuleCommand.of(
                "solarShadows.benchmark.cancel",
                "Cancel the active solar shadow benchmark.",
                args -> result("benchmark", shaders.cancelSolarShadowBenchmark())
        ));
    }

    private Map<String, Object> setPipelineEnabled(Map<String, Object> args) {
        boolean enabled = booleanArg(args, "enabled", true);
        shaders.setEnabled(enabled);
        return shaders.status();
    }

    private Map<String, Object> effectGet(Map<String, Object> args) {
        return result("effect", shaders.effectStatus(stringArg(args, "id", "")));
    }

    private Map<String, Object> effectSet(Map<String, Object> args) {
        String id = stringArg(args, "id", "");
        boolean enabled = booleanArg(args, "enabled", true);
        boolean applied = shaders.setEffectEnabled(id, enabled);
        Map<String, Object> result = result("effect", shaders.effectStatus(id));
        result.put("applied", applied);
        return result;
    }

    private Map<String, Object> shadowSettings() {
        return result("settings", shaders.getShadowSettings().toMap());
    }

    private String stringArg(Map<String, Object> args, String name, String fallback) {
        Object value = args == null ? null : args.get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intArg(Map<String, Object> args, String name, int fallback) {
        Object value = args == null ? null : args.get(name);
        return value instanceof Number number
                ? number.intValue()
                : value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private boolean booleanArg(Map<String, Object> args, String name, boolean fallback) {
        Object value = args == null ? null : args.get(name);
        return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Object> result(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put(key, value);
        return result;
    }
}
