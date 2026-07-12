package org.takesome.frozenlands.engine.player.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/** Independent yaw/pitch state for first- and third-person camera orbit. */
final class CameraOrbitState {
    private final float minPitch;
    private final float maxPitch;

    private float yaw;
    private float pitch;
    private float targetYaw;
    private float targetPitch;
    private boolean initialized;

    CameraOrbitState(float minPitch, float maxPitch) {
        this.minPitch = Math.min(minPitch, maxPitch);
        this.maxPitch = Math.max(minPitch, maxPitch);
    }

    void initialize(Vector3f direction) {
        Vector3f safe = direction == null ? Vector3f.UNIT_Z : direction;
        float lengthSquared = safe.lengthSquared();
        if (lengthSquared <= 0.000001f) {
            yaw = 0f;
            pitch = 0f;
        } else {
            float inverseLength = FastMath.invSqrt(lengthSquared);
            float x = safe.x * inverseLength;
            float y = FastMath.clamp(safe.y * inverseLength, -1f, 1f);
            float z = safe.z * inverseLength;
            yaw = FastMath.atan2(x, z);
            pitch = FastMath.asin(y);
        }
        pitch = FastMath.clamp(pitch, minPitch, maxPitch);
        targetYaw = yaw;
        targetPitch = pitch;
        initialized = true;
    }

    void alignToDirection(Vector3f direction) {
        initialize(direction);
    }

    void setTarget(float yaw, float pitch) {
        targetYaw = wrap(yaw);
        targetPitch = FastMath.clamp(pitch, minPitch, maxPitch);
        if (!initialized) {
            this.yaw = targetYaw;
            this.pitch = targetPitch;
            initialized = true;
        }
    }

    void update(float tpf, float response) {
        if (!initialized) {
            initialize(Vector3f.UNIT_Z);
        }
        float safeResponse = Math.max(0.01f, response);
        float alpha = 1f - FastMath.exp(-safeResponse * Math.max(0f, tpf));
        yaw = wrap(yaw + shortestAngleDelta(yaw, targetYaw) * alpha);
        pitch += (targetPitch - pitch) * alpha;
        pitch = FastMath.clamp(pitch, minPitch, maxPitch);
    }

    Vector3f direction(Vector3f store) {
        Vector3f result = store == null ? new Vector3f() : store;
        float cosPitch = FastMath.cos(pitch);
        result.set(
                FastMath.sin(yaw) * cosPitch,
                FastMath.sin(pitch),
                FastMath.cos(yaw) * cosPitch
        );
        return normalizeOrForward(result);
    }

    Vector3f horizontalForward(Vector3f store) {
        Vector3f result = store == null ? new Vector3f() : store;
        result.set(FastMath.sin(yaw), 0f, FastMath.cos(yaw));
        return normalizeOrForward(result);
    }

    Vector3f right(Vector3f store) {
        Vector3f result = store == null ? new Vector3f() : store;
        horizontalForward(result);
        float forwardX = result.x;
        result.set(result.z, 0f, -forwardX);
        return normalizeOrRight(result);
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    static float shortestAngleDelta(float from, float to) {
        float delta = wrap(to) - wrap(from);
        if (delta > FastMath.PI) {
            delta -= FastMath.TWO_PI;
        } else if (delta < -FastMath.PI) {
            delta += FastMath.TWO_PI;
        }
        return delta;
    }

    private static float wrap(float angle) {
        float result = angle % FastMath.TWO_PI;
        if (result > FastMath.PI) {
            result -= FastMath.TWO_PI;
        } else if (result < -FastMath.PI) {
            result += FastMath.TWO_PI;
        }
        return result;
    }

    private Vector3f normalizeOrForward(Vector3f vector) {
        if (vector.lengthSquared() <= 0.000001f) {
            return vector.set(Vector3f.UNIT_Z);
        }
        return vector.normalizeLocal();
    }

    private Vector3f normalizeOrRight(Vector3f vector) {
        if (vector.lengthSquared() <= 0.000001f) {
            return vector.set(Vector3f.UNIT_X);
        }
        return vector.normalizeLocal();
    }
}
