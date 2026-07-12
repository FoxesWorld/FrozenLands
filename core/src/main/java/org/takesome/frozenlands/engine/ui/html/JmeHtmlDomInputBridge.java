package org.takesome.frozenlands.engine.ui.html;

import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import dev.takesome.htmldom.desktop.HtmlDomHitTestEngine;
import dev.takesome.htmldom.desktop.HtmlDomSwingPanel;
import dev.takesome.htmldom.dom.UiDomElement;
import org.takesome.frozenlands.engine.EngineContext;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public final class JmeHtmlDomInputBridge implements RawInputListener {
    private final EngineContext context;
    private final FrozenLandsHtmlUiRuntime runtime;
    private int lastMouseX;
    private int lastMouseY;
    private int awtButtonMask;
    private int pressedMouseX;
    private int pressedMouseY;
    private boolean pointerMovedWhilePressed;
    private boolean uiPointerCapture;

    public JmeHtmlDomInputBridge(EngineContext context, FrozenLandsHtmlUiRuntime runtime) {
        this.context = context;
        this.runtime = runtime;
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        if (event == null || !available()) {
            return;
        }
        boolean capturedMotion = uiPointerCapture && awtButtonMask != 0;
        if (event.isConsumed() && !capturedMotion) {
            return;
        }

        int x = clampX(event.getX());
        int y = toAwtY(event.getY());
        lastMouseX = x;
        lastMouseY = y;
        if (awtButtonMask != 0
                && (Math.abs(x - pressedMouseX) > 3
                || Math.abs(y - pressedMouseY) > 3)) {
            pointerMovedWhilePressed = true;
        }

        HtmlDomSwingPanel panel = runtime.panel();

        int when = now();
        int modifiers = awtButtonMask;
        int motionEventId = awtMotionEventId(modifiers);
        dispatch(panel, new MouseEvent(
                panel,
                motionEventId,
                when,
                modifiers,
                x,
                y,
                0,
                false,
                MouseEvent.NOBUTTON
        ));
        if (motionEventId == MouseEvent.MOUSE_DRAGGED
                && (uiPointerCapture || capturesPointer(x, y))) {
            event.setConsumed();
        }

        int wheelDelta = event.getDeltaWheel();
        if (wheelDelta != 0) {
            int wheelRotation = wheelDelta > 0 ? 1 : -1;
            dispatch(panel, new MouseWheelEvent(
                    panel,
                    MouseEvent.MOUSE_WHEEL,
                    when,
                    modifiers,
                    x,
                    y,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    wheelRotation
            ));
            if (capturesWheel(x, y)) {
                event.setConsumed();
            }
        }
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (event == null || !available()) {
            return;
        }
        if (event.isConsumed() && !uiPointerCapture) {
            return;
        }

        int x = clampX(event.getX());
        int y = toAwtY(event.getY());
        lastMouseX = x;
        lastMouseY = y;

        HtmlDomSwingPanel panel = runtime.panel();

        int awtButton = toAwtButton(event.getButtonIndex());
        int buttonMask = toAwtButtonMask(awtButton);
        if (event.isPressed()) {
            awtButtonMask |= buttonMask;
            pressedMouseX = x;
            pressedMouseY = y;
            pointerMovedWhilePressed = false;
            uiPointerCapture = capturesPointer(x, y);
        } else {
            awtButtonMask &= ~buttonMask;
        }

        int modifiers = awtButtonMask;
        int id = event.isPressed() ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
        int when = now();
        dispatch(panel, new MouseEvent(panel, id, when, modifiers, x, y, 1, false, awtButton));

        boolean directClickHandled = false;
        boolean capturedPointer = uiPointerCapture;
        if (event.isReleased() && !pointerMovedWhilePressed) {
            directClickHandled = dispatchUiPointerClick(x, y);
            dispatch(panel, new MouseEvent(
                    panel,
                    MouseEvent.MOUSE_CLICKED,
                    when,
                    modifiers,
                    x,
                    y,
                    1,
                    false,
                    awtButton
            ));
        }

        if (directClickHandled
                || capturedPointer
                || capturesPointer(x, y)) {
            event.setConsumed();
        }
        if (event.isReleased()) {
            uiPointerCapture = false;
            pointerMovedWhilePressed = false;
        }
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (!available() || event == null || event.isConsumed()) {
            return;
        }
        if (event.getKeyCode() == KeyInput.KEY_ESCAPE || event.getKeyCode() == KeyInput.KEY_F12 || event.getKeyCode() == KeyInput.KEY_F10) {
            return;
        }
        if (!capturesKeyboard()) {
            return;
        }

        HtmlDomSwingPanel panel = runtime.panel();
        int keyCode = toAwtKeyCode(event.getKeyCode());
        char keyChar = event.getKeyChar() == 0 ? KeyEvent.CHAR_UNDEFINED : event.getKeyChar();
        int id = event.isPressed() ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
        dispatch(panel, new KeyEvent(panel, id, now(), 0, keyCode, keyChar));

        if (event.isPressed() && keyChar != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(keyChar)) {
            dispatch(panel, new KeyEvent(panel, KeyEvent.KEY_TYPED, now(), 0, KeyEvent.VK_UNDEFINED, keyChar));
        }
        event.setConsumed();
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent event) {
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }

    private boolean available() {
        return runtime != null && runtime.panel() != null;
    }

    private boolean capturesKeyboard() {
        String screen = runtime.screen();
        return !"hud".equals(screen) || runtime.panel().dialogOpen();
    }

    private boolean capturesWheel(int x, int y) {
        if (!"hud".equals(runtime.screen())) {
            return true;
        }
        HtmlDomSwingPanel panel = runtime.panel();
        for (HtmlDomHitTestEngine.Hit hit : panel.hitTest().scrollHits()) {
            if (hit.contains(x, y)) {
                return true;
            }
        }
        return false;
    }


    private boolean dispatchUiPointerClick(int x, int y) {
        UiDomElement element = interactiveHitAt(x, y);
        if (element == null) {
            context.getLogger().debug("Html UI pointer click miss x={} y={} screen={}", x, y, runtime.screen());
            return false;
        }
        context.getLogger().info(
                "Html UI pointer click hit x={} y={} elementId={} action={} event={} screen={}",
                x,
                y,
                element.id(),
                element.data("action", ""),
                element.data("event", ""),
                runtime.screen()
        );
        return runtime.emitPointerClick(element, "ui-pointer-click");
    }

    private boolean capturesPointer(int x, int y) {
        if (!"hud".equals(runtime.screen())) {
            return true;
        }
        return interactiveHitAt(x, y) != null;
    }

    private UiDomElement interactiveHitAt(int x, int y) {
        HtmlDomSwingPanel panel = runtime.panel();
        var hits = panel.hitTest().hits();
        for (int index = hits.size() - 1; index >= 0; index--) {
            HtmlDomHitTestEngine.Hit hit = hits.get(index);
            if (!hit.contains(x, y)) {
                continue;
            }
            UiDomElement element = hit.element();
            if (element == null) {
                continue;
            }
            if (!element.data("action", "").isBlank() || panel.clickable(element)) {
                return element;
            }
        }
        return null;
    }

    private void dispatch(Component component, java.awt.AWTEvent event) {
        try {
            if (component instanceof HtmlDomSwingPanel panel) {
                dispatchToPanelListeners(panel, event);
            } else {
                component.dispatchEvent(event);
            }
        } catch (RuntimeException exception) {
            context.getLogger().error(
                    "Html UI input dispatch failed event={} message={}",
                    event,
                    exception.toString(),
                    exception
            );
        }
    }

    private void dispatchToPanelListeners(
            HtmlDomSwingPanel panel,
            java.awt.AWTEvent event
    ) {
        if (event instanceof MouseWheelEvent wheelEvent) {
            for (MouseWheelListener listener : panel.getMouseWheelListeners()) {
                listener.mouseWheelMoved(wheelEvent);
            }
            return;
        }
        if (event instanceof MouseEvent mouseEvent) {
            dispatchMouseEvent(panel, mouseEvent);
            return;
        }
        if (event instanceof KeyEvent keyEvent) {
            for (KeyListener listener : panel.getKeyListeners()) {
                switch (keyEvent.getID()) {
                    case KeyEvent.KEY_PRESSED ->
                            listener.keyPressed(keyEvent);
                    case KeyEvent.KEY_RELEASED ->
                            listener.keyReleased(keyEvent);
                    case KeyEvent.KEY_TYPED ->
                            listener.keyTyped(keyEvent);
                    default -> {
                    }
                }
            }
            return;
        }
        panel.dispatchEvent(event);
    }

    static void dispatchMouseEvent(
            HtmlDomSwingPanel panel,
            MouseEvent event
    ) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_MOVED -> {
                for (MouseMotionListener listener
                        : panel.getMouseMotionListeners()) {
                    listener.mouseMoved(event);
                }
            }
            case MouseEvent.MOUSE_DRAGGED -> {
                for (MouseMotionListener listener
                        : panel.getMouseMotionListeners()) {
                    listener.mouseDragged(event);
                }
            }
            case MouseEvent.MOUSE_PRESSED -> {
                for (MouseListener listener : panel.getMouseListeners()) {
                    listener.mousePressed(event);
                }
            }
            case MouseEvent.MOUSE_RELEASED -> {
                for (MouseListener listener : panel.getMouseListeners()) {
                    listener.mouseReleased(event);
                }
            }
            case MouseEvent.MOUSE_CLICKED -> {
                for (MouseListener listener : panel.getMouseListeners()) {
                    listener.mouseClicked(event);
                }
            }
            case MouseEvent.MOUSE_ENTERED -> {
                for (MouseListener listener : panel.getMouseListeners()) {
                    listener.mouseEntered(event);
                }
            }
            case MouseEvent.MOUSE_EXITED -> {
                for (MouseListener listener : panel.getMouseListeners()) {
                    listener.mouseExited(event);
                }
            }
            default -> {
            }
        }
    }

    static int awtMotionEventId(int modifiers) {
        int buttonMasks = MouseEvent.BUTTON1_DOWN_MASK
                | MouseEvent.BUTTON2_DOWN_MASK
                | MouseEvent.BUTTON3_DOWN_MASK;
        return (modifiers & buttonMasks) != 0
                ? MouseEvent.MOUSE_DRAGGED
                : MouseEvent.MOUSE_MOVED;
    }

    private int clampX(int x) {
        return Math.max(0, Math.min(width() - 1, x));
    }

    private int toAwtY(int jmeY) {
        int height = height();
        return Math.max(0, Math.min(height - 1, height - 1 - jmeY));
    }

    private int width() {
        return context.getCamera() == null ? 1 : Math.max(1, context.getCamera().getWidth());
    }

    private int height() {
        return context.getCamera() == null ? 1 : Math.max(1, context.getCamera().getHeight());
    }

    private int now() {
        return (int) (System.currentTimeMillis() & 0x7fffffff);
    }

    private int toAwtButton(int jmeButton) {
        return switch (jmeButton) {
            case 0 -> MouseEvent.BUTTON1;
            case 1 -> MouseEvent.BUTTON3;
            case 2 -> MouseEvent.BUTTON2;
            default -> MouseEvent.NOBUTTON;
        };
    }

    private int toAwtButtonMask(int awtButton) {
        return switch (awtButton) {
            case MouseEvent.BUTTON1 -> MouseEvent.BUTTON1_DOWN_MASK;
            case MouseEvent.BUTTON2 -> MouseEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3 -> MouseEvent.BUTTON3_DOWN_MASK;
            default -> 0;
        };
    }

    private int toAwtKeyCode(int jmeKey) {
        return switch (jmeKey) {
            case KeyInput.KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case KeyInput.KEY_TAB -> KeyEvent.VK_TAB;
            case KeyInput.KEY_RETURN -> KeyEvent.VK_ENTER;
            case KeyInput.KEY_BACK -> KeyEvent.VK_BACK_SPACE;
            case KeyInput.KEY_DELETE -> KeyEvent.VK_DELETE;
            case KeyInput.KEY_INSERT -> KeyEvent.VK_INSERT;
            case KeyInput.KEY_HOME -> KeyEvent.VK_HOME;
            case KeyInput.KEY_END -> KeyEvent.VK_END;
            case KeyInput.KEY_PGUP -> KeyEvent.VK_PAGE_UP;
            case KeyInput.KEY_PGDN -> KeyEvent.VK_PAGE_DOWN;
            case KeyInput.KEY_UP -> KeyEvent.VK_UP;
            case KeyInput.KEY_DOWN -> KeyEvent.VK_DOWN;
            case KeyInput.KEY_LEFT -> KeyEvent.VK_LEFT;
            case KeyInput.KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case KeyInput.KEY_SPACE -> KeyEvent.VK_SPACE;
            case KeyInput.KEY_F1 -> KeyEvent.VK_F1;
            case KeyInput.KEY_F2 -> KeyEvent.VK_F2;
            case KeyInput.KEY_F3 -> KeyEvent.VK_F3;
            case KeyInput.KEY_F4 -> KeyEvent.VK_F4;
            case KeyInput.KEY_F5 -> KeyEvent.VK_F5;
            case KeyInput.KEY_F6 -> KeyEvent.VK_F6;
            case KeyInput.KEY_F7 -> KeyEvent.VK_F7;
            case KeyInput.KEY_F8 -> KeyEvent.VK_F8;
            case KeyInput.KEY_F9 -> KeyEvent.VK_F9;
            case KeyInput.KEY_F10 -> KeyEvent.VK_F10;
            case KeyInput.KEY_F11 -> KeyEvent.VK_F11;
            case KeyInput.KEY_F12 -> KeyEvent.VK_F12;
            case KeyInput.KEY_A -> KeyEvent.VK_A;
            case KeyInput.KEY_B -> KeyEvent.VK_B;
            case KeyInput.KEY_C -> KeyEvent.VK_C;
            case KeyInput.KEY_D -> KeyEvent.VK_D;
            case KeyInput.KEY_E -> KeyEvent.VK_E;
            case KeyInput.KEY_F -> KeyEvent.VK_F;
            case KeyInput.KEY_G -> KeyEvent.VK_G;
            case KeyInput.KEY_H -> KeyEvent.VK_H;
            case KeyInput.KEY_I -> KeyEvent.VK_I;
            case KeyInput.KEY_J -> KeyEvent.VK_J;
            case KeyInput.KEY_K -> KeyEvent.VK_K;
            case KeyInput.KEY_L -> KeyEvent.VK_L;
            case KeyInput.KEY_M -> KeyEvent.VK_M;
            case KeyInput.KEY_N -> KeyEvent.VK_N;
            case KeyInput.KEY_O -> KeyEvent.VK_O;
            case KeyInput.KEY_P -> KeyEvent.VK_P;
            case KeyInput.KEY_Q -> KeyEvent.VK_Q;
            case KeyInput.KEY_R -> KeyEvent.VK_R;
            case KeyInput.KEY_S -> KeyEvent.VK_S;
            case KeyInput.KEY_T -> KeyEvent.VK_T;
            case KeyInput.KEY_U -> KeyEvent.VK_U;
            case KeyInput.KEY_V -> KeyEvent.VK_V;
            case KeyInput.KEY_W -> KeyEvent.VK_W;
            case KeyInput.KEY_X -> KeyEvent.VK_X;
            case KeyInput.KEY_Y -> KeyEvent.VK_Y;
            case KeyInput.KEY_Z -> KeyEvent.VK_Z;
            case KeyInput.KEY_0 -> KeyEvent.VK_0;
            case KeyInput.KEY_1 -> KeyEvent.VK_1;
            case KeyInput.KEY_2 -> KeyEvent.VK_2;
            case KeyInput.KEY_3 -> KeyEvent.VK_3;
            case KeyInput.KEY_4 -> KeyEvent.VK_4;
            case KeyInput.KEY_5 -> KeyEvent.VK_5;
            case KeyInput.KEY_6 -> KeyEvent.VK_6;
            case KeyInput.KEY_7 -> KeyEvent.VK_7;
            case KeyInput.KEY_8 -> KeyEvent.VK_8;
            case KeyInput.KEY_9 -> KeyEvent.VK_9;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }
}
