package org.takesome.frozenlands.engine.weather;

import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeatherSkyBridgeTest {
    @Test
    void appliesPresetThroughLowLevelSkyAdapterAndPublishesResult() {
        WeatherTestContext context = new WeatherTestContext();
        AtomicReference<Map<String, Object>> applied = new AtomicReference<>(Map.of());
        context.modules().register(fakeSky(applied), context);
        WeatherSkyBridge bridge = new WeatherSkyBridge(context);

        context.modules().publishEvent(WeatherEvents.WEATHER_PRESET_CHANGED, Map.of(
                "preset", "OVERCAST",
                "transitionSeconds", 6f,
                "revision", 4L
        ));

        assertEquals("OVERCAST", applied.get().get("preset"));
        assertEquals(6f, ((Number) applied.get().get("seconds")).floatValue(), 0.0001f);
        Map<String, Object> event = context.modules().getEventBus().latest(WeatherEvents.WEATHER_SKY_APPLIED);
        assertFalse(event.isEmpty());
        assertEquals("OVERCAST", payload(event).get("preset"));

        Map<String, Object> presets = bridge.listPresets();
        assertEquals(List.of("CLEAR", "OVERCAST", "STORM"), presets.get("presets"));
        bridge.close();
    }

    @Test
    void blankPresetDoesNotTouchSky() {
        WeatherTestContext context = new WeatherTestContext();
        AtomicReference<Map<String, Object>> applied = new AtomicReference<>(Map.of());
        context.modules().register(fakeSky(applied), context);
        WeatherSkyBridge bridge = new WeatherSkyBridge(context);

        context.modules().publishEvent(WeatherEvents.WEATHER_PRESET_CHANGED, Map.of(
                "preset", "",
                "transitionSeconds", 4f
        ));

        assertTrue(applied.get().isEmpty());
        assertTrue(context.modules().getEventBus().latest(WeatherEvents.WEATHER_SKY_APPLIED).isEmpty());
        bridge.close();
    }

    private EngineModule fakeSky(AtomicReference<Map<String, Object>> applied) {
        Map<String, ModuleCommand> commands = new LinkedHashMap<>();
        commands.put("preset.set", ModuleCommand.of("preset.set", "test preset setter", args -> {
            applied.set(new LinkedHashMap<>(args));
            return Map.of("ok", true, "preset", args.get("preset"));
        }));
        commands.put("preset.list", ModuleCommand.of(
                "preset.list",
                "test preset list",
                args -> Map.of("ok", true, "presets", List.of("CLEAR", "OVERCAST", "STORM"))
        ));
        return new EngineModule() {
            @Override public String id() { return "engine.sky"; }
            @Override public Map<String, ModuleCommand> commands() { return commands; }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object value = event.get("payload");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
