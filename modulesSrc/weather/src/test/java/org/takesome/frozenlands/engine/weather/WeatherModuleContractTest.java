package org.takesome.frozenlands.engine.weather;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeatherModuleContractTest {
    @Test
    void exposesCanonicalWeatherApiAndRetainsCompatibilityAliases() {
        WeatherModule module = new WeatherModule();
        Set<String> commands = module.commands().keySet();

        assertTrue(commands.containsAll(Set.of(
                "status",
                "set",
                "preset.set",
                "preset.list",
                "intensity.set",
                "intensity.get",
                "precipitation.start",
                "precipitation.stop",
                "wind.set"
        )));
        assertTrue(commands.containsAll(Set.of(
                "snow_start",
                "snow_stop",
                "snow_light",
                "snow_default",
                "snow_blizzard",
                "wind"
        )));
        assertEquals(
                Set.of("engine.core", "engine.sky", "engine.world", "engine.player"),
                Set.copyOf(module.dependencies())
        );
    }
}
