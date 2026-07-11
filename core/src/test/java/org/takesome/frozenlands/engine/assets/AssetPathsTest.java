package org.takesome.frozenlands.engine.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetPathsTest {
    @Test
    void normalizesPortableVirtualPaths() {
        assertEquals(
                "models/tools/axe/axe.gltf",
                AssetPaths.canonicalPath("./models\\tools//axe/axe.gltf")
        );
    }

    @Test
    void canonicalizesAssetIdsCaseInsensitively() {
        assertEquals(
                "frozenlands.tools.axe",
                AssetPaths.canonicalId("  FrozenLands.Tools.Axe  ")
        );
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetPaths.canonicalPath("models/../secrets.bin")
        );
    }

    @Test
    void rejectsDiskPathsAndUris() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetPaths.canonicalPath("C:/assets/model.gltf")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetPaths.canonicalPath("file:///assets/model.gltf")
        );
    }

    @Test
    void rejectsBlankReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetPaths.canonicalPath("  ")
        );
    }
}
