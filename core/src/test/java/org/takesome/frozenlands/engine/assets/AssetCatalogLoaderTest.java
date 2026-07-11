package org.takesome.frozenlands.engine.assets;

import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetCatalogLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsTypedAliasesAndPreservesMetadata() throws IOException {
        Path catalogPath = temporaryDirectory.resolve("assets.json");
        Files.writeString(catalogPath, """
                {
                  "models": [
                    {
                      "id": "frozenlands.tools.axe",
                      "path": "models/tools/axe/axe.gltf",
                      "category": "tool",
                      "preloadGroup": "player"
                    }
                  ],
                  "textures": [
                    {
                      "id": "weather.snowflake",
                      "path": "textures/snowflake.png",
                      "profile": "PARTICLE"
                    }
                  ]
                }
                """);

        AssetLibrary assets = new AssetLibrary(
                new DesktopAssetManager(false),
                LoggerFactory.getLogger(AssetCatalogLoaderTest.class)
        );
        AssetCatalogLoader loader = new AssetCatalogLoader(
                assets,
                new ModuleIndexCatalog(temporaryDirectory)
        );

        AssetCatalogLoader.LoadResult result = loader.load(
                catalogPath.toString(),
                Map.of(
                        "models", AssetLibrary.Type.MODEL,
                        "textures", AssetLibrary.Type.TEXTURE
                ),
                Map.of("provider", "test.assets")
        );

        assertEquals(1, result.models());
        assertEquals(1, result.textures());
        assertEquals(2, result.total());
        assertEquals(
                "models/tools/axe/axe.gltf",
                assets.resolve(AssetLibrary.Type.MODEL, "frozenlands.tools.axe")
        );
        assertEquals(
                "textures/snowflake.png",
                assets.resolve(AssetLibrary.Type.TEXTURE, "weather.snowflake")
        );
        assertTrue(assets.registered(
                AssetLibrary.Type.MODEL,
                "FROZENLANDS.TOOLS.AXE"
        ));
        assertEquals(
                "test.assets",
                assets.catalog(AssetLibrary.Type.MODEL)
                        .get(0)
                        .get("metadata") instanceof Map<?, ?> metadata
                        ? metadata.get("provider")
                        : null
        );
    }
}
