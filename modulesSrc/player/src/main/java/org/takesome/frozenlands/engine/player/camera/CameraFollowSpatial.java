package org.takesome.frozenlands.engine.player.camera;

import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.player.PlayerCollisionProfile;
import org.takesome.frozenlands.engine.player.PlayerOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Responsive first-/third-person orbit camera with independent body facing. */
public final class CameraFollowSpatial extends AbstractControl {
    private static final float MIN_CAMERA_PITCH = -80f * FastMath.DEG_TO_RAD;
    private static final float MAX_CAMERA_PITCH = 80f * FastMath.DEG_TO_RAD;

    private final Camera camera;
    private final PlayerOptions options;
    private final CameraOrbitState orbit = new CameraOrbitState(
            MIN_CAMERA_PITCH,
            MAX_CAMERA_PITCH
    );
    private final CameraZoomController zoom;
    private final PlayerCameraPoseSolver poseSolver;
    private final CameraCollisionResolver collisionResolver;
    private final CameraMotionController motion;
    private final CameraControlBindings bindings;

    private final Vector3f orbitDirection = new Vector3f();
    private final Vector3f horizontalForward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f desiredLocation = new Vector3f();
    private final Vector3f desiredTarget = new Vector3f();
    private final Vector3f resolvedLocation = new Vector3f();

    private BetterCharacterControl character;
    private CameraViewMode viewMode;
    private boolean ready;
    private boolean obstructed;
    private boolean alignToCharacterFacingDuringTransition;

    public CameraFollowSpatial(
            Camera camera,
            EngineContext context,
            PlayerOptions options,
            PlayerCollisionProfile collisionProfile,
            int playerRef
    ) {
        this.camera = camera;
        this.options = options;
        this.viewMode = options.getDefaultCameraView();
        this.zoom = new CameraZoomController(options);
        this.poseSolver = new PlayerCameraPoseSolver(options, collisionProfile);
        this.collisionResolver = new CameraCollisionResolver(context, options);
        this.motion = new CameraMotionController(camera, options);
        this.bindings = new CameraControlBindings(context, playerRef, orbit);
    }

    public void setViewMode(CameraViewMode requestedMode) {
        CameraViewMode next = requestedMode == null
                ? CameraViewMode.FIRST_PERSON
                : requestedMode;
        if (next == viewMode && ready) {
            return;
        }
        viewMode = next;
        alignToCharacterFacingDuringTransition = true;
        motion.beginTransition();
    }

    public CameraViewMode getViewMode() {
        return viewMode;
    }

    public void alignToFacing(Vector3f facingDirection) {
        orbit.alignToDirection(facingDirection);
    }

    public float requestZoom(float wheelDirection) {
        return zoom.request(wheelDirection);
    }

    public void prepareZoomFromFirstPerson() {
        zoom.prepareFromFirstPerson();
    }

    public void moveZoomToMinimum() {
        zoom.moveToMinimum();
    }

    public boolean zoomTargetAtMinimum() {
        return zoom.targetAtMinimum();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("view", viewMode.name());
        result.put("yaw", orbit.yaw());
        result.put("pitch", orbit.pitch());
        result.put("yawDegrees", orbit.yaw() * FastMath.RAD_TO_DEG);
        result.put("pitchDegrees", orbit.pitch() * FastMath.RAD_TO_DEG);
        result.put("fovDegrees", motion.fovDegrees());
        result.put("obstructed", obstructed);
        result.put("paused", bindings.paused());
        result.put("transitioning", motion.transitioning());
        result.put("location", motion.location().clone());
        result.put("target", motion.target().clone());
        result.put("zoom", zoom.status());
        return Map.copyOf(result);
    }

    @Override
    protected void controlUpdate(float tpf) {
        initializeIfRequired();
        if (bindings.paused() || character == null) {
            return;
        }

        float safeTpf = Math.max(0f, tpf);
        if (alignToCharacterFacingDuringTransition) {
            orbit.alignToDirection(character.getViewDirection());
        }
        orbit.update(safeTpf, options.getCameraLookResponse());
        zoom.update(safeTpf);
        orbit.direction(orbitDirection);
        orbit.horizontalForward(horizontalForward);
        orbit.right(right);
        poseSolver.solve(
                viewMode,
                spatial.getWorldTranslation(),
                orbitDirection,
                horizontalForward,
                right,
                zoom.currentDistance(),
                desiredLocation,
                desiredTarget
        );

        obstructed = viewMode != CameraViewMode.FIRST_PERSON
                && collisionResolver.resolve(
                        desiredTarget,
                        desiredLocation,
                        character,
                        resolvedLocation
                );
        if (!obstructed) {
            resolvedLocation.set(desiredLocation);
        }

        motion.apply(
                safeTpf,
                viewMode,
                resolvedLocation,
                desiredTarget,
                poseSolver.fovDegrees(viewMode),
                obstructed
        );
        if (alignToCharacterFacingDuringTransition && !motion.transitioning()) {
            orbit.alignToDirection(character.getViewDirection());
            alignToCharacterFacingDuringTransition = false;
        }
    }

    private void initializeIfRequired() {
        if (ready) {
            return;
        }
        ready = true;
        character = findCharacter(spatial);
        orbit.initialize(camera.getDirection());
        bindings.connect();
    }

    private BetterCharacterControl findCharacter(Spatial source) {
        if (source == null) {
            return null;
        }
        BetterCharacterControl direct = source.getControl(BetterCharacterControl.class);
        if (direct != null) {
            return direct;
        }
        final BetterCharacterControl[] found = {null};
        source.depthFirstTraversal(candidate -> {
            if (found[0] == null) {
                found[0] = candidate.getControl(BetterCharacterControl.class);
            }
        });
        return found[0];
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
    }

    @Override
    public void setSpatial(Spatial spatial) {
        if (spatial == null) {
            bindings.close();
            character = null;
            ready = false;
            obstructed = false;
            alignToCharacterFacingDuringTransition = false;
            zoom.resetToDefault();
            motion.reset();
        }
        super.setSpatial(spatial);
    }
}
