package org.takesome.frozenlands.engine.host.hframe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takesome.hframe.HApplication;
import org.takesome.hframe.HBorderLayout;
import org.takesome.hframe.HComponent;
import org.takesome.hframe.HFrame;
import org.takesome.hframe.HFrameCloseDecision;
import org.takesome.hframe.HFrameCloseOperation;
import org.takesome.hframe.HFrameColor;
import org.takesome.hframe.HFrameEvent;
import org.takesome.hframe.HFrameOptions;
import org.takesome.hframe.HFrameWindowAdapter;
import org.takesome.hframe.HInsets;

/**
 * Optional HFrame startup integration for FrozenLands.
 *
 * <p>This is deliberately a startup/native-host probe, not yet a replacement for the JME window or the
 * existing HTML/Swing pre-launch dialog. It proves that the HFrame Java API and native backend can be
 * loaded by FrozenLands without making the main game runtime depend on it by default.</p>
 */
public final class FrozenLandsHFrameBootstrap {
    public static final String ENABLED_PROPERTY = "frozenlands.hframe.enabled";
    public static final String STRICT_PROPERTY = "frozenlands.hframe.strict";
    public static final String TITLE_PROPERTY = "frozenlands.hframe.title";
    public static final String WIDTH_PROPERTY = "frozenlands.hframe.width";
    public static final String HEIGHT_PROPERTY = "frozenlands.hframe.height";
    public static final String PROBE_MILLIS_PROPERTY = "frozenlands.hframe.probeMillis";
    public static final String KEEP_OPEN_PROPERTY = "frozenlands.hframe.keepOpen";

    private static final Logger LOGGER = LoggerFactory.getLogger(FrozenLandsHFrameBootstrap.class);
    private static final int DEFAULT_WIDTH = 720;
    private static final int DEFAULT_HEIGHT = 320;
    private static final int DEFAULT_PROBE_MILLIS = 650;

    private FrozenLandsHFrameBootstrap() {
    }

    public static void showStartupProbeIfEnabled() {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }

        try {
            showStartupProbe();
        } catch (Throwable error) {
            if (Boolean.getBoolean(STRICT_PROPERTY)) {
                throw new IllegalStateException("FrozenLands HFrame startup probe failed", error);
            }
            LOGGER.warn("FrozenLands HFrame startup probe failed; continuing with the standard launcher path.", error);
        }
    }

    private static void showStartupProbe() throws InterruptedException {
        HApplication.setQuitOnLastFrameDisposed(false);

        String title = System.getProperty(TITLE_PROPERTY, "FrozenLands // HFrame Host");
        int width = integerProperty(WIDTH_PROPERTY, DEFAULT_WIDTH, 320, 4096);
        int height = integerProperty(HEIGHT_PROPERTY, DEFAULT_HEIGHT, 180, 2160);
        int probeMillis = integerProperty(PROBE_MILLIS_PROPERTY, DEFAULT_PROBE_MILLIS, 0, 30_000);
        boolean keepOpen = Boolean.getBoolean(KEEP_OPEN_PROPERTY);

        HFrame frame = new HFrame(
                HFrameOptions.builder()
                        .title(title)
                        .size(width, height)
                        .minimumSize(320, 180)
                        .centerOnScreen(true)
                        .resizable(false)
                        .decorated(true)
                        .alwaysOnTop(false)
                        .backgroundColor(HFrameColor.rgb(8, 12, 18))
                        .closeOperation(HFrameCloseOperation.DISPOSE)
                        .build()
        );

        frame.getContentPane().setLayout(new HBorderLayout(0, 0, new HInsets(18, 18, 18, 18)));
        frame.add(new HComponent().id("frozenlands-hframe-status").preferredSize(0, 64), HBorderLayout.CENTER);
        frame.addCloseHandler(request -> HFrameCloseDecision.DISPOSE);
        frame.addWindowListener(new HFrameWindowAdapter() {
            @Override
            public void frameShown(HFrameEvent event) {
                LOGGER.info("HFrame startup probe shown: {}", frame.snapshot());
            }

            @Override
            public void frameClosed(HFrameEvent event) {
                LOGGER.info("HFrame startup probe closed: {}", frame.snapshot());
            }
        });

        frame.setVisible(true);
        pollFor(probeMillis);

        if (keepOpen) {
            LOGGER.warn("{}=true requested, but persistent HFrame/JME coexistence is not enabled yet; disposing startup probe.", KEEP_OPEN_PROPERTY);
        }

        frame.dispose();
        HApplication.pollEvents();
    }

    private static void pollFor(int millis) throws InterruptedException {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        do {
            HApplication.pollEvents();
            Thread.sleep(16L);
        } while (System.nanoTime() < deadline);
    }

    private static int integerProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
