package org.takesome.frozenlands.engine.weather;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeatherControllerTest {
    @Test
    void appliesOneAtomicChangeAndEmitsTypedStateEvents() {
        WeatherTestContext context = new WeatherTestContext();
        WeatherRuntimeConfig config = new WeatherRuntimeConfig(
                true,
                WeatherIntensity.FULL,
                "",
                5f,
                new Vector3f(0.75f, 0f, -0.25f),
                SnowPrecipitationSystem.Config.defaultConfig()
        );
        WeatherController controller = new WeatherController(context, config);
        List<String> topics = new ArrayList<>();
        context.modules().getEventBus().subscribe(
                "weather.*",
                event -> topics.add(String.valueOf(event.get("topic"))),
                false
        );

        controller.publishInitialState();
        long initialRevision = controller.snapshot().revision();
        int stateEventsBefore = count(topics, WeatherEvents.WEATHER_STATE_CHANGED);

        context.modules().publishEvent(WeatherEvents.WEATHER_CHANGE_REQUESTED, Map.of(
                "preset", "STORM",
                "intensity", 0.72f,
                "active", true,
                "transitionSeconds", 8f,
                "wind", Map.of("x", 2.5f, "y", 0f, "z", -1.25f),
                "source", "junit",
                "reason", "test-change"
        ));

        WeatherState state = controller.snapshot();
        assertEquals(initialRevision + 1, state.revision());
        assertEquals("STORM", state.preset());
        assertEquals(0.72f, state.intensity().value(), 0.0001f);
        assertTrue(state.precipitationActive());
        assertEquals(8f, state.transitionSeconds(), 0.0001f);
        assertEquals(new Vector3f(2.5f, 0f, -1.25f), state.wind());
        assertEquals("junit", state.source());
        assertEquals("test-change", state.reason());

        assertEquals(stateEventsBefore + 1, count(topics, WeatherEvents.WEATHER_STATE_CHANGED));
        assertTrue(topics.contains(WeatherEvents.WEATHER_PRESET_CHANGED));
        assertTrue(topics.contains(WeatherEvents.WEATHER_INTENSITY_CHANGED));
        assertTrue(topics.contains(WeatherEvents.WEATHER_PRECIPITATION_CHANGED));
        assertTrue(topics.contains(WeatherEvents.WIND_CHANGED));

        int eventCount = topics.size();
        context.modules().publishEvent(WeatherEvents.WEATHER_CHANGE_REQUESTED, Map.of(
                "preset", "STORM",
                "intensity", 0.72f,
                "active", true,
                "transitionSeconds", 8f,
                "wind", Map.of("x", 2.5f, "y", 0f, "z", -1.25f)
        ));
        assertEquals(state.revision(), controller.snapshot().revision());
        assertEquals(eventCount + 1, topics.size(), "Only the request event itself should be recorded for a no-op");
        controller.close();
    }

    @Test
    void intensityAndPrecipitationRequestsAreIndependentButConsistent() {
        WeatherTestContext context = new WeatherTestContext();
        WeatherController controller = new WeatherController(context, WeatherRuntimeConfig.defaultConfig());

        context.modules().publishEvent(WeatherEvents.WEATHER_INTENSITY_REQUESTED, Map.of(
                "percent", 25,
                "source", "junit"
        ));
        assertEquals(0.25f, controller.snapshot().intensity().value(), 0.0001f);
        assertTrue(controller.snapshot().precipitationActive());

        context.modules().publishEvent(WeatherEvents.WEATHER_PRECIPITATION_REQUESTED, Map.of(
                "active", false
        ));
        assertFalse(controller.snapshot().precipitationActive());
        assertEquals(0.25f, controller.snapshot().intensity().value(), 0.0001f);

        context.modules().publishEvent(WeatherEvents.WEATHER_INTENSITY_REQUESTED, Map.of(
                "percent", 180
        ));
        assertEquals(1f, controller.snapshot().intensity().value(), 0.0001f);
        assertFalse(controller.snapshot().precipitationActive());
        controller.close();
    }

    @Test
    void translatesLegacyWeatherEventsWithoutFeedbackLoops() {
        WeatherTestContext context = new WeatherTestContext();
        WeatherController controller = new WeatherController(context, WeatherRuntimeConfig.defaultConfig());
        List<String> stateChanges = new ArrayList<>();
        context.modules().getEventBus().subscribe(
                WeatherEvents.WEATHER_STATE_CHANGED,
                event -> stateChanges.add(String.valueOf(event.get("id"))),
                false
        );

        context.modules().publishEvent(
                WeatherEvents.SNOW_DENSITY_CHANGED,
                Map.of("mode", "light", "running", true),
                "legacy.weather.script",
                Map.of()
        );
        assertEquals(0.30f, controller.snapshot().intensity().value(), 0.0001f);
        assertTrue(controller.snapshot().precipitationActive());
        assertEquals(1, stateChanges.size());

        context.modules().publishEvent(
                WeatherEvents.WIND_CHANGED,
                Map.of("x", 3f, "y", 0f, "z", -2f),
                "legacy.weather.script",
                Map.of()
        );
        assertEquals(new Vector3f(3f, 0f, -2f), controller.snapshot().wind());
        assertEquals(2, stateChanges.size());
        controller.close();
    }

    private int count(List<String> values, String target) {
        int count = 0;
        for (String value : values) {
            if (target.equals(value)) {
                count++;
            }
        }
        return count;
    }
}
