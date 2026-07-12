package org.takesome.frozenlands.engine.save;

import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SaveModule implements EngineModule {
    public static final String ID = "engine.save";

    private final SaveManager saveManager;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public SaveModule(SaveManager saveManager) {
        this.saveManager = saveManager;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Game snapshot persistence";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("snapshot", ModuleCommand.of("snapshot", "Build an in-memory save snapshot", args -> saveManager.snapshot()));
        commands.put("save", ModuleCommand.of("save", "Write a save slot", args -> saveManager.save(slot(args))));
        commands.put("overwrite", ModuleCommand.of("overwrite", "Overwrite an existing save slot atomically", args -> saveManager.overwrite(slot(args))));
        commands.put("delete", ModuleCommand.of("delete", "Delete an existing save slot", args -> saveManager.delete(slot(args))));
        commands.put("load", ModuleCommand.of("load", "Load a save slot and restore runtime state", args -> saveManager.load(slot(args))));
        commands.put("list", ModuleCommand.of("list", "List save slots", args -> result("saves", saveManager.list())));
    }

    private String slot(Map<String, Object> args) {
        Object value = args == null ? null : args.get("slot");
        return value == null ? "quick" : String.valueOf(value);
    }

    private Map<String, Object> result(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put(key, value);
        return result;
    }
}
