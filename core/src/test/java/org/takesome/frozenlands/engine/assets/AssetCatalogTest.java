package org.takesome.frozenlands.engine.assets;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetCatalogTest {
    private final AssetCatalog catalog = new AssetCatalog(
            LoggerFactory.getLogger(AssetCatalogTest.class)
    );

    @Test
    void resolvesAliasesWithoutCaseSensitivity() {
        catalog.registerModel(
                "FrozenLands.Tools.Axe",
                "models/tools/axe/axe.gltf",
                Map.of("category", "tool")
        );

        assertEquals(
                "models/tools/axe/axe.gltf",
                catalog.resolve(AssetLibrary.Type.MODEL, "frozenlands.tools.axe")
        );
        assertTrue(catalog.registered(
                AssetLibrary.Type.MODEL,
                "FROZENLANDS.TOOLS.AXE"
        ));
    }

    @Test
    void returnsDirectVirtualPathsWhenNoAliasExists() {
        assertEquals(
                "textures/world/ice.png",
                catalog.resolve(
                        AssetLibrary.Type.TEXTURE,
                        "textures\\world\\ice.png"
                )
        );
        assertFalse(catalog.registered(
                AssetLibrary.Type.TEXTURE,
                "textures/world/ice.png"
        ));
    }

    @Test
    void exposesStableTypedMetadataSnapshots() {
        catalog.registerTexture(
                "weather.snowflake",
                "textures/snowflake.png",
                Map.of("category", "weather", "profile", "PARTICLE")
        );

        List<Map<String, Object>> snapshot = catalog.snapshot(
                AssetLibrary.Type.TEXTURE
        );
        assertEquals(1, snapshot.size());
        assertEquals("weather.snowflake", snapshot.get(0).get("id"));
        assertEquals("TEXTURE", snapshot.get(0).get("type"));
        assertEquals(
                Map.of("category", "weather", "profile", "PARTICLE"),
                snapshot.get(0).get("metadata")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.get(0).put("source", "changed")
        );
    }

    @Test
    void validatesPathsDuringRegistration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.registerModel(
                        "invalid.model",
                        "../outside/model.gltf",
                        Map.of()
                )
        );
    }
}
