package org.takesome.frozenlands.engine.config;

import com.jme3.system.AppSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraphicsLaunchSettingsTest {
    @Test
    void roundTripsIndependentGraphicsVariablesThroughAppSettings() {
        AppSettings settings = new AppSettings(true);
        GraphicsLaunchSettings.Snapshot expected = new GraphicsLaunchSettings.Snapshot(
                "HIGH",
                "QUALITY",
                8,
                true,
                true,
                false,
                true,
                false,
                true
        );

        GraphicsLaunchSettings.write(settings, expected);
        GraphicsLaunchSettings.Snapshot actual = GraphicsLaunchSettings.read(settings);

        assertEquals("HIGH", actual.graphicsPreset());
        assertEquals("QUALITY", actual.shadowPreset());
        assertEquals(8, actual.msaaSamples());
        assertTrue(actual.fxaa(), "FXAA must remain independently selectable when MSAA is enabled");
        assertTrue(actual.ssao());
        assertFalse(actual.bloom());
        assertTrue(actual.depthOfField());
        assertFalse(actual.lightRays());
        assertTrue(actual.shadows());
    }

    @Test
    void exposesCanonicalRuntimeMapAndNormalizesDisabledShadows() {
        AppSettings settings = new AppSettings(true);
        GraphicsLaunchSettings.write(settings, new GraphicsLaunchSettings.Snapshot(
                "medium",
                "cinematic",
                3,
                false,
                false,
                true,
                false,
                false,
                false
        ));

        Map<String, Object> values = GraphicsLaunchSettings.toMap(settings);

        assertEquals("MEDIUM", values.get("graphicsPreset"));
        assertEquals("OFF", values.get("shadowPreset"));
        assertEquals(0, values.get("msaaSamples"));
        assertEquals(false, values.get("shadows"));
    }
}
