package org.takesome.frozenlands.engine.weather;

import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeatherIntensityTest {
    @Test
    void normalizesValuesAndExplicitPercentInput() {
        assertEquals(0f, WeatherIntensity.of(-4f).value(), 0.0001f);
        assertEquals(1f, WeatherIntensity.of(3f).value(), 0.0001f);
        assertEquals(0f, WeatherIntensity.of(Float.NaN).value(), 0.0001f);
        assertEquals(0.42f, WeatherIntensity.from(Map.of("intensity", 0.42f), null).value(), 0.0001f);
        assertEquals(0.65f, WeatherIntensity.from(Map.of("percent", 65), null).value(), 0.0001f);
        assertEquals(65, WeatherIntensity.from(Map.of("percent", 65), null).percent());
        assertFalse(WeatherIntensity.NONE.active());
        assertTrue(WeatherIntensity.FULL.active());
    }

    @Test
    void mapsIntensityToVisibleSnowWithoutReallocatingTheConfiguredCapacity() {
        assertEquals(0, SnowPrecipitationSystem.effectiveFlakeCount(1100, 0f));
        assertEquals(275, SnowPrecipitationSystem.effectiveFlakeCount(1100, 0.25f));
        assertEquals(550, SnowPrecipitationSystem.effectiveFlakeCount(1100, 0.5f));
        assertEquals(1100, SnowPrecipitationSystem.effectiveFlakeCount(1100, 1f));
        assertEquals(1100, SnowPrecipitationSystem.effectiveFlakeCount(1100, 8f));
        assertEquals(0, SnowPrecipitationSystem.effectiveFlakeCount(1100, -2f));
    }
}
