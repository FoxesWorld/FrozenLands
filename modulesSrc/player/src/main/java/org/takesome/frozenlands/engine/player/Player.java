package org.takesome.frozenlands.engine.player;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.player.camera.PlayerCameraRig;
import org.takesome.frozenlands.engine.player.input.FPSViewControl;
import org.takesome.frozenlands.engine.player.input.UserInputHandler;
import org.takesome.frozenlands.engine.player.menu.PlayerMenuState;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.Map;
import java.util.function.Consumer;

public class Player extends Node {
    private final EngineContext kernelInterface;
    private final PhysicsSpace pspace;
    private final PlayerOptions playerOptions;
    private final PlayerRuntimeSettings runtimeSettings;
    private final PlayerLocomotionState locomotionState = new PlayerLocomotionState();
    private final PlayerSoundProvider playerSoundProvider;
    private final PlayerModel playerModel;

    private UserInputHandler userInputHandler;
    private PlayerCameraRig cameraRig;
    private PlayerAnimationController animationController;
    private PlayerHeadTurnController headTurnController;
    private PlayerToolController toolController;
    private ActionsControl actionsControl;
    private PlayerMenuState playerMenuState;

    public Player(EngineContext kernel) {
        this.kernelInterface = kernel;
        this.pspace = kernel.getBulletAppState().getPhysicsSpace();
        this.playerOptions = PlayerOptions.load(ModuleIndexCatalog.defaultCatalog().configPath(PlayerModule.ID, "playerOptions"));
        this.runtimeSettings = new PlayerRuntimeSettings();
        this.playerSoundProvider = new PlayerSoundProvider(kernel);
        this.playerModel = new PlayerModel(kernel.assets(), playerOptions);
        logModelPipelineDiagnostics();
        this.playerModel.setCullHint(playerOptions.getCullHint());
        this.playerModel.setShadowMode(playerOptions.getShadowMode());
        attachChild(playerModel);
    }


    private void logModelPipelineDiagnostics() {
        if (playerModel.getSanitizedTangentBufferCount() > 0) {
            getLogger().warn(
                    "Player model pipeline sanitized tangent buffers model={} geometries={} buffers={} normalMapsCleared={} reason=skinning-tangent-layout-compatibility",
                    playerOptions.getModelPath(),
                    playerModel.getSanitizedTangentGeometryCount(),
                    playerModel.getSanitizedTangentBufferCount(),
                    playerModel.getSanitizedNormalMapCount()
            );
        }

        PlayerSkinningGuard.RepairResult repair = playerModel.getSkinningRepairResult();
        if (repair.repaired()) {
            getLogger().warn(
                    "Player model multi-skin targets repaired model={} details={}",
                    playerOptions.getModelPath(),
                    repair.summary()
            );
        } else if (repair.failed()) {
            getLogger().error(
                    "Player model multi-skin target repair failed model={} details={}",
                    playerOptions.getModelPath(),
                    repair.summary()
            );
        }

        PlayerSkinningGuard.Result skinning = playerModel.getSkinningGuardResult();
        if (skinning.disablesVisual()) {
            getLogger().error(
                    "Player model visual disabled by skinning guard model={} details={}",
                    playerOptions.getModelPath(),
                    skinning.summary()
            );
        } else if (skinning.skinned()) {
            getLogger().info(
                    "Player model skinning diagnostics ok model={} details={}",
                    playerOptions.getModelPath(),
                    skinning.summary()
            );
        }
    }

    public void addPlayer(Camera cam, Vector3f spawnPoint) {
        attachToSceneGraph();
        BetterCharacterControl character = initializeCollisionFromModel(spawnPoint);
        initializeRuntimeControls(cam);
        setLocalTranslation(spawnPoint);
        updateGeometricState();
        pspace.addAll(this);
        character.warp(spawnPoint);
        updateGeometricState();
        logSpawnDiagnostics(cam, spawnPoint, character);
        onSpawn();
    }

    private void attachToSceneGraph() {
        if (getParent() == null) {
            kernelInterface.getRootNode().attachChild(this);
        }
    }

    private BetterCharacterControl initializeCollisionFromModel(Vector3f spawnPoint) {
        PlayerCollisionProfile collision = playerModel.getCollisionProfile();
        BetterCharacterControl character = new BetterCharacterControl(collision.radius(), collision.height(), collision.mass());
        character.setJumpForce(playerOptions.getJumpForce());
        playerOptions.setCharacterControl(character);
        addControl(character);
        getLogger().info(
                "Player collision from {} radius={} height={} mass={} boundsCenter={} boundsExtent={}",
                collision.source(),
                collision.radius(),
                collision.height(),
                collision.mass(),
                collision.boundsCenter(),
                collision.boundsExtent()
        );
        return character;
    }

    private void initializeRuntimeControls(Camera cam) {
        actionsControl = new ActionsControl(this);
        addControl(actionsControl);

        userInputHandler = new UserInputHandler(this, () -> { });
        setPlayerHealth(playerOptions.getInitialHealth());
        addControl(userInputHandler);
        cameraRig = new PlayerCameraRig(this, cam, kernelInterface);
        toolController = new PlayerToolController(this);
        addControl(toolController);
        animationController = new PlayerAnimationController(this);
        addControl(animationController);
        addControl(new PlayerFootstepSystem(this));
        headTurnController = new PlayerHeadTurnController(this);
        playerModel.getPlayerSpatial().addControl(headTurnController);
        playerMenuState = new PlayerMenuState(this);
        getStateManager().attach(playerMenuState);
        addControl(new FPSViewControl(FPSViewControl.Mode.WORLD_SCENE));
    }

    private void onSpawn() {
        // Sound is routed by SoundProvider from PLAYER_SPAWNED.
    }

    private void logSpawnDiagnostics(Camera cam, Vector3f requestedSpawn, BetterCharacterControl character) {
        updateGeometricState();
        playerModel.updateGeometricState();
        playerModel.getPlayerSpatial().updateGeometricState();
        getLogger().info(
                "Player spawn diagnostics requestedSpawn={} playerWorld={} modelWorld={} visualWorld={} cameraLocation={} characterControlPresent={} viewMode={} playerCull={} modelCull={} visualCull={}",
                requestedSpawn,
                getWorldTranslation(),
                playerModel.getWorldTranslation(),
                playerModel.getPlayerSpatial().getWorldTranslation(),
                cam.getLocation(),
                character != null,
                cameraRig == null ? "<none>" : cameraRig.viewMode(),
                getCullHint(),
                playerModel.getCullHint(),
                playerModel.getPlayerSpatial().getCullHint()
        );
    }

    public Map<String, Object> publishEvent(String topic, Map<String, Object> payload) {
        return kernelInterface.getModuleRegistry().publishEvent(topic, payload);
    }

    public Map<String, Object> publishLiveEvent(String topic, Map<String, Object> payload) {
        return kernelInterface.getModuleRegistry().publishLiveEvent(topic, payload);
    }

    public AutoCloseable subscribeEvent(String topic, Consumer<Map<String, Object>> listener) {
        return subscribeEvent(topic, listener, false);
    }

    public AutoCloseable subscribeEvent(String topic, Consumer<Map<String, Object>> listener, boolean replayLatest) {
        return kernelInterface.getModuleRegistry().getEventBus().subscribe(topic, listener, replayLatest);
    }

    public int runtimeId() {
        return System.identityHashCode(this);
    }

    public Logger getLogger() {
        return kernelInterface.getLogger();
    }

    public Vector3f getPlayerPosition() {
        if (userInputHandler != null) {
            return userInputHandler.getPlayerPosition();
        }
        Vector3f worldTranslation = getWorldTranslation();
        return new Vector3f(Math.round(worldTranslation.x), Math.round(worldTranslation.y), Math.round(worldTranslation.z));
    }

    public UserInputHandler getUserInputHandler() { return userInputHandler; }
    public PlayerCameraRig getCameraRig() { return cameraRig; }
    public PlayerAnimationController getAnimationController() { return animationController; }
    public PlayerHeadTurnController getHeadTurnController() { return headTurnController; }
    public PlayerToolController getToolController() { return toolController; }
    public AssetManager getAssetManager() { return kernelInterface.getAssetManager(); }
    public AssetLibrary getAssets() { return kernelInterface.assets(); }
    public AppStateManager getStateManager() { return kernelInterface.appStateManager(); }
    public InputManager getInputManager() { return kernelInterface.getInputManager(); }
    public Camera getCamera() { return kernelInterface.getCamera(); }
    public PhysicsSpace getPhysicsSpace() { return pspace; }
    public Node getRootNode() { return kernelInterface.getRootNode(); }
    public Node getGuiNode() { return kernelInterface.getGuiNode(); }
    public Map getConfig() { return kernelInterface.getConfig(); }
    public PlayerOptions getPlayerOptions() { return playerOptions; }
    public PlayerRuntimeSettings getRuntimeSettings() { return runtimeSettings; }
    public PlayerLocomotionState getLocomotionState() { return locomotionState; }
    public PlayerModel getPlayerModel() { return playerModel; }
    public PlayerSoundProvider getPlayerSoundProvider() { return playerSoundProvider; }

    public void updateLocomotionState(org.takesome.frozenlands.engine.player.input.PlayerState state, Vector3f walkDirection, Vector3f velocity, boolean onGround, boolean running, float currentSpeed) {
        locomotionState.update(state, walkDirection, velocity, onGround, running, currentSpeed);
    }

    public void setPlayerHealth(int health) {
        playerOptions.setInitialHealth(health);
    }
}
