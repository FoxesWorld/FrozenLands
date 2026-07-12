package org.takesome.frozenlands.engine.world.sky;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.util.SkyFactory;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;
import jme3utilities.sky.command.SkyCommandBus;
import jme3utilities.sky.command.SkyCommandResult;
import jme3utilities.sky.runtime.SkyEnvironmentSnapshot;
import jme3utilities.sky.runtime.SkyLightingSnapshot;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.EngineContext;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Sky {

    private String skyTexture = "textures/FullskiesSunset0068.dds";
    private Vector3f sunDirection = new Vector3f(-1f, -1f, -1f);
    private ColorRGBA sunColor = ColorRGBA.White;
    private ColorRGBA ambientColor = ColorRGBA.DarkGray;
    private DirectionalLight sun;
    private final Node rootNode;
    private final AssetManager assetManager;
    private final Camera camera;
    private final Logger logger;
    private final Queue<Runnable> renderThreadCommands = new ConcurrentLinkedQueue<>();
    private volatile Thread renderThread;
    private SkyControl skyControl;
    private SkyCommandBus commandBus;
    private SkyCommandQueueControl commandQueueControl;

    public Sky(EngineContext kernelInterface) {
        this.rootNode = kernelInterface.getRootNode();
        this.assetManager = kernelInterface.getAssetManager();
        this.camera = kernelInterface.getCamera();
        this.logger = kernelInterface.getLogger();
    }

    public void addSky() {
        if (skyControl != null) {
            return;
        }

        var gi = new AmbientLight(ambientColor);
        sun = new DirectionalLight(sunDirection.normalizeLocal());
        sun.setColor(sunColor.mult(1f));

        Spatial sky = SkyFactory.createSky(assetManager, skyTexture, SkyFactory.EnvMapType.CubeMap);
        sky.setShadowMode(RenderQueue.ShadowMode.Off);
        rootNode.attachChild(sky);

        skyControl = new SkyControl(assetManager, camera, .5f, StarsOption.TopDome, true);
        commandBus = createCommandBus(skyControl);
        commandQueueControl = new SkyCommandQueueControl();

        // Queue control is attached before SkyControl so external sky commands
        // queued from other threads are applied before SkyControl.controlUpdate(tpf)
        // advances active transitions for the frame.
        rootNode.addControl(commandQueueControl);
        rootNode.addControl(skyControl);

        skyControl.setCloudiness(0.8f);
        skyControl.setCloudsYOffset(0.4f);
        skyControl.setTopVerticalAngle(1.78f);
        skyControl.getSunAndStars().setHour(11);
        Updater updater = skyControl.getUpdater();
        updater.setAmbientLight(gi);
        updater.setMainLight(sun);
        skyControl.setEnabled(true);
        rootNode.addLight(sun);
    }

    public DirectionalLight getSun() {
        return this.sun;
    }

    /**
     * Copy the current world-space direction from the camera toward the visual sun.
     * This is the authoritative solar direction used by the sky dome.
     *
     * @param storeResult optional storage vector
     * @return a normalized direction vector
     */
    public Vector3f getDirectionToSun(Vector3f storeResult) {
        if (skyControl == null) {
            throw new IllegalStateException("SkyControl is not initialized");
        }
        Vector3f result = storeResult == null ? new Vector3f() : storeResult;
        skyControl.getSunAndStars().sunDirection(result);
        if (result.lengthSquared() == 0f) {
            throw new IllegalStateException("SkyControl returned a zero sun direction");
        }
        return result.normalizeLocal();
    }

    /**
     * Copy the current world-space up direction used by the astronomical model.
     *
     * @param storeResult optional storage vector
     * @return a normalized up vector
     */
    public Vector3f getUpDirection(Vector3f storeResult) {
        if (skyControl == null) {
            throw new IllegalStateException("SkyControl is not initialized");
        }
        Vector3f result = storeResult == null ? new Vector3f() : storeResult;
        skyControl.getSunAndStars().upDirection(result);
        if (result.lengthSquared() == 0f) {
            throw new IllegalStateException("SkyControl returned a zero up direction");
        }
        return result.normalizeLocal();
    }

    /**
     * Calculate a camera-relative world position for screen-space solar effects.
     * The point remains on the same ray as the visual sun while the camera moves.
     *
     * @param distance positive distance from the camera
     * @param storeResult optional storage vector
     * @return the world-space sun point
     */
    public Vector3f getSunWorldPosition(float distance, Vector3f storeResult) {
        if (!Float.isFinite(distance) || distance <= 0f) {
            throw new IllegalArgumentException("distance must be finite and positive");
        }
        Vector3f result = getDirectionToSun(storeResult);
        result.multLocal(distance);
        result.addLocal(camera.getLocation());
        return result;
    }

    /**
     * Copy the current world-space direction from the camera toward the visual moon.
     *
     * @param storeResult optional storage vector
     * @return a normalized direction vector
     */
    public Vector3f getDirectionToMoon(Vector3f storeResult) {
        requireSkyControl();
        Vector3f result = storeResult == null ? new Vector3f() : storeResult;
        skyControl.moonDirection(result);
        if (result.lengthSquared() < 1e-8f) {
            throw new IllegalStateException("SkyControl returned a zero moon direction");
        }
        return result.normalizeLocal();
    }

    /**
     * Copy the propagation direction of the active celestial directional light.
     * This is the exact direction applied by SkyControl's Updater to the main
     * DirectionalLight. It can represent the sun, moon, or night/starlight source.
     *
     * @param storeResult optional storage vector
     * @return normalized light-ray propagation direction
     */
    public Vector3f getPrimaryLightPropagationDirection(Vector3f storeResult) {
        requireSkyControl();
        Vector3f result = storeResult == null ? new Vector3f() : storeResult;
        SkyLightingSnapshot snapshot = skyControl.lightingSnapshot();
        if (snapshot != null) {
            // SkyLightingSnapshot.mainDirection() points toward the source.
            // DirectionalLight and CSM renderers require ray propagation.
            snapshot.mainDirection(result).negateLocal();
        }
        if (result.lengthSquared() < 1e-8f) {
            getDirectionToSun(result).negateLocal();
        }
        return result.normalizeLocal();
    }

    /**
     * Copy the direction from the camera toward the active celestial source.
     *
     * @param storeResult optional storage vector
     * @return normalized direction toward the selected source
     */
    public Vector3f getDirectionToPrimaryLight(Vector3f storeResult) {
        return getPrimaryLightPropagationDirection(storeResult).negateLocal();
    }

    /**
     * Calculate a camera-relative world position for the active celestial source.
     * This is suitable for screen-space shafts and other source-position effects.
     *
     * @param distance positive distance from the camera
     * @param storeResult optional storage vector
     * @return camera-relative source position
     */
    public Vector3f getPrimaryLightWorldPosition(float distance, Vector3f storeResult) {
        if (!Float.isFinite(distance) || distance <= 0f) {
            throw new IllegalArgumentException("distance must be finite and positive");
        }
        Vector3f result = getDirectionToPrimaryLight(storeResult);
        return result.multLocal(distance).addLocal(camera.getLocation());
    }

    /** Atmospheric/time-of-day multiplier generated by SkyControl for shadows. */
    public float getPrimaryShadowIntensity() {
        requireSkyControl();
        SkyLightingSnapshot snapshot = skyControl.lightingSnapshot();
        if (snapshot == null || !Float.isFinite(snapshot.shadowIntensity())) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, snapshot.shadowIntensity()));
    }

    public boolean isSunUp() {
        requireSkyControl();
        SkyLightingSnapshot snapshot = skyControl.lightingSnapshot();
        return snapshot != null && snapshot.sunUp();
    }

    public boolean isMoonUp() {
        requireSkyControl();
        SkyLightingSnapshot snapshot = skyControl.lightingSnapshot();
        return snapshot != null && snapshot.moonUp();
    }

    /** Resolve the celestial source selected by SkyControl for the main light. */
    public String getPrimaryLightSource() {
        Vector3f main = getPrimaryLightPropagationDirection(null);
        Vector3f sunPropagation = getDirectionToSun(null).negateLocal();
        Vector3f moonPropagation = getDirectionToMoon(null).negateLocal();
        float sunAlignment = main.dot(sunPropagation);
        float moonAlignment = main.dot(moonPropagation);
        if (isSunUp() && sunAlignment >= moonAlignment) {
            return "sun";
        }
        if (isMoonUp() && moonAlignment > sunAlignment) {
            return "moon";
        }
        return "starlight";
    }

    /** The historical getSun() object is SkyControl's active main light. */
    public DirectionalLight getPrimaryDirectionalLight() {
        return sun;
    }

    public SkyControl getSkyControl() {
        return skyControl;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("initialized", skyControl != null);
        result.put("commandBus", commandBus != null);
        result.put("enabled", skyControl != null && skyControl.isEnabled());
        result.put("queuedCommands", renderThreadCommands.size());
        result.put("renderThreadKnown", renderThread != null);
        result.put("onRenderThread", isRenderThread());
        result.put("frameLoopOwner", "jMonkeyEngine control lifecycle");
        result.put("transitionTickSource", "SkyControl.controlUpdate(tpf)");
        if (skyControl != null) {
            result.put("primaryDirectionalSource", getPrimaryLightSource());
            result.put("primaryLightPropagationDirection", vectorToMap(
                    getPrimaryLightPropagationDirection(null)
            ));
            result.put("directionToPrimaryLight", vectorToMap(
                    getDirectionToPrimaryLight(null)
            ));
            result.put("sunUp", isSunUp());
            result.put("moonUp", isMoonUp());
            result.put("primaryShadowIntensity", getPrimaryShadowIntensity());
            result.put("directionAuthority", "-SkyLightingSnapshot.mainDirection");
        }
        return result;
    }

    /**
     * Execute a SkySimulation command ABI entry.
     * <p>
     * If called from the jME render/update thread, the command is executed
     * immediately. Otherwise it is queued and applied by the scene control before
     * SkyControl receives the next frame tpf. This keeps script-side callers as
     * intent sources and prevents external threads from mutating sky materials
     * directly.
     *
     * @param commandId canonical SkyCommandIds command id
     * @param arguments command arguments encoded as strings
     * @return execution result, or a queued acknowledgement for non-render thread calls
     */
    public Map<String, Object> executeCommand(String commandId, String... arguments) {
        if (commandBus == null) {
            return failed(commandId, "Sky command bus is not initialized");
        }
        String[] safeArguments = arguments == null ? new String[0] : arguments.clone();
        if (isRenderThread()) {
            return executeCommandOnRenderThread(commandId, safeArguments);
        }

        renderThreadCommands.add(() -> {
            Map<String, Object> result = executeCommandOnRenderThread(commandId, safeArguments);
            if (!Boolean.TRUE.equals(result.get("ok"))) {
                logger.warn("Queued sky command failed: {} {} -> {}",
                        commandId, Arrays.toString(safeArguments), result.get("message"));
            }
        });
        return queued(commandId, safeArguments);
    }

    /**
     * Manual headless tick facade for tests, servers, and preview hosts without
     * a jME scene update loop. Do not call this during normal game rendering;
     * attached SkyControl instances are advanced automatically by jME.
     *
     * @param deltaSeconds seconds since previous synthetic frame
     */
    public void tick(float deltaSeconds) {
        if (deltaSeconds < 0f) {
            throw new IllegalArgumentException("deltaSeconds must be non-negative");
        }
        if (skyControl == null) {
            return;
        }
        renderThread = Thread.currentThread();
        drainRenderThreadCommands();
        skyControl.controlUpdate(deltaSeconds);
    }

    private SkyCommandBus createCommandBus(SkyControl control) {
        try {
            return new SkyCommandBus(assetManager, control);
        } catch (RuntimeException exception) {
            logger.error("Failed to initialize SkySimulation command bus", exception);
            return null;
        }
    }

    private void requireSkyControl() {
        if (skyControl == null) {
            throw new IllegalStateException("SkyControl is not initialized");
        }
    }

    private boolean isRenderThread() {
        return renderThread != null && Thread.currentThread() == renderThread;
    }

    private void drainRenderThreadCommands() {
        Runnable command;
        while ((command = renderThreadCommands.poll()) != null) {
            command.run();
        }
    }

    private Map<String, Object> executeCommandOnRenderThread(String commandId, String[] arguments) {
        try {
            SkyCommandResult commandResult = commandBus.execute(commandId, arguments);
            return toMap(commandResult, false);
        } catch (RuntimeException exception) {
            logger.warn("Sky command failed: {} {}", commandId, Arrays.toString(arguments), exception);
            return failed(commandId, exception.getMessage());
        }
    }

    private Map<String, Object> toMap(SkyCommandResult commandResult, boolean queued) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", commandResult.succeeded());
        result.put("queued", queued);
        result.put("command", commandResult.commandId());
        result.put("message", commandResult.message());
        if (!commandResult.values().isEmpty()) {
            result.put("values", commandResult.values());
        }
        if (commandResult.snapshot() != null) {
            result.put("snapshot", snapshotToMap(commandResult.snapshot()));
        }
        return result;
    }

    private Map<String, Object> queued(String commandId, String[] arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("queued", true);
        result.put("command", commandId);
        result.put("arguments", Arrays.asList(arguments.clone()));
        result.put("message", "sky command queued for render/update thread");
        result.put("transitionTickSource", "SkyControl.controlUpdate(tpf)");
        return result;
    }

    private Map<String, Object> failed(String commandId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("queued", false);
        result.put("command", commandId);
        result.put("message", message == null ? "sky command failed" : message);
        return result;
    }

    private static Map<String, Object> snapshotToMap(SkyEnvironmentSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeOfDayHours", snapshot.timeOfDayHours());
        result.put("weatherId", snapshot.weatherId());
        result.put("cloudPreset", String.valueOf(snapshot.cloudPreset()));
        result.put("cloudiness", snapshot.cloudiness());
        result.put("visibility", snapshot.visibility());
        result.put("precipitation", snapshot.precipitation());
        result.put("windStrength", snapshot.windStrength());
        result.put("bloom", snapshot.bloom());
        result.put("shadowIntensity", snapshot.shadowIntensity());
        result.put("ambient", colorToMap(snapshot.ambient(null)));
        result.put("mainLight", colorToMap(snapshot.mainLight(null)));
        return result;
    }

    private static Map<String, Object> colorToMap(ColorRGBA color) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("r", color.r);
        result.put("g", color.g);
        result.put("b", color.b);
        result.put("a", color.a);
        return result;
    }

    private static Map<String, Object> vectorToMap(Vector3f vector) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", vector.x);
        result.put("y", vector.y);
        result.put("z", vector.z);
        return result;
    }

    private final class SkyCommandQueueControl extends AbstractControl {
        @Override
        protected void controlUpdate(float tpf) {
            renderThread = Thread.currentThread();
            drainRenderThreadCommands();
        }

        @Override
        protected void controlRender(RenderManager renderManager, ViewPort viewPort) {
            // Commands are update-thread work; render pass stays untouched.
        }
    }
}
