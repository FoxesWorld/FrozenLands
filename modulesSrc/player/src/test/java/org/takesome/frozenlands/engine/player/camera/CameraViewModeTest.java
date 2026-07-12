package org.takesome.frozenlands.engine.player.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraViewModeTest {
    @Test
    void standardToggleSwitchesFirstAndThirdPerson() {
        assertEquals(
                CameraViewMode.THIRD_PERSON,
                CameraViewMode.FIRST_PERSON.toggle()
        );
        assertEquals(
                CameraViewMode.FIRST_PERSON,
                CameraViewMode.THIRD_PERSON.toggle()
        );
        assertEquals(
                CameraViewMode.FIRST_PERSON,
                CameraViewMode.FRONT_PERSON.toggle()
        );
    }
}
