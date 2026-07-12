package org.takesome.frozenlands.engine.player;

import org.takesome.frozenlands.engine.lua.LuaRuntimeConfig;
import org.takesome.frozenlands.engine.runtime.RuntimeMaps;

import java.util.List;
import java.util.Map;

public final class PlayerRuntimeSettings {
    private static final String MODULE_ID = PlayerModule.ID;

    private final LuaRuntimeConfig loader = new LuaRuntimeConfig();
    private final Map<String, Object> config = loader.read(MODULE_ID);
    private final Map<String, Object> movement = loader.map(config, "movement");
    private final Map<String, Object> look = loader.map(config, "look");
    private final Map<String, Object> physics = loader.map(config, "physics");
    private final Map<String, Object> footsteps = loader.map(config, "footsteps");
    private final Map<String, Object> feedback = loader.map(config, "feedback");
    private final Map<String, Object> feedbackSounds = loader.map(feedback, "sounds");
    private final Map<String, Object> feedbackParticles = loader.map(feedback, "particles");
    private final Map<String, Object> interaction = loader.map(config, "interaction");
    private final Map<String, Object> interactionKinds = loader.map(interaction, "kinds");
    private final Map<String, Object> skeleton = loader.map(config, "skeleton");
    private final Map<String, Object> tool = loader.map(config, "tool");
    private final Map<String, Object> manager = loader.map(config, "manager");
    private final Map<String, Object> hud = loader.map(config, "hud");
    private final Map<String, Object> fpsVisual = loader.map(config, "fpsVisual");

    public float walkSpeed() { return loader.floating(movement, "walkSpeed", 4.0f); }
    public float runSpeed() { return loader.floating(movement, "runSpeed", 8.0f); }
    public float maxSmoothSpeedChange() { return loader.floating(movement, "maxSmoothSpeedChange", 2.0f); }
    public float walkDamping() { return loader.floating(physics, "walkDamping", 0.9f); }
    public float jumpDamping() { return loader.floating(physics, "jumpDamping", 0.0f); }
    public float characterHeight() { return loader.floating(physics, "characterHeight", 2.0f); }
    public float walkingRotationMultiplier() { return loader.floating(look, "walkingRotationMultiplier", 0.04f); }
    public float lookRotationMultiplier() { return loader.floating(look, "rotationMultiplier", walkingRotationMultiplier()); }
    public float runningRotationMultiplier() { return loader.floating(look, "runningRotationMultiplier", 0.1f); }
    public boolean hudEnabled() { return bool(hud, "enabled", true); }

    public boolean fpsHideCameraHead() { return bool(fpsVisual, "hideCamera" + "Head", true); }
    public List<String> fpsHiddenNameTokens() {
        List<String> configured = RuntimeMaps.stringList(fpsVisual.get("hiddenName" + "Tokens"));
        return configured.isEmpty() ? List.of("head", "hair", "eye", "eyelash") : configured;
    }


    public boolean managerPublishesState() { return bool(manager, "publishState", true); }
    public float managerTelemetryInterval() { return loader.floating(manager, "telemetryInterval", 0.25f); }

    public boolean footstepsEnabled() { return bool(footsteps, "enabled", true); }
    public float footstepWalkInterval() { return loader.floating(footsteps, "walkInterval", 0.52f); }
    public float footstepRunInterval() { return loader.floating(footsteps, "runInterval", 0.34f); }
    public float footstepMinSpeed() { return loader.floating(footsteps, "minSpeed", 0.2f); }
    public boolean footstepSoundEnabled() { return bool(footsteps, "soundEnabled", true); }
    public boolean footstepEventsEnabled() { return bool(footsteps, "eventsEnabled", true); }

    public boolean feedbackEnabled() { return bool(feedback, "enabled", true); }
    public boolean feedbackSoundEnabled() { return bool(feedback, "soundEnabled", true); }
    public boolean feedbackParticleEnabled() { return bool(feedback, "particleEnabled", true); }
    public String feedbackSound(String key, String fallback) { return string(feedbackSounds, key, fallback); }
    public String feedbackParticle(String key, String fallback) { return string(feedbackParticles, key, fallback); }

    public String interactionTargetType() { return string(interaction, "targetType", "grindable"); }
    public String interactionHitTopic(String fallback) { return string(interaction, "hitTopic", fallback); }
    public String interactionDestroyedTopic(String fallback) { return string(interaction, "destroyedTopic", fallback); }
    public String interactionMissedTopic(String fallback) { return string(interaction, "missedTopic", fallback); }
    public String interactionDestroyedFlag() { return string(interaction, "destroyedFlag", "destroyed"); }
    public String grindableKindName(int kindId) {
        Map<String, Object> kind = loader.map(interactionKinds, String.valueOf(kindId));
        return string(kind, "name", kindId == 1 ? "tree" : "generic");
    }

    public String skeletonChestNode() { return string(skeleton, "chestNode", ""); }
    public String skeletonNeckNode() { return string(skeleton, "neckNode", ""); }
    public String skeletonHeadNode() { return string(skeleton, "headNode", ""); }
    public String skeletonRightHandNode() { return string(skeleton, "rightHandNode", ""); }
    public String skeletonLeftHandNode() { return string(skeleton, "leftHandNode", ""); }

    public boolean toolEnabled() { return bool(tool, "enabled", true); }
    public String toolName() { return string(tool, "name", "tool"); }
    public String toolModel() { return string(tool, "model", ""); }
    public float toolScale() { return loader.floating(tool, "scale", 0.45f); }
    public boolean toolAttachToHand() { return bool(tool, "attachToHand", true); }
    public boolean toolHandFallbackToCamera() { return bool(tool, "handFallbackToCamera", true); }
    public String toolHandJoint() { return string(tool, "handJoint", skeletonRightHandNode()); }
    public float toolHandOffsetX() { return loader.floating(tool, "handOffsetX", 0.03f); }
    public float toolHandOffsetY() { return loader.floating(tool, "handOffsetY", 0.02f); }
    public float toolHandOffsetZ() { return loader.floating(tool, "handOffsetZ", 0.06f); }
    public float toolHandRotationX() { return loader.floating(tool, "handRotationX", -90f); }
    public float toolHandRotationY() { return loader.floating(tool, "handRotationY", 0f); }
    public float toolHandRotationZ() { return loader.floating(tool, "handRotationZ", 90f); }
    public float toolForwardOffset() { return loader.floating(tool, "forwardOffset", 1.15f); }
    public float toolLeftOffset() { return loader.floating(tool, "leftOffset", -0.45f); }
    public float toolUpOffset() { return loader.floating(tool, "upOffset", -0.45f); }
    public float toolRotationX() { return loader.floating(tool, "rotationX", -20f); }
    public float toolRotationY() { return loader.floating(tool, "rotationY", 90f); }
    public float toolRotationZ() { return loader.floating(tool, "rotationZ", 12f); }
    public float toolRange() { return loader.floating(tool, "range", 4.25f); }
    public float toolDamage() { return loader.floating(tool, "damage", 35f); }
    public float toolSwingSeconds() { return loader.floating(tool, "swingSeconds", 0.22f); }
    public float toolSwingArcDegrees() { return loader.floating(tool, "swingArcDegrees", -58f); }
    public String toolHitSound() { return string(tool, "hitSound", "chop"); }
    public boolean toolHitSoundEnabled() { return bool(tool, "hitSoundEnabled", true); }
    public float grindableDefaultHealth() { return loader.floating(tool, "grindableDefaultHealth", loader.floating(tool, "treeDefaultHealth", 100f)); }

    private String string(Map<String, Object> source, String key, String fallback) {
        return RuntimeMaps.string(source, key, fallback);
    }

    private boolean bool(Map<String, Object> source, String key, boolean fallback) {
        return RuntimeMaps.bool(source, key, fallback);
    }
}
