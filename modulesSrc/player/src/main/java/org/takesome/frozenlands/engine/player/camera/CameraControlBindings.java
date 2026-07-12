package org.takesome.frozenlands.engine.player.camera;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.util.Map;
import java.util.function.Consumer;

/** Owns camera-related event subscriptions and input gating. */
final class CameraControlBindings implements AutoCloseable {
    private final EngineContext context;
    private final int playerRef;
    private final CameraOrbitState orbit;

    private AutoCloseable pauseSubscription;
    private AutoCloseable consoleSubscription;
    private AutoCloseable lookSubscription;
    private boolean pauseRequested;
    private boolean consoleOpen;
    private boolean connected;

    CameraControlBindings(
            EngineContext context,
            int playerRef,
            CameraOrbitState orbit
    ) {
        this.context = context;
        this.playerRef = playerRef;
        this.orbit = orbit;
    }

    void connect() {
        if (connected || context == null) {
            return;
        }
        connected = true;
        pauseSubscription = subscribe(
                EngineEventTopics.CAMERA_FOLLOW_PAUSE_REQUESTED,
                this::onPauseRequested
        );
        consoleSubscription = subscribe(
                EngineEventTopics.CONSOLE_VISIBILITY_CHANGED,
                this::onConsoleVisibilityChanged
        );
        lookSubscription = subscribe(
                EngineEventTopics.PLAYER_LOOK_INTENT,
                this::onLookIntent
        );
    }

    boolean paused() {
        return pauseRequested || consoleOpen;
    }

    private AutoCloseable subscribe(
            String topic,
            Consumer<Map<String, Object>> listener
    ) {
        return context.getModuleRegistry().getEventBus().subscribe(
                topic,
                listener,
                true
        );
    }

    private void onPauseRequested(Map<String, Object> event) {
        pauseRequested = Boolean.TRUE.equals(payload(event).get("paused"));
    }

    private void onConsoleVisibilityChanged(Map<String, Object> event) {
        consoleOpen = Boolean.TRUE.equals(payload(event).get("open"));
    }

    private void onLookIntent(Map<String, Object> event) {
        Map<String, Object> payload = payload(event);
        if (longNumber(payload, "playerRef", -1L) != playerRef) {
            return;
        }
        orbit.setTarget(
                number(payload, "yaw", orbit.yaw()),
                number(payload, "pitch", orbit.pitch())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object value = event == null ? null : event.get("payload");
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    private float number(Map<String, Object> values, String key, float fallback) {
        Object value = values.get(key);
        return value instanceof Number number
                ? number.floatValue()
                : value == null ? fallback : Float.parseFloat(String.valueOf(value));
    }

    private long longNumber(Map<String, Object> values, String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number number
                ? number.longValue()
                : value == null ? fallback : Long.parseLong(String.valueOf(value));
    }

    @Override
    public void close() {
        closeQuietly(pauseSubscription);
        closeQuietly(consoleSubscription);
        closeQuietly(lookSubscription);
        pauseSubscription = null;
        consoleSubscription = null;
        lookSubscription = null;
        connected = false;
        pauseRequested = false;
        consoleOpen = false;
    }

    private void closeQuietly(AutoCloseable subscription) {
        if (subscription == null) {
            return;
        }
        try {
            subscription.close();
        } catch (Exception ignored) {
        }
    }
}
