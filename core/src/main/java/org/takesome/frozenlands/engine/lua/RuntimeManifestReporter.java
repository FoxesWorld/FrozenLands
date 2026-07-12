package org.takesome.frozenlands.engine.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takesome.frozenlands.engine.EngineContext;

import java.util.List;
import java.util.Map;

public final class RuntimeManifestReporter {
    private static final String FLAG = "frozenlands.runtimeManifest";
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeManifestReporter.class);

    private RuntimeManifestReporter() {
    }

    public static void reportIfRequested(EngineContext context) {
        if (!Boolean.getBoolean(FLAG)) {
            return;
        }

        Map<String, Object> manifest = new LuaProviderBridge(context).exportRuntimeManifest();
        Map<?, ?> providers = map(manifest.get("providers"));
        Map<?, ?> modules = map(manifest.get("modules"));

        LOGGER.info("RuntimeManifest providers={}", providers.keySet());
        LOGGER.info("RuntimeManifest modules={}", modules.keySet());
        reportModule(modules, "engine.core", List.of("status", "manifest", "modules", "providers", "call.module", "call.provider", "event.publish", "events.snapshot", "events.drain", "script.manifest", "script.list", "script.read", "script.run", "script.autorun", "console.execute", "console.help", "console.version", "console.commandsList", "console.complete", "task.pool.status", "task.list", "task.get", "task.cancel", "service.pool.status", "service.list", "operations.status", "operations.active", "runtime.game.start", "runtime.game.status", "ui.screen.show", "ui.screen.update", "ui.screen.hide"));
        reportModule(modules, "engine.save", List.of("snapshot", "save", "overwrite", "delete", "load", "list"));
        reportModule(modules, "engine.particles", List.of("status", "snow.enable", "snow.rate", "emit", "impact", "effect.get", "clear"));
        reportModule(modules, "engine.terrain", List.of("status", "chunks", "heightAt", "spawnLocation"));
        reportModule(modules, "engine.sky", List.of("status", "command.execute", "atmosphere.setGradient", "preset.set", "preset.list", "clock.setTime", "environment.snapshot"));
        reportModule(modules, "engine.weather", List.of("status", "set", "preset.set", "preset.list", "intensity.set", "intensity.get", "precipitation.start", "precipitation.stop", "wind.set"));
        reportModule(modules, "engine.shaders", List.of("status", "setEnabled", "effects", "effect.get", "effect.set", "shadowSettings", "shadowSettings.set", "solarShadows.status", "solarShadows.set"));
        reportModule(modules, "engine.sound", List.of("status", "load", "reload", "list.blocks", "list.events", "event.get", "registry.snapshot", "request", "play"));
        reportModule(modules, "engine.material", List.of("load", "list", "get"));
        reportModule(modules, "engine.model", List.of("load", "list", "detach"));
        reportModule(modules, "engine.icoParser", List.of("status", "inspect", "best"));
    }

    private static void reportModule(Map<?, ?> modules, String id, List<String> requiredCommands) {
        Object moduleObject = modules.get(id);
        if (!(moduleObject instanceof Map<?, ?> module)) {
            LOGGER.warn("RuntimeManifest missing module {}", id);
            return;
        }

        Map<?, ?> commands = map(module.get("commands"));
        LOGGER.info("RuntimeManifest module {} commands={}", id, commands.keySet());
        for (String command : requiredCommands) {
            if (!commands.containsKey(command)) {
                LOGGER.warn("RuntimeManifest missing command {}.{}", id, command);
            }
        }
    }

    private static Map<?, ?> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }
}
