package org.takesome.frozenlands.engine.player.camera;

import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerCameraRig {
    private final Player owner;
    private final Camera camera;
    private final CameraFollowSpatial followControl;
    private final EngineContext context;
    private final int playerRef;

    private AutoCloseable viewToggleSubscription;
    private AutoCloseable viewRequestSubscription;
    private AutoCloseable zoomRequestSubscription;
    private CameraViewMode viewMode;

    public PlayerCameraRig(Player owner, Camera camera, EngineContext context) {
        this.owner = owner;
        this.camera = camera;
        this.context = context;
        this.playerRef = owner.runtimeId();
        this.viewMode = owner.getPlayerOptions().getDefaultCameraView();
        this.followControl = new CameraFollowSpatial(
                camera,
                context,
                owner.getPlayerOptions(),
                owner.getPlayerModel().getCollisionProfile(),
                playerRef
        );
        owner.addControl(followControl);
        subscribeViewEvents();
        applyViewMode(viewMode, "initial");
    }

    public void toggleView() {
        applyViewMode(viewMode.toggle(), "toggle");
    }

    public void applyViewMode(CameraViewMode nextMode) {
        applyViewMode(nextMode, "direct");
    }

    public void applyViewMode(CameraViewMode nextMode, String reason) {
        CameraViewMode previous = viewMode;
        viewMode = nextMode == null ? CameraViewMode.FIRST_PERSON : nextMode;
        BetterCharacterControl character = owner.getPlayerOptions().getCharacterControl();
        Vector3f facingDirection = character == null
                ? camera.getDirection()
                : character.getViewDirection();
        followControl.alignToFacing(facingDirection);
        followControl.setViewMode(viewMode);
        boolean visualRequested = viewMode.showsPlayerVisual()
                || (viewMode == CameraViewMode.FIRST_PERSON
                && owner.getPlayerOptions().isFirstPersonBodyVisible());
        boolean visualVisible = owner.getPlayerModel().setVisualVisible(
                visualRequested,
                owner.getPlayerOptions().getCullHint()
        );
        if (visualRequested && !visualVisible) {
            context.getLogger().warn(
                    "Player visual request suppressed by skinning guard viewMode={} reason={} details={}",
                    viewMode,
                    reason,
                    owner.getPlayerModel().getVisualGuardReason()
            );
        }
        context.getLogger().info(
                "Player camera view changed: {} -> {} visualRequested={} visualVisible={} reason={} transitionSeconds={}",
                previous,
                viewMode,
                visualRequested,
                visualVisible,
                reason,
                owner.getPlayerOptions().getCameraTransitionSeconds()
        );
        context.getModuleRegistry().publishEvent(
                EngineEventTopics.PLAYER_CAMERA_VIEW_CHANGED,
                Map.of(
                        "playerRef", playerRef,
                        "from", previous.name(),
                        "to", viewMode.name(),
                        "view", viewMode.name(),
                        "reason", reason == null ? "" : reason,
                        "visualRequested", visualRequested,
                        "visualVisible", visualVisible,
                        "visualSuppressed", visualRequested && !visualVisible,
                        "transitionSeconds", owner.getPlayerOptions().getCameraTransitionSeconds()
                )
        );
    }

    public Camera camera() {
        return camera;
    }

    public CameraFollowSpatial followControl() {
        return followControl;
    }

    public CameraViewMode viewMode() {
        return viewMode;
    }

    public Map<String, Object> status() {
        return followControl.status();
    }

    public void close() {
        close(viewToggleSubscription);
        close(viewRequestSubscription);
        close(zoomRequestSubscription);
        viewToggleSubscription = null;
        viewRequestSubscription = null;
        zoomRequestSubscription = null;
    }

    private void subscribeViewEvents() {
        viewToggleSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.PLAYER_CAMERA_VIEW_TOGGLE_REQUESTED,
                this::onViewToggleRequested,
                false
        );
        viewRequestSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.PLAYER_CAMERA_VIEW_REQUESTED,
                this::onViewRequested,
                false
        );
        zoomRequestSubscription = context.getModuleRegistry().getEventBus().subscribe(
                EngineEventTopics.PLAYER_CAMERA_ZOOM_REQUESTED,
                this::onZoomRequested,
                false
        );
    }

    private void onViewToggleRequested(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        if (payload instanceof Map<?, ?> map && isOwnPlayer(map)) {
            toggleView();
        }
    }

    private void onViewRequested(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        if (!(payload instanceof Map<?, ?> map) || !isOwnPlayer(map)) {
            return;
        }
        CameraViewMode requested = CameraViewMode.parse(
                String.valueOf(map.get("view")),
                viewMode
        );
        applyViewMode(requested, "event-request");
    }

    private void onZoomRequested(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        if (!(payload instanceof Map<?, ?> map) || !isOwnPlayer(map)) {
            return;
        }
        float direction = floatValue(map.get("direction"), 0f);
        if (!Float.isFinite(direction) || direction == 0f) {
            return;
        }

        CameraViewMode previous = viewMode;
        boolean switchedView = false;
        if (viewMode == CameraViewMode.FIRST_PERSON) {
            if (direction <= 0f || !owner.getPlayerOptions().isCameraWheelSwitchesView()) {
                return;
            }
            followControl.prepareZoomFromFirstPerson();
            applyViewMode(CameraViewMode.THIRD_PERSON, "mouse-wheel-zoom-out");
            switchedView = true;
        } else {
            followControl.requestZoom(direction);
            if (direction < 0f
                    && followControl.zoomTargetAtMinimum()
                    && owner.getPlayerOptions().isCameraWheelSwitchesView()) {
                followControl.moveZoomToMinimum();
                applyViewMode(CameraViewMode.FIRST_PERSON, "mouse-wheel-zoom-in");
                switchedView = true;
            }
        }

        Map<String, Object> cameraStatus = followControl.status();
        Map<String, Object> changed = new LinkedHashMap<>();
        changed.put("playerRef", playerRef);
        changed.put("direction", Math.signum(direction));
        changed.put("fromView", previous.name());
        changed.put("view", viewMode.name());
        changed.put("switchedView", switchedView);
        changed.put("zoom", cameraStatus.getOrDefault("zoom", Map.of()));
        context.getModuleRegistry().publishLiveEvent(
                EngineEventTopics.PLAYER_CAMERA_ZOOM_CHANGED,
                Map.copyOf(changed)
        );
    }

    private boolean isOwnPlayer(Map<?, ?> map) {
        Object ref = map.get("playerRef");
        return ref == null || playerRef == longValue(ref, -1L);
    }

    private void close(AutoCloseable subscription) {
        if (subscription == null) {
            return;
        }
        try {
            subscription.close();
        } catch (Exception ignored) {
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private float floatValue(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null) {
            return fallback;
        }
        return Float.parseFloat(String.valueOf(value));
    }
}
