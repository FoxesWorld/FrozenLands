package org.takesome.frozenlands.engine.quality;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsQualityCatalogTest {
    @Test
    void loadsAllConfiguredPresetsAndHighDefault() {
        GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();

        assertEquals(GraphicsPreset.HIGH, catalog.defaultPreset());
        assertEquals(4, catalog.presets().size());
        assertTrue(catalog.presets().contains(GraphicsPreset.ULTRA));
        assertEquals(8, catalog.profile(GraphicsPreset.HIGH).textures().anisotropy());
    }

    @Test
    void customOverridesPreserveUntouchedSections() {
        GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();
        GraphicsQualityProfile high = catalog.profile(GraphicsPreset.HIGH);

        GraphicsQualityProfile custom = catalog.custom(
                high,
                Map.of(
                        "textures", Map.of("anisotropy", 4),
                        "particles", Map.of("densityMultiplier", 0.8)
                )
        );

        assertEquals(GraphicsPreset.CUSTOM, custom.preset());
        assertEquals(4, custom.textures().anisotropy());
        assertEquals(0.8f, custom.particles().densityMultiplier());
        assertEquals(high.shadows(), custom.shadows());
        assertEquals(high.terrain(), custom.terrain());
    }
}
