package org.takesome.frozenlands.engine.world.sky;

import jme3utilities.sky.command.SkyCommandIds;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkyRuntimeModule implements EngineModule {
    public static final String ID = "engine.sky";

    private final Sky sky;
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();

    public SkyRuntimeModule(Sky sky) {
        this.sky = sky;
        registerCommands();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "SkySimulation command bridge. Script commands submit intent; jME/SkyControl owns frame time and transitions.";
    }

    @Override
    public List<String> dependencies() {
        return List.of("engine.core");
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    private void registerCommands() {
        commands.put("status", ModuleCommand.of("status", "Return sky runtime status", args -> sky.status()));
        commands.put("command.execute", ModuleCommand.of("command.execute", "Execute a canonical SkySimulation command id", this::executeRaw));
        commands.put("atmosphere.setGradient", ModuleCommand.of("atmosphere.setGradient", "Start an atmosphere gradient transition", this::setGradient));
        commands.put("preset.set", ModuleCommand.of("preset.set", "Apply a low-level sky preset for engine.weather.", this::setWeather));
        commands.put("preset.list", ModuleCommand.of("preset.list", "List low-level sky presets for engine.weather.", args -> sky.executeCommand(SkyCommandIds.weatherList)));
        commands.put("weather.set", ModuleCommand.of("weather.set", "Compatibility alias; use engine.weather.preset.set.", this::setWeather));
        commands.put("weather.list", ModuleCommand.of("weather.list", "Compatibility alias; use engine.weather.preset.list.", args -> sky.executeCommand(SkyCommandIds.weatherList)));
        commands.put("clock.setTime", ModuleCommand.of("clock.setTime", "Set time of day in hours", this::setClockTime));
        commands.put("environment.snapshot", ModuleCommand.of("environment.snapshot", "Return sky environment snapshot", args -> sky.executeCommand(SkyCommandIds.environmentSnapshot)));
    }

    private Map<String, Object> executeRaw(Map<String, Object> args) {
        String commandId = stringArg(args, "commandId", "command", "id");
        List<String> commandArgs = stringList(firstValue(args, "args", "arguments"));
        return sky.executeCommand(commandId, commandArgs.toArray(new String[0]));
    }

    private Map<String, Object> setGradient(Map<String, Object> args) {
        String style = stringArg(args, "style", "gradient", "id");
        String seconds = optionalStringArg(args, "0", "seconds", "duration", "durationSeconds");
        return sky.executeCommand(SkyCommandIds.atmosphereSetGradient, style, seconds);
    }

    private Map<String, Object> setWeather(Map<String, Object> args) {
        String weatherId = stringArg(args, "preset", "weather", "id", "style");
        String seconds = optionalStringArg(args, null, "seconds", "duration", "durationSeconds");
        return seconds == null
                ? sky.executeCommand(SkyCommandIds.weatherSet, weatherId)
                : sky.executeCommand(SkyCommandIds.weatherSet, weatherId, seconds);
    }

    private Map<String, Object> setClockTime(Map<String, Object> args) {
        String hour = stringArg(args, "hour", "time", "timeOfDay", "timeOfDayHours");
        return sky.executeCommand(SkyCommandIds.clockSetTime, hour);
    }

    private static Object firstValue(Map<String, Object> args, String... names) {
        if (args == null) {
            return null;
        }
        for (String name : names) {
            Object value = args.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringArg(Map<String, Object> args, String... names) {
        Object value = firstValue(args, names);
        if (value == null) {
            throw new IllegalArgumentException("Missing sky command argument: " + String.join("/", names));
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Empty sky command argument: " + String.join("/", names));
        }
        return text;
    }

    private static String optionalStringArg(Map<String, Object> args, String fallback, String... names) {
        Object value = firstValue(args, names);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(String.valueOf(java.lang.reflect.Array.get(value, i)));
            }
            return result;
        }
        result.add(String.valueOf(value));
        return result;
    }
}
