package org.takesome.frozenlands.engine.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphicsQualityProfileTest {
    @Test
    void rejectsInvalidTextureAnisotropy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsQualityProfile.Textures(true, 17)
        );
    }

    @Test
    void rejectsNonPowerOfTwoShadowMaps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsQualityProfile.Shadows(
                        3000,
                        3,
                        140f,
                        0.5f,
                        3
                )
        );
    }

    @Test
    void rejectsInvalidDistancesAndMultipliers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsQualityProfile.Terrain(0f, 4, 1f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsQualityProfile.Particles(3f, 100f)
        );
    }
}
