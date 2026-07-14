package org.takesome.frozenlands.engine.ui.prelaunch;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreLaunchGraphicsContractTest {
    @Test
    void htmlExposesEveryCanonicalLaunchGraphicsControl() throws Exception {
        String resource = "org/takesome/frozenlands/engine/ui/prelaunch/prelaunch.html";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            String html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String id : List.of(
                    "quality-value",
                    "shadow-value",
                    "msaa-value",
                    "fxaa-toggle",
                    "ssao-toggle",
                    "bloom-toggle",
                    "dof-toggle",
                    "rays-toggle",
                    "shadows-toggle",
                    "fullscreen-toggle",
                    "vsync-toggle",
                    "center-toggle"
            )) {
                assertTrue(html.contains("id=\"" + id + "\""), id);
            }
        }
    }
}
