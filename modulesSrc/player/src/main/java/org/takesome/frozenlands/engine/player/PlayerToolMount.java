package org.takesome.frozenlands.engine.player;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerToolMount {
    private final Player player;
    private final PlayerRuntimeSettings settings;
    private final Vector3f tmpPosition = new Vector3f();
    private final Vector3f tmpForward = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Quaternion tmpRotation = new Quaternion();
    private final Quaternion cameraRotationOffset = new Quaternion();
    private final Quaternion handRotationOffset = new Quaternion();
    private final Quaternion swingRotation = new Quaternion();

    private Spatial toolSpatial;
    private Node handAttachmentNode;
    private boolean handAttachAttempted;
    private boolean handMounted;
    private String mountMode = "none";
    private float swingTimer;

    public PlayerToolMount(Player player, PlayerRuntimeSettings settings) {
        this.player = player;
        this.settings = settings;
        cameraRotationOffset.fromAngles(
                settings.toolRotationX() * FastMath.DEG_TO_RAD,
                settings.toolRotationY() * FastMath.DEG_TO_RAD,
                settings.toolRotationZ() * FastMath.DEG_TO_RAD
        );
        handRotationOffset.fromAngles(
                settings.toolHandRotationX() * FastMath.DEG_TO_RAD,
                settings.toolHandRotationY() * FastMath.DEG_TO_RAD,
                settings.toolHandRotationZ() * FastMath.DEG_TO_RAD
        );
    }

    public void ensureLoaded() {
        if (toolSpatial == null && settings.toolEnabled()) {
            loadTool();
        }
    }

    public void update(float tpf) {
        if (toolSpatial == null) {
            return;
        }
        if (handMounted) {
            applyHandToolTransform(tpf);
        } else {
            updateCameraFallbackToolTransform(tpf);
        }
    }

    public void triggerSwing() {
        swingTimer = settings.toolSwingSeconds();
    }

    public void detach() {
        if (toolSpatial != null) {
            toolSpatial.removeFromParent();
            toolSpatial = null;
        }
        handAttachmentNode = null;
        handMounted = false;
        mountMode = "none";
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("visible", toolSpatial != null && toolSpatial.getParent() != null);
        status.put("mountMode", mountMode);
        status.put("handMounted", handMounted);
        status.put("handJoint", settings.toolHandJoint());
        status.put("swinging", swingTimer > 0f);
        return status;
    }

    public String mountMode() {
        return mountMode;
    }

    private void loadTool() {
        toolSpatial = player.getAssets().model(settings.toolModel());
        toolSpatial.setName("player_tool_" + settings.toolName());
        toolSpatial.setLocalScale(settings.toolScale());
        toolSpatial.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        if (settings.toolAttachToHand() && mountToolToHand()) {
            mountMode = "hand";
        } else if (settings.toolHandFallbackToCamera()) {
            mountToolToCameraFallback();
        } else {
            toolSpatial.removeFromParent();
            mountMode = "disabled-no-hand";
        }
    }

    private boolean mountToolToHand() {
        handAttachAttempted = true;
        handAttachmentNode = findAttachmentNode(player.getPlayerModel().getPlayerSpatial(), settings.toolHandJoint());
        if (handAttachmentNode == null) {
            player.getLogger().warn("Player tool hand attachment was not found: joint={}; falling back={}",
                    settings.toolHandJoint(), settings.toolHandFallbackToCamera());
            return false;
        }
        toolSpatial.removeFromParent();
        player.getRootNode().attachChild(toolSpatial);
        handMounted = true;
        applyHandToolTransform(0f);
        player.getLogger().info("Player tool mounted to hand joint={} tool={} model={} mode=world-follow",
                settings.toolHandJoint(), settings.toolName(), settings.toolModel());
        return true;
    }

    private void mountToolToCameraFallback() {
        toolSpatial.removeFromParent();
        player.getRootNode().attachChild(toolSpatial);
        handMounted = false;
        mountMode = handAttachAttempted ? "camera-fallback" : "camera";
    }

    private Node findAttachmentNode(Spatial source, String jointName) {
        if (source == null || jointName == null || jointName.isBlank()) {
            return null;
        }
        Node fromControl = findAttachmentNodeFromControls(source, jointName);
        if (fromControl != null) {
            return fromControl;
        }
        if (source instanceof Node node) {
            if (jointName.equals(node.getName())) {
                return node;
            }
            for (Spatial child : node.getChildren()) {
                Node found = findAttachmentNode(child, jointName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Node findAttachmentNodeFromControls(Spatial source, String jointName) {
        for (int i = 0; i < source.getNumControls(); i++) {
            Control control = source.getControl(i);
            Node attachmentNode = invokeAttachmentNode(control, jointName);
            if (attachmentNode != null) {
                return attachmentNode;
            }
        }
        return null;
    }

    private Node invokeAttachmentNode(Control control, String jointName) {
        if (control == null) {
            return null;
        }
        try {
            Method method = control.getClass().getMethod("getAttachmentsNode", String.class);
            Object result = method.invoke(control, jointName);
            return result instanceof Node node ? node : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private void applyHandToolTransform(float tpf) {
        tmpPosition.set(settings.toolHandOffsetX(), settings.toolHandOffsetY(), settings.toolHandOffsetZ());
        Vector3f worldOffset = handAttachmentNode == null
                ? tmpPosition.clone()
                : handAttachmentNode.getWorldRotation().mult(tmpPosition);
        if (handAttachmentNode != null) {
            tmpPosition.set(handAttachmentNode.getWorldTranslation()).addLocal(worldOffset);
            tmpRotation.set(handAttachmentNode.getWorldRotation()).multLocal(handRotationOffset);
        } else {
            tmpRotation.set(handRotationOffset);
        }
        applySwingRotation(tpf);
        toolSpatial.setLocalTranslation(tmpPosition);
        toolSpatial.setLocalRotation(tmpRotation);
    }

    private void updateCameraFallbackToolTransform(float tpf) {
        Camera camera = player.getCamera();
        if (camera == null) {
            return;
        }
        tmpForward.set(camera.getDirection());
        tmpLeft.set(camera.getLeft());
        tmpUp.set(camera.getUp());
        tmpPosition.set(camera.getLocation());
        tmpPosition.addLocal(tmpForward.mult(settings.toolForwardOffset()));
        tmpPosition.addLocal(tmpLeft.mult(settings.toolLeftOffset()));
        tmpPosition.addLocal(tmpUp.mult(settings.toolUpOffset()));
        toolSpatial.setLocalTranslation(tmpPosition);

        tmpRotation.set(camera.getRotation()).multLocal(cameraRotationOffset);
        applySwingRotation(tpf);
        toolSpatial.setLocalRotation(tmpRotation);
    }

    private void applySwingRotation(float tpf) {
        if (swingTimer <= 0f) {
            return;
        }
        float swingProgress = 1f - (swingTimer / Math.max(0.001f, settings.toolSwingSeconds()));
        float swingArc = FastMath.sin(swingProgress * FastMath.PI) * settings.toolSwingArcDegrees() * FastMath.DEG_TO_RAD;
        swingRotation.fromAngleAxis(swingArc, Vector3f.UNIT_X);
        tmpRotation.multLocal(swingRotation);
        swingTimer = Math.max(0f, swingTimer - tpf);
    }
}
