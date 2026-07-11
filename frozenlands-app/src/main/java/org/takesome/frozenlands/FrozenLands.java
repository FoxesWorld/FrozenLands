package org.takesome.frozenlands;

import com.jme3.app.Application;
import com.jme3.app.LostFocusBehavior;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.Listener;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.system.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takesome.frozenlands.engine.EngineApplicationHost;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.ui.screen.UiScreenModel;
import org.takesome.frozenlands.launch.FrozenLandsLauncherBootstrap;
import org.takesome.frozenlands.logging.LoggingBootstrap;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public final class FrozenLands implements Application, EngineApplicationHost {
    private final Node rootNode = new Node("Root Node");
    private final Node guiNode = new Node("Gui Node");
    private final Logger logger = LoggerFactory.getLogger(FrozenLands.class);
    private final FrozenLandsRuntimeHost runtime = new FrozenLandsRuntimeHost();
    private final FrozenLandsFocusController focusController;
    private final FrozenLandsStartup startup;

    public FrozenLands() {
        focusController = new FrozenLandsFocusController(runtime, logger, this::kernelForFocus);
        startup = new FrozenLandsStartup(this, runtime, rootNode, guiNode, focusController);
        runtime.setCallbacks(new FrozenLandsRuntimeHost.Callbacks() {
            @Override
            public void initializeRuntimeHost() {
                startup.initializeRuntimeHost();
            }

            @Override
            public void updateRuntimeHost() {
                startup.updateRuntimeHost();
            }

            @Override
            public void destroyRuntimeHost() {
                startup.destroyRuntimeHost();
                focusController.cleanupFocusToggleMapping();
            }

            @Override
            public void onRuntimeAction(String name, boolean isPressed, float tpf) {
                if (FrozenLandsFocusController.FOCUS_TOGGLE_MAPPING.equals(name) && isPressed) {
                    focusController.toggleApplicationFocus();
                }
            }
        });
    }

    private Kernel kernelForFocus() {
        return startup == null ? null : startup.kernel();
    }

    public static void main(String[] args) {
        if (FrozenLandsLauncherBootstrap.relaunchWithVmOptionsIfRequired(FrozenLands.class, args)) {
            return;
        }
        LoggingBootstrap.install();
        FrozenApp frozenApp = FrozenApp.frozenLands();
        FrozenLands app = new FrozenLands();
        AppSettings cfg = frozenApp.requestSettings();
        if (cfg == null) {
            shutdownAfterPrelaunchCancel();
            return;
        }
        frozenApp.applyWindowIcon(cfg);
        app.setSettings(cfg);
        app.start();
    }

    private static void shutdownAfterPrelaunchCancel() {
        System.exit(0);
    }

    @Override
    public EngineEventBus getEventBus() {
        return startup.eventBus();
    }

    @Override
    public void reportStartupProgress(String stage, float progress) {
        EngineApplicationHost.super.reportStartupProgress(stage, progress);
    }

    @Override
    public void showUiScreen(UiScreenModel model) {
        startup.showUiScreen(model);
    }

    @Override
    public void updateUiScreen(UiScreenModel model) {
        startup.updateUiScreen(model);
    }

    @Override
    public void hideUiScreen(String screenId) {
        startup.hideUiScreen(screenId);
    }

    public Node getRootNode() {
        return rootNode;
    }

    public Node getGuiNode() {
        return guiNode;
    }

    public BulletAppState getBulletAppState() {
        return startup.bulletAppState();
    }

    public FilterPostProcessor getFpp() {
        return startup.fpp();
    }

    public Map getCONFIG() {
        return startup.config();
    }

    public Logger getLogger() {
        return logger;
    }

    @Override public LostFocusBehavior getLostFocusBehavior() { return runtime.getLostFocusBehavior(); }
    @Override public void setLostFocusBehavior(LostFocusBehavior behavior) { runtime.setLostFocusBehavior(behavior); }
    @Override public boolean isPauseOnLostFocus() { return runtime.isPauseOnLostFocus(); }
    @Override public void setPauseOnLostFocus(boolean pauseOnLostFocus) { runtime.setPauseOnLostFocus(pauseOnLostFocus); }
    @Override public void setSettings(AppSettings settings) { runtime.setSettings(settings); }
    @Override public void setTimer(Timer timer) { runtime.setTimer(timer); }
    @Override public Timer getTimer() { return runtime.getTimer(); }
    @Override public AssetManager getAssetManager() { return runtime.getAssetManager(); }
    @Override public InputManager getInputManager() { return runtime.getInputManager(); }
    @Override public AppStateManager getStateManager() { return runtime.getStateManager(); }
    @Override public RenderManager getRenderManager() { return runtime.getRenderManager(); }
    @Override public Renderer getRenderer() { return runtime.getRenderer(); }
    @Override public AudioRenderer getAudioRenderer() { return runtime.getAudioRenderer(); }
    @Override public Listener getListener() { return runtime.getListener(); }
    @Override public JmeContext getContext() { return runtime.getContext(); }
    @Override public Camera getCamera() { return runtime.getCamera(); }
    @Override public void start() { runtime.start(); }
    @Override public void start(boolean waitFor) { runtime.start(waitFor); }
    @Override public void setAppProfiler(AppProfiler profiler) { runtime.setAppProfiler(profiler); }
    @Override public AppProfiler getAppProfiler() { return runtime.getAppProfiler(); }
    @Override public void restart() { runtime.restart(); }
    @Override public void stop() { runtime.stop(); }
    @Override public void stop(boolean waitFor) { runtime.stop(waitFor); }
    @Override public <V> Future<V> enqueue(Callable<V> callable) { return runtime.enqueue(callable); }
    @Override public void enqueue(Runnable runnable) { runtime.enqueue(runnable); }
    @Override public ViewPort getGuiViewPort() { return runtime.getGuiViewPort(); }
    @Override public ViewPort getViewPort() { return runtime.getViewPort(); }

    public void requestRuntimeInputFocus(String reason) {
        focusController.requestRuntimeInputFocus(reason);
    }
}
