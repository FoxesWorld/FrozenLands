package org.takesome.frozenlands.engine.player.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraOrbitStateTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void resolvesIndependentYawPitchDirections() {
        CameraOrbitState orbit = new CameraOrbitState(
                -80f * FastMath.DEG_TO_RAD,
                80f * FastMath.DEG_TO_RAD
        );
        orbit.initialize(Vector3f.UNIT_Z);
        orbit.setTarget(FastMath.HALF_PI, 30f * FastMath.DEG_TO_RAD);
        orbit.update(1f, 100f);

        Vector3f direction = orbit.direction(new Vector3f());
        Vector3f horizontal = orbit.horizontalForward(new Vector3f());

        assertEquals(FastMath.cos(30f * FastMath.DEG_TO_RAD), direction.x, EPSILON);
        assertEquals(0.5f, direction.y, EPSILON);
        assertEquals(0f, direction.z, EPSILON);
        assertEquals(1f, horizontal.x, EPSILON);
        assertEquals(0f, horizontal.y, EPSILON);
        assertEquals(0f, horizontal.z, EPSILON);
    }

    @Test
    void takesShortestRouteAcrossYawWrap() {
        float from = 179f * FastMath.DEG_TO_RAD;
        float to = -179f * FastMath.DEG_TO_RAD;

        float delta = CameraOrbitState.shortestAngleDelta(from, to);

        assertEquals(2f * FastMath.DEG_TO_RAD, delta, EPSILON);
    }

    @Test
    void alignsInstantlyToCharacterFacingAndClearsPitch() {
        CameraOrbitState orbit = new CameraOrbitState(-1.4f, 1.4f);
        orbit.initialize(Vector3f.UNIT_Z);
        orbit.setTarget(0.7f, 0.8f);
        orbit.update(1f, 100f);

        orbit.alignToDirection(Vector3f.UNIT_X);

        assertEquals(FastMath.HALF_PI, orbit.yaw(), EPSILON);
        assertEquals(0f, orbit.pitch(), EPSILON);
        Vector3f aligned = orbit.horizontalForward(new Vector3f());
        assertEquals(1f, aligned.x, EPSILON);
        assertEquals(0f, aligned.y, EPSILON);
        assertEquals(0f, aligned.z, EPSILON);
    }

    @Test
    void clampsPitchWithoutAffectingYawOrbit() {
        CameraOrbitState orbit = new CameraOrbitState(-1f, 1f);
        orbit.initialize(Vector3f.UNIT_Z);
        orbit.setTarget(0.75f, 10f);
        orbit.update(1f, 100f);

        assertEquals(0.75f, orbit.yaw(), EPSILON);
        assertEquals(1f, orbit.pitch(), EPSILON);
        assertTrue(orbit.direction(new Vector3f()).y > 0f);
    }
}
