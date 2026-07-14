package org.takesome.frozenlands.engine;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.events.EngineStartupEvents;
import org.takesome.frozenlands.engine.ui.screen.UiScreenModel;

import java.util.Map;

public interface EngineApplicationHost {
    Node getRootNode();
    Node getGuiNode();
    ViewPort getViewPort();
    AppStateManager getStateManager();
    AssetManager getAssetManager();
    InputManager getInputManager();
    Camera getCamera();
    BulletAppState getBulletAppState();
    FilterPostProcessor getFpp();
    Map getCONFIG();
    Logger getLogger();
    EngineEventBus getEventBus();
    default void reportStartupProgress(String stage, float progress) {
        getEventBus().publishLive(
                EngineEventTopics.STARTUP_PROGRESS,
                EngineStartupEvents.progress(stage, progress),
                "engine.application"
        );
    }

    default void showUiScreen(UiScreenModel model) {
    }

    default void updateUiScreen(UiScreenModel model) {
    }

    default void hideUiScreen(String screenId) {
    }

    void stop();
}
