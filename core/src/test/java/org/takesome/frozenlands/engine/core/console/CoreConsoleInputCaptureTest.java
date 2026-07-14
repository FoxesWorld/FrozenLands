package org.takesome.frozenlands.engine.core.console;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.testing.TestEngineContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoreConsoleInputCaptureTest {
    @Test
    void openConsoleConsumesMouseAndGameplayKeyboardInput() {
        TestEngineContext context = new TestEngineContext();
        CoreConsoleState console = new CoreConsoleState(context);
        console.setOpen(true);

        MouseMotionEvent motion = new MouseMotionEvent(100, 100, 8, -4, 0, 0);
        console.onMouseMotionEvent(motion);
        assertTrue(motion.isConsumed());

        MouseButtonEvent attackPress = new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 100, 100);
        console.onMouseButtonEvent(attackPress);
        assertTrue(attackPress.isConsumed());

        MouseButtonEvent attackRelease = new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 100, 100);
        console.onMouseButtonEvent(attackRelease);
        assertTrue(attackRelease.isConsumed());

        MouseButtonEvent secondary = new MouseButtonEvent(MouseInput.BUTTON_RIGHT, true, 100, 100);
        console.onMouseButtonEvent(secondary);
        assertTrue(secondary.isConsumed());

        KeyInputEvent movePress = new KeyInputEvent(KeyInput.KEY_W, 'w', true, false);
        console.onKeyEvent(movePress);
        assertTrue(movePress.isConsumed());

        KeyInputEvent moveRelease = new KeyInputEvent(KeyInput.KEY_W, 'w', false, false);
        console.onKeyEvent(moveRelease);
        assertTrue(moveRelease.isConsumed());

        KeyInputEvent toggle = new KeyInputEvent(KeyInput.KEY_F1, '\0', true, false);
        console.onKeyEvent(toggle);
        assertFalse(toggle.isConsumed(), "F1 must reach the mapped toggle listener");
    }

    @Test
    void closedConsoleDoesNotConsumeInputAndPublishesVisibility() {
        TestEngineContext context = new TestEngineContext();
        CoreConsoleState console = new CoreConsoleState(context);
        console.setOpen(true);
        console.setOpen(false);

        MouseMotionEvent motion = new MouseMotionEvent(100, 100, 8, -4, 0, 0);
        console.onMouseMotionEvent(motion);
        assertFalse(motion.isConsumed());

        Map<String, Object> latest = context.modules()
                .getEventBus()
                .latest(EngineEventTopics.CONSOLE_VISIBILITY_CHANGED);
        assertFalse(latest.isEmpty());
        assertEquals(false, payload(latest).get("open"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object value = event.get("payload");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
