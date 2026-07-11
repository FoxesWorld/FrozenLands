package org.takesome.frozenlands;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.KeyTrigger;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.ui.html.HtmlUiState;

import java.util.Map;
import java.util.function.Supplier;

final class FrozenLandsFocusController {
    static final String FOCUS_TOGGLE_MAPPING = "engine.application.focus.toggle";

    private final FrozenLandsRuntimeHost runtime;
    private final Logger logger;
    private final Supplier<Kernel> kernelSupplier;
    private boolean focusPaused;

    FrozenLandsFocusController(FrozenLandsRuntimeHost runtime, Logger logger, Supplier<Kernel> kernelSupplier) {
        this.runtime = runtime;
        this.logger = logger;
        this.kernelSupplier = kernelSupplier;
    }

    void installFocusToggleMapping() {
        InputManager inputManager = runtime.getInputManager();
        if (!inputManager.hasMapping(FOCUS_TOGGLE_MAPPING)) {
            inputManager.addMapping(FOCUS_TOGGLE_MAPPING, new KeyTrigger(KeyInput.KEY_ESCAPE));
        }
        inputManager.addListener(runtime, FOCUS_TOGGLE_MAPPING);
    }

    void cleanupFocusToggleMapping() {
        InputManager inputManager = runtime.getInputManager();
        if (inputManager == null) {
            return;
        }
        inputManager.removeListener(runtime);
        if (inputManager.hasMapping(FOCUS_TOGGLE_MAPPING)) {
            inputManager.deleteMapping(FOCUS_TOGGLE_MAPPING);
        }
    }

    void requestRuntimeInputFocus(String reason) {
        runtime.requestInputFocus();
        logger.info("Runtime input focus requested reason={}", reason);
    }

    void toggleApplicationFocus() {
        HtmlUiState htmlUiState = runtime.getStateManager() == null ? null : runtime.getStateManager().getState(HtmlUiState.class);
        if (htmlUiState != null && htmlUiState.handleEscape()) {
            return;
        }
        setApplicationFocusPaused(!focusPaused, "escape");
    }

    private void setApplicationFocusPaused(boolean paused, String reason) {
        if (focusPaused == paused) {
            return;
        }
        focusPaused = paused;
        boolean focused = !paused;
        boolean cursorVisible = paused;

        if (runtime.getInputManager() != null) {
            runtime.getInputManager().setCursorVisible(cursorVisible);
        }

        Kernel kernel = kernelSupplier.get();
        if (kernel != null) {
            kernel.getModuleRegistry().publishEvent(EngineEventTopics.APPLICATION_FOCUS_CHANGED, Map.of(
                    "focused", focused,
                    "paused", paused,
                    "reason", reason
            ));
            kernel.getModuleRegistry().publishEvent(EngineEventTopics.CURSOR_VISIBILITY_REQUESTED, Map.of("visible", cursorVisible));
            kernel.getModuleRegistry().publishEvent(EngineEventTopics.CAMERA_FOLLOW_PAUSE_REQUESTED, Map.of("paused", paused));
            kernel.getModuleRegistry().publishEvent(EngineEventTopics.CAMERA_LOOK_INPUT_ENABLED_REQUESTED, Map.of("enabled", focused));
            kernel.getModuleRegistry().publishEvent(EngineEventTopics.PLAYER_INPUT_ENABLED_REQUESTED, Map.of("enabled", focused));
        }

        logger.info("Application focus {} by {}", focused ? "resumed" : "paused", reason);
    }
}
