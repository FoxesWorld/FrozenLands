package org.takesome.frozenlands.engine.player.camera;

import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.player.PlayerOptions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraZoomControllerTest {
    private static final float EPSILON = 0.001f;

    @Test
    void animatesTowardWheelTargetInsteadOfSnapping() {
        CameraZoomController zoom = new CameraZoomController(options());

        float target = zoom.request(1f);
        zoom.update(0.016f);

        assertEquals(5.5f, target, EPSILON);
        assertTrue(zoom.currentDistance() > 5.0f);
        assertTrue(zoom.currentDistance() < 5.5f);
        assertTrue((Boolean) zoom.status().get("animating"));
    }

    @Test
    void clampsZoomToConfiguredRange() {
        CameraZoomController zoom = new CameraZoomController(options());

        for (int index = 0; index < 100; index++) {
            zoom.request(-1f);
        }
        assertEquals(1.5f, zoom.targetDistance(), EPSILON);
        assertTrue(zoom.targetAtMinimum());

        for (int index = 0; index < 100; index++) {
            zoom.request(1f);
        }
        assertEquals(8.0f, zoom.targetDistance(), EPSILON);
        assertFalse(zoom.targetAtMinimum());
    }

    @Test
    void preparesAnimatedThirdPersonZoomFromFirstPerson() {
        CameraZoomController zoom = new CameraZoomController(options());

        zoom.prepareFromFirstPerson();

        assertEquals(1.5f, zoom.currentDistance(), EPSILON);
        assertEquals(2.0f, zoom.targetDistance(), EPSILON);
        zoom.update(0.016f);
        assertTrue(zoom.currentDistance() > 1.5f);
        assertTrue(zoom.currentDistance() < 2.0f);
    }

    private PlayerOptions options() {
        return new PlayerOptions(Map.of(
                "model", "test.player",
                "camera", Map.of(
                        "thirdPersonDistance", 5.0f,
                        "zoomMinimumDistance", 1.5f,
                        "zoomMaximumDistance", 8.0f,
                        "zoomStep", 0.5f,
                        "zoomResponse", 12.0f
                )
        ));
    }
}
