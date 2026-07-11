package org.takesome.frozenlands.engine.providers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderArgumentsTest {
    @Test
    void readsAndTrimsStringArguments() {
        assertEquals(
                "frozenlands.tools.axe",
                ProviderArguments.string(
                        Map.of("asset", "  frozenlands.tools.axe  "),
                        "asset",
                        "fallback"
                )
        );
        assertEquals(
                "fallback",
                ProviderArguments.string(Map.of(), "asset", "fallback")
        );
    }

    @Test
    void readsBooleanValuesAndFallbacks() {
        assertTrue(ProviderArguments.bool(Map.of("enabled", true), "enabled", false));
        assertFalse(ProviderArguments.bool(Map.of("enabled", "false"), "enabled", true));
        assertTrue(ProviderArguments.bool(Map.of(), "enabled", true));
    }

    @Test
    void prefersAssetReferenceAndFallsBackToPath() {
        assertEquals(
                "catalog.id",
                ProviderArguments.reference(Map.of(
                        "asset", "catalog.id",
                        "path", "models/fallback.gltf"
                ))
        );
        assertEquals(
                "models/fallback.gltf",
                ProviderArguments.reference(Map.of("path", "models/fallback.gltf"))
        );
    }

    @Test
    void rejectsMissingRequiredReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderArguments.reference(Map.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderArguments.requiredString(Map.of(), "id")
        );
    }
}
