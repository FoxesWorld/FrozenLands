package org.takesome.frozenlands.engine.player.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.takesome.frozenlands.engine.player.PlayerOptions;

/** Applies smoothing, view transitions and FOV to the native camera. */
final class CameraMotionController {
    private static final float COLLISION_SNAP_EPSILON = 0.02f;

    private final Camera camera;
    private final PlayerOptions options;
    private final Vector3f location = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f transitionStartLocation = new Vector3f();
    private final Vector3f transitionStartTarget = new Vector3f();

    private boolean initialized;
    private boolean transitionActive;
    private float transitionTime;
    private float transitionStartFov;
    private float currentFov;

    CameraMotionController(Camera camera, PlayerOptions options) {
        this.camera = camera;
        this.options = options;
    }

    void beginTransition() {
        if (!initialized) {
            transitionActive = false;
            return;
        }
        transitionStartLocation.set(location);
        transitionStartTarget.set(target);
        transitionStartFov = currentFov;
        transitionTime = 0f;
        transitionActive = true;
    }

    void apply(
            float tpf,
            CameraViewMode viewMode,
            Vector3f desiredLocation,
            Vector3f desiredTarget,
            float desiredFov,
            boolean obstructed
    ) {
        float safeTpf = Math.max(0f, tpf);
        if (!initialized) {
            location.set(desiredLocation);
            target.set(desiredTarget);
            currentFov = desiredFov;
            initialized = true;
            transitionActive = false;
        } else if (transitionActive) {
            applyTransition(safeTpf, desiredLocation, desiredTarget, desiredFov);
        } else {
            applyContinuousMotion(
                    safeTpf,
                    viewMode,
                    desiredLocation,
                    desiredTarget,
                    desiredFov,
                    obstructed
            );
        }

        camera.setLocation(location);
        camera.lookAt(target, Vector3f.UNIT_Y);
        applyFov();
    }

    Vector3f location() {
        return location;
    }

    Vector3f target() {
        return target;
    }

    float fovDegrees() {
        return currentFov;
    }

    boolean transitioning() {
        return transitionActive;
    }

    void reset() {
        initialized = false;
        transitionActive = false;
        transitionTime = 0f;
        currentFov = 0f;
        location.set(Vector3f.ZERO);
        target.set(Vector3f.ZERO);
    }

    private void applyTransition(
            float tpf,
            Vector3f desiredLocation,
            Vector3f desiredTarget,
            float desiredFov
    ) {
        transitionTime += tpf;
        float alpha = Math.min(
                1f,
                transitionTime / options.getCameraTransitionSeconds()
        );
        float eased = alpha * alpha * (3f - 2f * alpha);
        location.set(transitionStartLocation)
                .interpolateLocal(desiredLocation, eased);
        target.set(transitionStartTarget)
                .interpolateLocal(desiredTarget, eased);
        currentFov = FastMath.interpolateLinear(
                eased,
                transitionStartFov,
                desiredFov
        );
        if (alpha >= 1f) {
            transitionActive = false;
        }
    }

    private void applyContinuousMotion(
            float tpf,
            CameraViewMode viewMode,
            Vector3f desiredLocation,
            Vector3f desiredTarget,
            float desiredFov,
            boolean obstructed
    ) {
        float positionResponse = viewMode == CameraViewMode.FIRST_PERSON
                ? options.getFirstPersonPositionResponse()
                : options.getThirdPersonPositionResponse();
        if (obstructed && orbitDistance() > desiredLocation.distance(desiredTarget)
                + COLLISION_SNAP_EPSILON) {
            location.set(desiredLocation);
        } else {
            smooth(location, desiredLocation, positionResponse, tpf);
        }
        smooth(target, desiredTarget, options.getCameraTargetResponse(), tpf);
        currentFov += (desiredFov - currentFov)
                * responseAlpha(options.getCameraFovResponse(), tpf);
    }

    private void applyFov() {
        float aspect = camera.getWidth() / (float) Math.max(1, camera.getHeight());
        float near = options.getCameraNearClip();
        float far = Math.max(near + 1f, camera.getFrustumFar());
        camera.setFrustumPerspective(currentFov, aspect, near, far);
    }

    private float orbitDistance() {
        return location.distance(target);
    }

    private void smooth(
            Vector3f current,
            Vector3f desired,
            float response,
            float tpf
    ) {
        current.interpolateLocal(desired, responseAlpha(response, tpf));
    }

    private float responseAlpha(float response, float tpf) {
        return 1f - FastMath.exp(-Math.max(0.01f, response) * tpf);
    }
}
