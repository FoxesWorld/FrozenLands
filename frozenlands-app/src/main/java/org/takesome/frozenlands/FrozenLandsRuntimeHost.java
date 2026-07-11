package org.takesome.frozenlands;

import com.jme3.app.LegacyApplication;
import com.jme3.input.controls.ActionListener;

final class FrozenLandsRuntimeHost extends LegacyApplication implements ActionListener {
    interface Callbacks {
        void initializeRuntimeHost();
        void updateRuntimeHost();
        void destroyRuntimeHost();
        void onRuntimeAction(String name, boolean isPressed, float tpf);
    }

    private Callbacks callbacks = new Callbacks() {
        @Override public void initializeRuntimeHost() { }
        @Override public void updateRuntimeHost() { }
        @Override public void destroyRuntimeHost() { }
        @Override public void onRuntimeAction(String name, boolean isPressed, float tpf) { }
    };

    void setCallbacks(Callbacks callbacks) {
        if (callbacks != null) {
            this.callbacks = callbacks;
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        callbacks.initializeRuntimeHost();
    }

    @Override
    public void update() {
        super.update();
        callbacks.updateRuntimeHost();
    }

    @Override
    public void destroy() {
        callbacks.destroyRuntimeHost();
        super.destroy();
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        callbacks.onRuntimeAction(name, isPressed, tpf);
    }

    void enableInput() {
        inputEnabled = true;
    }

    void requestInputFocus() {
        gainFocus();
        enableInput();
        if (inputManager != null) {
            inputManager.setCursorVisible(true);
        }
    }
}
