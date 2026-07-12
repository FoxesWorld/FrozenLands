package org.takesome.frozenlands.engine.player.menu;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import org.takesome.frozenlands.engine.player.Player;

import java.util.Map;

public final class PlayerMenuState extends BaseAppState implements RawInputListener {
    private final Player player;
    private final PlayerMenuView view;
    private AutoCloseable focusSubscription;
    private boolean open;
    private boolean draggingLookScale;

    public PlayerMenuState(Player player) {
        this.player = player;
        this.view = new PlayerMenuView(player.getAssetManager(), player.getCamera(), player.getGuiNode());
    }

    @Override
    protected void initialize(Application application) {
        view.initialize();
        view.setVisible(false);
        player.getInputManager().addRawInputListener(this);
        focusSubscription = player.subscribeEvent("engine.application.focus.changed", this::onApplicationFocusChanged, true);
    }

    @Override
    protected void cleanup(Application application) {
        player.getInputManager().removeRawInputListener(this);
        close(focusSubscription);
        focusSubscription = null;
        view.dispose();
    }

    @Override protected void onEnable() { }
    @Override protected void onDisable() { }

    @Override
    public void update(float tpf) {
        if (open) {
            syncView();
        }
    }

    @Override public void beginInput() { }
    @Override public void endInput() { }
    @Override public void onJoyAxisEvent(JoyAxisEvent event) { }
    @Override public void onJoyButtonEvent(JoyButtonEvent event) { }
    @Override public void onKeyEvent(KeyInputEvent event) { }
    @Override public void onTouchEvent(TouchEvent event) { }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        if (!open) {
            return;
        }
        if (draggingLookScale) {
            setLookScaleFromMouse(event.getX());
        }
        event.setConsumed();
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (!open || event.getButtonIndex() != MouseInput.BUTTON_LEFT) {
            return;
        }

        if (event.isPressed()) {
            if (view.lookScaleSliderContains(event.getX(), event.getY())) {
                draggingLookScale = true;
                setLookScaleFromMouse(event.getX());
            }
        } else {
            draggingLookScale = false;
        }
        event.setConsumed();
    }

    private void onApplicationFocusChanged(Map<String, Object> event) {
        Map<?, ?> payload = payload(event);
        if (bool(payload, "htmlUi")) {
            setOpen(false);
            return;
        }
        setOpen(bool(payload, "paused"));
    }

    private void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        draggingLookScale = false;
        syncView();
        view.setVisible(open);
    }

    private void syncView() {
        var options = player.getPlayerOptions();
        view.setLookScale(
                options.getMouseSensitivity(),
                options.getMinMouseSensitivity(),
                options.getMaxMouseSensitivity()
        );
    }

    private void setLookScaleFromMouse(float mouseX) {
        var options = player.getPlayerOptions();
        float next = view.lookScaleForMouseX(
                mouseX,
                options.getMinMouseSensitivity(),
                options.getMaxMouseSensitivity()
        );
        options.setMouseSensitivity(next);
        syncView();
    }

    private Map<?, ?> payload(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        return payload instanceof Map<?, ?> map ? map : Map.of();
    }

    private boolean bool(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
