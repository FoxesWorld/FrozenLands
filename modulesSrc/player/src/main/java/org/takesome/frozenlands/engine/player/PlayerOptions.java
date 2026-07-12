package org.takesome.frozenlands.engine.player;

import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import org.takesome.frozenlands.engine.player.camera.CameraViewMode;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.Map;

public class PlayerOptions {
    private static final ModuleIndexCatalog MODULE_INDEX = ModuleIndexCatalog.defaultCatalog();

    private BetterCharacterControl characterControl;
    private String modelPath;
    private float scale;
    private Camera fpsCam;
    private Spatial.CullHint cullHint;
    private RenderQueue.ShadowMode shadowMode;
    private Vector3f jumpForce;
    private int initialHealth;
    private float mass;
    private String collisionSourceNode;
    private float collisionRadiusOverride;
    private float collisionHeightOverride;
    private float collisionRadiusScale;
    private float collisionHeightScale;
    private float minCollisionRadius;
    private float minCollisionHeight;
    private CameraViewMode defaultCameraView;
    private float firstPersonEyeHeight;
    private boolean firstPersonBodyVisible;
    private float firstPersonCameraForwardOffset;
    private float firstPersonCameraVerticalOffset;
    private float firstPersonLookAheadDistance;
    private float thirdPersonDistance;
    private float thirdPersonHeight;
    private float thirdPersonTargetHeight;
    private float thirdPersonVerticalOffset;
    private float thirdPersonShoulderOffset;
    private float thirdPersonLookAheadDistance;
    private float frontPersonDistance;
    private float frontPersonHeight;
    private float frontPersonTargetHeight;
    private float frontPersonVerticalOffset;
    private float frontPersonLookAheadDistance;
    private float firstPersonFovDegrees;
    private float thirdPersonFovDegrees;
    private float frontPersonFovDegrees;
    private float cameraLookResponse;
    private float firstPersonPositionResponse;
    private float thirdPersonPositionResponse;
    private float cameraTargetResponse;
    private float cameraFovResponse;
    private float cameraNearClip;
    private boolean cameraCollisionEnabled;
    private float cameraCollisionPadding;
    private float cameraMinimumDistance;
    private float cameraZoomMinimumDistance;
    private float cameraZoomMaximumDistance;
    private float cameraZoomStep;
    private float cameraZoomResponse;
    private boolean cameraWheelSwitchesView;
    private float cameraTransitionSeconds;
    private float mouseSensitivity;
    private float minMouseSensitivity;
    private float maxMouseSensitivity;
    private boolean sanitizeSkinnedTangents;

    public PlayerOptions(Map<String, Object> options) {
        loadOptions(options);
    }

    public static PlayerOptions load(String path) {
        return new PlayerOptions(MODULE_INDEX.readJsonMap(path));
    }

    private void loadOptions(Map<String, Object> optionsMap) {
        this.modelPath = requiredAssetPath(optionsMap, "model", "player.model");
        this.scale = RuntimeMaps.floating(optionsMap, "scale", 1f);
        this.cullHint = Spatial.CullHint.valueOf(RuntimeMaps.string(optionsMap, "cullHint", "Never"));
        this.shadowMode = RenderQueue.ShadowMode.valueOf(RuntimeMaps.string(optionsMap, "shadowMode", "Cast"));
        this.jumpForce = new Vector3f(0, RuntimeMaps.floating(optionsMap, "jumpForce", 500f), 0);
        this.initialHealth = RuntimeMaps.integer(optionsMap, "initialHealth", 100);
        this.mass = RuntimeMaps.floating(optionsMap, "mass", 175f);

        Map<String, Object> modelPipeline = RuntimeMaps.map(optionsMap, "modelPipeline");
        this.sanitizeSkinnedTangents = RuntimeMaps.bool(modelPipeline, "sanitizeSkinnedTangents", true);

        Map<String, Object> collision = RuntimeMaps.map(optionsMap, "collision");
        this.collisionSourceNode = RuntimeMaps.string(collision, "sourceNode", "collision");
        this.collisionRadiusOverride = RuntimeMaps.floating(collision, "radiusOverride", -1f);
        this.collisionHeightOverride = RuntimeMaps.floating(collision, "heightOverride", -1f);
        this.collisionRadiusScale = RuntimeMaps.floating(collision, "radiusScale", 1.0f);
        this.collisionHeightScale = RuntimeMaps.floating(collision, "heightScale", 1.0f);
        this.minCollisionRadius = RuntimeMaps.floating(collision, "minRadius", RuntimeMaps.floating(optionsMap, "radius", 0.25f));
        this.minCollisionHeight = RuntimeMaps.floating(collision, "minHeight", RuntimeMaps.floating(optionsMap, "height", 1.0f));

        Map<String, Object> camera = RuntimeMaps.map(optionsMap, "camera");
        this.defaultCameraView = CameraViewMode.parse(RuntimeMaps.string(camera, "defaultView", "FIRST_PERSON"), CameraViewMode.FIRST_PERSON);
        this.firstPersonEyeHeight = RuntimeMaps.floating(camera, "firstPersonEyeHeight", 1.65f);
        this.firstPersonBodyVisible = RuntimeMaps.bool(camera, "firstPersonBodyVisible", true);
        this.firstPersonCameraForwardOffset = RuntimeMaps.floating(camera, "firstPersonCameraForwardOffset", 0.18f);
        this.firstPersonCameraVerticalOffset = RuntimeMaps.floating(camera, "firstPersonCameraVerticalOffset", -0.08f);
        this.firstPersonLookAheadDistance = Math.max(0.25f, RuntimeMaps.floating(camera, "firstPersonLookAheadDistance", 8.0f));

        this.thirdPersonDistance = Math.max(0.5f, RuntimeMaps.floating(camera, "thirdPersonDistance", 5.0f));
        this.thirdPersonHeight = RuntimeMaps.floating(camera, "thirdPersonHeight", 2.15f);
        this.thirdPersonTargetHeight = RuntimeMaps.floating(camera, "thirdPersonTargetHeight", this.firstPersonEyeHeight);
        this.thirdPersonVerticalOffset = RuntimeMaps.floating(
                camera,
                "thirdPersonVerticalOffset",
                Math.max(0f, this.thirdPersonHeight - this.thirdPersonTargetHeight)
        );
        this.thirdPersonShoulderOffset = RuntimeMaps.floating(camera, "thirdPersonShoulderOffset", 0.45f);
        this.thirdPersonLookAheadDistance = Math.max(0f, RuntimeMaps.floating(camera, "thirdPersonLookAheadDistance", 0.35f));

        this.frontPersonDistance = Math.max(0.5f, RuntimeMaps.floating(camera, "frontPersonDistance", this.thirdPersonDistance * 0.85f));
        this.frontPersonHeight = RuntimeMaps.floating(camera, "frontPersonHeight", this.thirdPersonHeight);
        this.frontPersonTargetHeight = RuntimeMaps.floating(camera, "frontPersonTargetHeight", this.firstPersonEyeHeight);
        this.frontPersonVerticalOffset = RuntimeMaps.floating(
                camera,
                "frontPersonVerticalOffset",
                Math.max(0f, this.frontPersonHeight - this.frontPersonTargetHeight)
        );
        this.frontPersonLookAheadDistance = Math.max(0f, RuntimeMaps.floating(camera, "frontPersonLookAheadDistance", 0.15f));

        this.firstPersonFovDegrees = clamp(RuntimeMaps.floating(camera, "firstPersonFovDegrees", 80f), 45f, 120f);
        this.thirdPersonFovDegrees = clamp(RuntimeMaps.floating(camera, "thirdPersonFovDegrees", 70f), 45f, 120f);
        this.frontPersonFovDegrees = clamp(RuntimeMaps.floating(camera, "frontPersonFovDegrees", 68f), 45f, 120f);
        this.cameraLookResponse = Math.max(1f, RuntimeMaps.floating(camera, "lookResponse", 36f));
        this.firstPersonPositionResponse = Math.max(1f, RuntimeMaps.floating(camera, "firstPersonPositionResponse", 42f));
        this.thirdPersonPositionResponse = Math.max(1f, RuntimeMaps.floating(camera, "thirdPersonPositionResponse", 14f));
        this.cameraTargetResponse = Math.max(1f, RuntimeMaps.floating(camera, "targetResponse", 28f));
        this.cameraFovResponse = Math.max(1f, RuntimeMaps.floating(camera, "fovResponse", 12f));
        this.cameraNearClip = Math.max(0.01f, RuntimeMaps.floating(camera, "nearClip", 0.06f));
        this.cameraCollisionEnabled = RuntimeMaps.bool(camera, "collisionEnabled", true);
        this.cameraCollisionPadding = Math.max(0f, RuntimeMaps.floating(camera, "collisionPadding", 0.22f));
        this.cameraMinimumDistance = Math.max(0.1f, RuntimeMaps.floating(camera, "minimumDistance", 0.75f));
        this.cameraZoomMinimumDistance = Math.max(
                this.cameraMinimumDistance,
                RuntimeMaps.floating(camera, "zoomMinimumDistance", 1.4f)
        );
        this.cameraZoomMaximumDistance = Math.max(
                this.cameraZoomMinimumDistance,
                RuntimeMaps.floating(camera, "zoomMaximumDistance", 9.0f)
        );
        this.cameraZoomStep = Math.max(0.05f, RuntimeMaps.floating(camera, "zoomStep", 0.65f));
        this.cameraZoomResponse = Math.max(1f, RuntimeMaps.floating(camera, "zoomResponse", 16f));
        this.cameraWheelSwitchesView = RuntimeMaps.bool(camera, "wheelSwitchesView", true);
        this.cameraTransitionSeconds = Math.max(0.01f, RuntimeMaps.floating(camera, "transitionSeconds", 0.18f));

        Map<String, Object> input = RuntimeMaps.map(optionsMap, "input");
        this.minMouseSensitivity = Math.max(0.01f, RuntimeMaps.floating(input, "minMouseSensitivity", 0.2f));
        this.maxMouseSensitivity = Math.max(this.minMouseSensitivity, RuntimeMaps.floating(input, "maxMouseSensitivity", 3.0f));
        this.mouseSensitivity = clamp(RuntimeMaps.floating(input, "mouseSensitivity", 1.0f), minMouseSensitivity, maxMouseSensitivity);
    }







    private String requiredAssetPath(Map<String, Object> options, String key, String configKey) {
        String value = RuntimeMaps.string(options, key, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Required player asset path is not configured: " + configKey);
        }
        return value;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public String getModelPath() { return modelPath; }
    public float getScale() { return scale; }
    public Spatial.CullHint getCullHint() { return cullHint; }
    public RenderQueue.ShadowMode getShadowMode() { return shadowMode; }
    public Vector3f getJumpForce() { return jumpForce; }
    public int getInitialHealth() { return initialHealth; }
    public float getMass() { return mass; }
    public String getCollisionSourceNode() { return collisionSourceNode; }
    public float getCollisionRadiusOverride() { return collisionRadiusOverride; }
    public float getCollisionHeightOverride() { return collisionHeightOverride; }
    public float getCollisionRadiusScale() { return collisionRadiusScale; }
    public float getCollisionHeightScale() { return collisionHeightScale; }
    public float getMinCollisionRadius() { return minCollisionRadius; }
    public float getMinCollisionHeight() { return minCollisionHeight; }
    public CameraViewMode getDefaultCameraView() { return defaultCameraView; }
    public float getFirstPersonEyeHeight() { return firstPersonEyeHeight; }
    public boolean isFirstPersonBodyVisible() { return firstPersonBodyVisible; }
    public float getFirstPersonCameraForwardOffset() { return firstPersonCameraForwardOffset; }
    public float getFirstPersonCameraVerticalOffset() { return firstPersonCameraVerticalOffset; }
    public float getFirstPersonLookAheadDistance() { return firstPersonLookAheadDistance; }
    public float getThirdPersonDistance() { return thirdPersonDistance; }
    public float getThirdPersonHeight() { return thirdPersonHeight; }
    public float getThirdPersonTargetHeight() { return thirdPersonTargetHeight; }
    public float getThirdPersonVerticalOffset() { return thirdPersonVerticalOffset; }
    public float getThirdPersonShoulderOffset() { return thirdPersonShoulderOffset; }
    public float getThirdPersonLookAheadDistance() { return thirdPersonLookAheadDistance; }
    public float getFrontPersonDistance() { return frontPersonDistance; }
    public float getFrontPersonHeight() { return frontPersonHeight; }
    public float getFrontPersonTargetHeight() { return frontPersonTargetHeight; }
    public float getFrontPersonVerticalOffset() { return frontPersonVerticalOffset; }
    public float getFrontPersonLookAheadDistance() { return frontPersonLookAheadDistance; }
    public float getFirstPersonFovDegrees() { return firstPersonFovDegrees; }
    public float getThirdPersonFovDegrees() { return thirdPersonFovDegrees; }
    public float getFrontPersonFovDegrees() { return frontPersonFovDegrees; }
    public float getCameraLookResponse() { return cameraLookResponse; }
    public float getFirstPersonPositionResponse() { return firstPersonPositionResponse; }
    public float getThirdPersonPositionResponse() { return thirdPersonPositionResponse; }
    public float getCameraTargetResponse() { return cameraTargetResponse; }
    public float getCameraFovResponse() { return cameraFovResponse; }
    public float getCameraNearClip() { return cameraNearClip; }
    public boolean isCameraCollisionEnabled() { return cameraCollisionEnabled; }
    public float getCameraCollisionPadding() { return cameraCollisionPadding; }
    public float getCameraMinimumDistance() { return cameraMinimumDistance; }
    public float getCameraZoomMinimumDistance() { return cameraZoomMinimumDistance; }
    public float getCameraZoomMaximumDistance() { return cameraZoomMaximumDistance; }
    public float getCameraZoomStep() { return cameraZoomStep; }
    public float getCameraZoomResponse() { return cameraZoomResponse; }
    public boolean isCameraWheelSwitchesView() { return cameraWheelSwitchesView; }
    public float getCameraTransitionSeconds() { return cameraTransitionSeconds; }
    public Camera getFpsCam() { return fpsCam; }
    public void setFpsCam(Camera fpsCam) { this.fpsCam = fpsCam; }
    public float getMouseSensitivity() { return mouseSensitivity; }
    public float getMinMouseSensitivity() { return minMouseSensitivity; }
    public float getMaxMouseSensitivity() { return maxMouseSensitivity; }
    public float setMouseSensitivity(float mouseSensitivity) {
        this.mouseSensitivity = clamp(mouseSensitivity, minMouseSensitivity, maxMouseSensitivity);
        return this.mouseSensitivity;
    }
    public boolean sanitizeSkinnedTangents() { return sanitizeSkinnedTangents; }
    public BetterCharacterControl getCharacterControl() { return characterControl; }
    public void setCharacterControl(BetterCharacterControl characterControl) { this.characterControl = characterControl; }
    public void setInitialHealth(int initialHealth) { this.initialHealth = initialHealth; }
}
