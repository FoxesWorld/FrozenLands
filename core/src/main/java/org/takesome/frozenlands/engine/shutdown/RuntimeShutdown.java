package org.takesome.frozenlands.engine.shutdown;

import com.jme3.app.Application;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;

import java.awt.EventQueue;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RuntimeShutdown {
    private static final AtomicBoolean REQUESTED = new AtomicBoolean(false);
    private static final long HARD_EXIT_DELAY_MILLIS = 1500L;

    private RuntimeShutdown() {
    }

    public static Map<String, Object> request(EngineContext context, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "runtime-exit" : reason;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requested", true);
        payload.put("reason", safeReason);
        payload.put("hardExitDelayMillis", HARD_EXIT_DELAY_MILLIS);
        payload.put("firstRequest", REQUESTED.compareAndSet(false, true));

        if (!Boolean.TRUE.equals(payload.get("firstRequest"))) {
            return payload;
        }

        publish(context, EngineEventTopics.RUNTIME_EXIT_REQUESTED, payload);
        closeAwtWindows(context, safeReason);
        stopApplication(context, safeReason);
        startHardExitFallback(context, safeReason);
        publish(context, EngineEventTopics.RUNTIME_EXIT_STARTED, payload);
        return payload;
    }

    private static void stopApplication(EngineContext context, String reason) {
        if (context == null || context.appStateManager() == null) {
            return;
        }
        Application application = context.appStateManager().getApplication();
        if (application == null) {
            return;
        }
        try {
            context.getLogger().info("Runtime shutdown stopping jME application reason={}", reason);
            application.stop(false);
        } catch (RuntimeException exception) {
            context.getLogger().error("Runtime shutdown jME stop failed reason={} message={}", reason, exception.toString(), exception);
        }
    }

    private static void closeAwtWindows(EngineContext context, String reason) {
        publish(context, EngineEventTopics.RUNTIME_EXIT_AWT_DISPOSE_REQUESTED, Map.of("reason", reason));
        try {
            EventQueue.invokeLater(() -> {
                for (Window window : Window.getWindows()) {
                    try {
                        window.setVisible(false);
                        window.dispose();
                    } catch (RuntimeException ignored) {
                        // best effort shutdown path
                    }
                }
            });
        } catch (RuntimeException exception) {
            if (context != null) {
                context.getLogger().warn("Runtime shutdown AWT dispose failed reason={} message={}", reason, exception.toString());
            }
        }
    }

    private static void startHardExitFallback(EngineContext context, String reason) {
        Thread fallback = new Thread(() -> {
            try {
                Thread.sleep(HARD_EXIT_DELAY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (context != null) {
                context.getLogger().warn("Runtime shutdown hard exit fallback reason={} delayMs={}", reason, HARD_EXIT_DELAY_MILLIS);
            }
            System.exit(0);
        }, "FrozenLands-Runtime-Hard-Exit");
        fallback.setDaemon(false);
        fallback.start();
    }

    private static void publish(EngineContext context, String topic, Map<String, Object> payload) {
        if (context == null || context.getModuleRegistry() == null) {
            return;
        }
        try {
            context.getModuleRegistry().publishEvent(topic, payload == null ? Map.of() : payload);
        } catch (RuntimeException exception) {
            if (context.getLogger() != null) {
                context.getLogger().warn("Runtime shutdown event publish failed topic={} message={}", topic, exception.toString());
            }
        }
    }
}
