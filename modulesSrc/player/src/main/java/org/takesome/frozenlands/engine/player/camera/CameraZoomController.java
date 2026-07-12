package org.takesome.frozenlands.engine.player.camera;

import com.jme3.math.FastMath;
import org.takesome.frozenlands.engine.player.PlayerOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Smooth animated orbit-distance controller driven by mouse-wheel steps. */
final class CameraZoomController {
    private static final float EPSILON = 0.001f;

    private final float minimumDistance;
    private final float maximumDistance;
    private final float step;
    private final float response;
    private final float defaultDistance;

    private float currentDistance;
    private float targetDistance;

    CameraZoomController(PlayerOptions options) {
        this.minimumDistance = options.getCameraZoomMinimumDistance();
        this.maximumDistance = options.getCameraZoomMaximumDistance();
        this.step = options.getCameraZoomStep();
        this.response = options.getCameraZoomResponse();
        this.defaultDistance = clamp(options.getThirdPersonDistance());
        this.currentDistance = defaultDistance;
        this.targetDistance = defaultDistance;
    }

    void update(float tpf) {
        float alpha = 1f - FastMath.exp(-response * Math.max(0f, tpf));
        currentDistance += (targetDistance - currentDistance) * alpha;
        if (Math.abs(targetDistance - currentDistance) <= EPSILON) {
            currentDistance = targetDistance;
        }
    }

    float request(float wheelDirection) {
        if (!Float.isFinite(wheelDirection) || wheelDirection == 0f) {
            return targetDistance;
        }
        float direction = Math.signum(wheelDirection);
        targetDistance = clamp(targetDistance + direction * step);
        return targetDistance;
    }

    void prepareFromFirstPerson() {
        currentDistance = minimumDistance;
        targetDistance = clamp(minimumDistance + step);
    }

    void moveToMinimum() {
        targetDistance = minimumDistance;
    }

    void resetToDefault() {
        currentDistance = defaultDistance;
        targetDistance = defaultDistance;
    }

    boolean targetAtMinimum() {
        return targetDistance <= minimumDistance + EPSILON;
    }

    float currentDistance() {
        return currentDistance;
    }

    float targetDistance() {
        return targetDistance;
    }

    Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentDistance", currentDistance);
        result.put("targetDistance", targetDistance);
        result.put("minimumDistance", minimumDistance);
        result.put("maximumDistance", maximumDistance);
        result.put("step", step);
        result.put("animating", Math.abs(targetDistance - currentDistance) > EPSILON);
        return Map.copyOf(result);
    }

    private float clamp(float value) {
        return FastMath.clamp(value, minimumDistance, maximumDistance);
    }
}
