package org.takesome.frozenlands;

import com.jme3.system.AppSettings;
import dev.takesome.htmldom.desktop.HtmlDomConfig;
import dev.takesome.htmldom.desktop.HtmlDomSwingPanel;
import org.takesome.frozenlands.engine.ui.window.AbstractEngineJFrame;
import org.takesome.frozenlands.engine.ui.window.EngineFrameOptions;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;

final class HtmlPreLaunchSettingsDialog {
    private static final String PREFERENCES_KEY = "FrozenLands";
    private static final String PRELAUNCH_RESOURCE_ROOT = "org/takesome/frozenlands/engine/ui/prelaunch";
    private static final String PRELAUNCH_MODALITY_PROPERTY = "frozenlands.prelaunch.modality";
    private static final String PRELAUNCH_MODAL_PROPERTY = "frozenlands.prelaunch.modal";
    private static final String PRELAUNCH_SHOW_WINDOW_PROPERTY = "frozenlands.prelaunch.showWindow";
    private static final String PRELAUNCH_APPLY_SKIN_PROPERTY = "frozenlands.prelaunch.applySkin";
    private static final String PRELAUNCH_DEVTOOLS_INPUT_PROPERTY = "frozenlands.prelaunch.devToolsInput";
    private static final String PRELAUNCH_DEVTOOLS_AVAILABILITY_PROPERTY = "frozenlands.prelaunch.devTools";
    private static final String PRELAUNCH_DEVTOOLS_WINDOW_TYPE_PROPERTY = "frozenlands.prelaunch.devTools.windowType";
    private static final String PRELAUNCH_DEVTOOLS_Z_ORDER_PROPERTY = "frozenlands.prelaunch.devTools.zOrder";
    private static final String PRELAUNCH_DEVTOOLS_CLOSE_POLICY_PROPERTY = "frozenlands.prelaunch.devTools.closePolicy";
    private static final int[] SAMPLE_VALUES = {0, 2, 4, 8, 16};
    private static final String[] SAMPLE_LABELS = {"Off", "2x MSAA", "4x MSAA", "8x MSAA", "16x MSAA"};

    private HtmlPreLaunchSettingsDialog() {
    }

    static AppSettings request(String title, AppSettings initialSettings) {
        HtmlPreLaunchSettingsDialog.Controller controller = new Controller(title, initialSettings);
        return controller.show();
    }

    private static final class Controller {
        private final String title;
        private final AppSettings initialSettings;
        private final List<Resolution> resolutions;
        private final Resolution nativeResolution;
        private final LaunchWindowOptions windowOptions;
        private final AtomicReference<AppSettings> result = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final CountDownLatch completion = new CountDownLatch(1);

        private HtmlDomSwingPanel panel;
        private PreLaunchFrame frame;
        private SecondaryLoop completionLoop;
        private Point windowDragOffset;
        private int resolutionIndex;
        private int sampleIndex;
        private boolean fullscreen;
        private boolean centerWindow;
        private boolean vsync;
        private Preset preset = Preset.CUSTOM;

        private Controller(String title, AppSettings initialSettings) {
            this.title = title;
            this.initialSettings = Objects.requireNonNull(initialSettings, "initialSettings");
            this.nativeResolution = currentResolution();
            this.resolutions = availableResolutions(nativeResolution);
            this.windowOptions = LaunchWindowOptions.fromSystemProperties();
            applyInitialSettings(initialSettings);
        }

        private AppSettings show() {
            try {
                if (!windowOptions.frameOptions().showWindow()) {
                    return initialSettings;
                }
                String sourcePath = PRELAUNCH_RESOURCE_ROOT + "/prelaunch.html";
                panel = new HtmlDomSwingPanel(readHtml(), "", sourcePath, PRELAUNCH_RESOURCE_ROOT, windowOptions.htmlDomConfig());
                panel.setOpaque(false);
                panel.setBackground(new Color(0, 0, 0, 0));
                panel.setPreferredSize(new Dimension(960, 640));
                panel.setMinimumSize(new Dimension(860, 560));
                panel.setSize(960, 640);
                installActions();
                installWindowDrag();
                refresh();
                panel.ensureLayout();

                frame = new PreLaunchFrame("FrozenLands PreLaunch", "FrozenLands Launcher", windowOptions.frameOptions());
                frame.installCloseHandler(this::cancel);
                frame.applySkin(panel, new Dimension(960, 640), new Dimension(860, 560));
                installKeyboardShortcuts(frame);
                SwingUtilities.invokeLater(panel::requestFocusInWindow);
                frame.showEngineFrame();
                return awaitResult();
            } catch (RuntimeException | IOException exception) {
                throw new IllegalStateException("HTML pre-launch engine frame failed", exception);
            }
        }

        private void installKeyboardShortcuts(AbstractEngineJFrame frame) {
            frame.getRootPane().registerKeyboardAction(event -> cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            frame.getRootPane().registerKeyboardAction(event -> confirm(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        private void installActions() {
            on("preset-performance", () -> applyPreset(Preset.PERFORMANCE));
            on("preset-balanced", () -> applyPreset(Preset.BALANCED));
            on("preset-quality", () -> applyPreset(Preset.QUALITY));
            on("preset-fullscreen", () -> applyPreset(Preset.NATIVE_FULLSCREEN));
            on("resolution-prev", () -> stepResolution(-1));
            on("resolution-next", () -> stepResolution(1));
            on("msaa-prev", () -> stepSamples(-1));
            on("msaa-next", () -> stepSamples(1));
            on("fullscreen-toggle", this::toggleFullscreen);
            on("center-toggle", this::toggleCenterWindow);
            on("vsync-toggle", this::toggleVsync);
            on("prelaunch-reset", this::resetDefaults);
            on("prelaunch-cancel", this::cancel);
            on("prelaunch-launch", this::confirm);
            on("window-close", this::cancel);
        }

        private void installWindowDrag() {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || !insideDraggableChrome(event)) {
                        windowDragOffset = null;
                        return;
                    }
                    windowDragOffset = event.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (frame == null || windowDragOffset == null) {
                        return;
                    }
                    frame.setLocation(event.getXOnScreen() - windowDragOffset.x, event.getYOnScreen() - windowDragOffset.y);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    windowDragOffset = null;
                }
            };
            panel.addMouseListener(adapter);
            panel.addMouseMotionListener(adapter);
        }

        private boolean insideDraggableChrome(MouseEvent event) {
            if (event == null) {
                return false;
            }
            int x = event.getX();
            int y = event.getY();
            return y >= 10 && y <= 70 && x >= 14 && x < 882;
        }

        private void on(String id, Runnable action) {
            panel.addEventListener(id, "click", event -> {
                event.preventDefault();
                System.out.println("[PreLaunch] click id=" + id);
                action.run();
                panel.invalidateLayout();
                panel.ensureLayout();
                panel.repaintHost();
            });
        }

        private void applyInitialSettings(AppSettings settings) {
            Resolution selected = new Resolution(settings.getWidth(), settings.getHeight());
            resolutionIndex = Math.max(0, resolutions.indexOf(selected));
            if (resolutionIndex < 0) {
                resolutions.add(selected);
                resolutions.sort(Comparator.comparingInt((Resolution resolution) -> resolution.width()).thenComparingInt(Resolution::height));
                resolutionIndex = resolutions.indexOf(selected);
            }
            sampleIndex = sampleIndex(settings.getSamples());
            fullscreen = settings.isFullscreen();
            centerWindow = settings.getCenterWindow();
            vsync = settings.isVSync();
            if (fullscreen) {
                sampleIndex = 0;
                centerWindow = false;
            }
            preset = Preset.CUSTOM;
        }

        private void resetDefaults() {
            Resolution selected = nativeResolution;
            resolutionIndex = Math.max(0, resolutions.indexOf(selected));
            sampleIndex = 0;
            fullscreen = false;
            centerWindow = true;
            vsync = false;
            preset = Preset.CUSTOM;
            refresh();
        }

        private void applyPreset(Preset nextPreset) {
            preset = nextPreset;
            resolutionIndex = Math.max(0, resolutions.indexOf(nativeResolution));
            switch (nextPreset) {
                case PERFORMANCE -> {
                    fullscreen = false;
                    sampleIndex = 0;
                    centerWindow = true;
                    vsync = false;
                }
                case BALANCED -> {
                    fullscreen = false;
                    sampleIndex = sampleIndex(4);
                    centerWindow = true;
                    vsync = true;
                }
                case QUALITY -> {
                    fullscreen = false;
                    sampleIndex = sampleIndex(8);
                    centerWindow = true;
                    vsync = true;
                }
                case NATIVE_FULLSCREEN -> {
                    fullscreen = true;
                    sampleIndex = 0;
                    centerWindow = false;
                    vsync = true;
                }
                case CUSTOM -> {
                }
            }
            refresh();
        }

        private void stepResolution(int delta) {
            resolutionIndex = wrap(resolutionIndex + delta, resolutions.size());
            preset = Preset.CUSTOM;
            refresh();
        }

        private void stepSamples(int delta) {
            if (fullscreen) {
                return;
            }
            sampleIndex = wrap(sampleIndex + delta, SAMPLE_VALUES.length);
            preset = Preset.CUSTOM;
            refresh();
        }

        private void toggleFullscreen() {
            fullscreen = !fullscreen;
            if (fullscreen) {
                sampleIndex = 0;
                centerWindow = false;
            } else {
                centerWindow = true;
            }
            preset = Preset.CUSTOM;
            refresh();
        }

        private void toggleCenterWindow() {
            if (fullscreen) {
                return;
            }
            centerWindow = !centerWindow;
            preset = Preset.CUSTOM;
            refresh();
        }

        private void toggleVsync() {
            vsync = !vsync;
            preset = Preset.CUSTOM;
            refresh();
        }

        private void cancel() {
            finish(null);
        }

        private void confirm() {
            AppSettings settings = new AppSettings(true);
            settings.copyFrom(initialSettings);
            Resolution selected = selectedResolution();
            applyDisplayMode(settings, selected, fullscreen);
            settings.setFullscreen(fullscreen);
            settings.setCenterWindow(!fullscreen && centerWindow);
            settings.setSamples(fullscreen ? 0 : SAMPLE_VALUES[sampleIndex]);
            settings.setVSync(vsync);
            settings.setTitle(title);
            try {
                settings.save(PREFERENCES_KEY);
            } catch (BackingStoreException ignored) {
            }
            finish(settings);
        }

        private AppSettings awaitResult() {
            if (SwingUtilities.isEventDispatchThread()) {
                EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
                completionLoop = queue.createSecondaryLoop();
                if (completion.getCount() > 0) {
                    completionLoop.enter();
                }
            } else {
                try {
                    completion.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    finish(null);
                }
            }
            return result.get();
        }

        private void finish(AppSettings settings) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            result.set(settings);
            if (frame != null && frame.isDisplayable()) {
                frame.dispose();
            }
            completion.countDown();
            SecondaryLoop loop = completionLoop;
            if (loop != null) {
                loop.exit();
            }
        }

        private void refresh() {
            Resolution selected = selectedResolution();
            setText("resolution-value", selected.shortText() + (selected.equals(nativeResolution) ? " · Native" : ""));
            setText("msaa-value", fullscreen ? "Off" : SAMPLE_LABELS[sampleIndex]);
            setText("fullscreen-toggle", fullscreen ? "Fullscreen" : "Windowed");
            setText("center-toggle", fullscreen ? "Center window: Disabled" : "Center window: " + (centerWindow ? "On" : "Off"));
            setText("vsync-toggle", "VSync: " + (vsync ? "On" : "Off"));
            setText("prelaunch-summary", summary(selected));
            setSelected("preset-performance", preset == Preset.PERFORMANCE);
            setSelected("preset-balanced", preset == Preset.BALANCED);
            setSelected("preset-quality", preset == Preset.QUALITY);
            setSelected("preset-fullscreen", preset == Preset.NATIVE_FULLSCREEN);
            setEnabled("fullscreen-toggle", fullscreen);
            setEnabled("center-toggle", !fullscreen && centerWindow);
            setEnabled("vsync-toggle", vsync);
            setAttribute("center-toggle", "aria-disabled", fullscreen ? "true" : "false");
            panel.invalidateLayout();
            panel.ensureLayout();
            panel.repaintHost();
        }

        private String summary(Resolution selected) {
            String mode = fullscreen ? "Fullscreen" : "Windowed";
            String samples = fullscreen ? "MSAA off" : SAMPLE_LABELS[sampleIndex];
            String sync = vsync ? "VSync on" : "VSync off";
            return mode + " · " + selected.shortText() + " · " + samples + " · " + sync;
        }

        private Resolution selectedResolution() {
            return resolutions.get(Math.max(0, Math.min(resolutionIndex, resolutions.size() - 1)));
        }

        private void setText(String id, String value) {
            panel.document().getElementById(id).ifPresent(element -> {
                element.clearChildren();
                element.appendChild(element.ownerDocument().createText(value == null ? "" : value));
            });
        }

        private void setSelected(String id, boolean selected) {
            setAttribute(id, "data-selected", selected ? "true" : "false");
            setAttribute(id, "aria-pressed", selected ? "true" : "false");
        }

        private void setEnabled(String id, boolean enabled) {
            setAttribute(id, "data-enabled", enabled ? "true" : "false");
        }

        private void setAttribute(String id, String name, String value) {
            panel.document().getElementById(id).ifPresent(element -> element.setAttribute(name, value));
        }


        private static final class PreLaunchFrame extends AbstractEngineJFrame {
            private PreLaunchFrame(String frameId, String title, EngineFrameOptions options) {
                super(frameId, title, true, options);
                enableRoundedWindowClip(18);
            }
        }

        private String readHtml() throws IOException {
            String resource = PRELAUNCH_RESOURCE_ROOT + "/prelaunch.html";
            try (var stream = HtmlPreLaunchSettingsDialog.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new IOException("Engine-owned prelaunch resource not found: " + resource);
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private record LaunchWindowOptions(boolean modal, EngineFrameOptions frameOptions, HtmlDomConfig htmlDomConfig) {
        private static LaunchWindowOptions fromSystemProperties() {
            String mode = System.getProperty(PRELAUNCH_MODALITY_PROPERTY, "").trim().toLowerCase();
            String explicitModal = System.getProperty(PRELAUNCH_MODAL_PROPERTY, "").trim();
            boolean devToolsInput = Boolean.parseBoolean(System.getProperty(PRELAUNCH_DEVTOOLS_INPUT_PROPERTY, "false"));
            boolean modal = resolveModal(mode, explicitModal, devToolsInput);
            return new LaunchWindowOptions(modal, resolveFrameOptions(), htmlDomConfig(devToolsInput));
        }

        private static boolean resolveModal(String mode, String explicitModal, boolean devToolsInput) {
            if (!mode.isBlank()) {
                return switch (mode) {
                    case "modal", "application-modal", "application_modal" -> true;
                    case "modeless", "nonmodal", "non-modal", "devtools", "dev-tools" -> false;
                    default -> !devToolsInput;
                };
            }
            if (!explicitModal.isBlank()) {
                return Boolean.parseBoolean(explicitModal);
            }
            return !devToolsInput;
        }

        private static EngineFrameOptions resolveFrameOptions() {
            return EngineFrameOptions.of(
                    booleanProperty(PRELAUNCH_SHOW_WINDOW_PROPERTY, true),
                    booleanProperty(PRELAUNCH_APPLY_SKIN_PROPERTY, true)
            );
        }

        private static boolean booleanProperty(String name, boolean fallback) {
            String raw = System.getProperty(name, "").trim();
            if (raw.isBlank()) {
                return fallback;
            }
            if ("true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase(raw) || "0".equals(raw) || "no".equalsIgnoreCase(raw) || "off".equalsIgnoreCase(raw)) {
                return false;
            }
            return fallback;
        }

        private static HtmlDomConfig htmlDomConfig(boolean devToolsInput) {
            return HtmlDomConfig.defaults()
                    .withAllowDevTools(enumProperty(
                            PRELAUNCH_DEVTOOLS_AVAILABILITY_PROPERTY,
                            devToolsInput ? HtmlDomConfig.DevToolsAvailability.ENABLED : HtmlDomConfig.DevToolsAvailability.ENABLED,
                            HtmlDomConfig.DevToolsAvailability.class
                    ))
                    .withDevToolsWindowType(enumProperty(
                            PRELAUNCH_DEVTOOLS_WINDOW_TYPE_PROPERTY,
                            HtmlDomConfig.DevToolsWindowType.STANDALONE_FRAME,
                            HtmlDomConfig.DevToolsWindowType.class
                    ))
                    .withDevToolsZOrder(enumProperty(
                            PRELAUNCH_DEVTOOLS_Z_ORDER_PROPERTY,
                            HtmlDomConfig.DevToolsZOrder.SAME_LEVEL,
                            HtmlDomConfig.DevToolsZOrder.class
                    ))
                    .withDevToolsClosePolicy(enumProperty(
                            PRELAUNCH_DEVTOOLS_CLOSE_POLICY_PROPERTY,
                            HtmlDomConfig.DevToolsClosePolicy.CLOSE_WITH_HOST,
                            HtmlDomConfig.DevToolsClosePolicy.class
                    ));
        }

        private static <E extends Enum<E>> E enumProperty(String name, E fallback, Class<E> type) {
            String raw = System.getProperty(name, "").trim();
            if (raw.isBlank()) {
                return fallback;
            }
            String normalized = raw.replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase();
            try {
                return Enum.valueOf(type, normalized);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private enum Preset {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        NATIVE_FULLSCREEN,
        CUSTOM
    }

    private record Resolution(int width, int height) {
        String shortText() {
            return width + " × " + height;
        }
    }

    private static int wrap(int value, int size) {
        if (size <= 0) {
            return 0;
        }
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private static int sampleIndex(int samples) {
        for (int i = 0; i < SAMPLE_VALUES.length; i++) {
            if (SAMPLE_VALUES[i] == samples) {
                return i;
            }
        }
        return 0;
    }

    private static Resolution currentResolution() {
        DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
        return new Resolution(mode.getWidth(), mode.getHeight());
    }

    private static List<Resolution> availableResolutions(Resolution nativeResolution) {
        TreeSet<Resolution> unique = new TreeSet<>(Comparator
                .comparingInt((Resolution resolution) -> resolution.width)
                .thenComparingInt(resolution -> resolution.height));
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        for (DisplayMode mode : device.getDisplayModes()) {
            int width = mode.getWidth();
            int height = mode.getHeight();
            if (width >= 1024 && height >= 720) {
                unique.add(new Resolution(width, height));
            }
        }
        unique.add(nativeResolution);
        return new ArrayList<>(unique);
    }

    private static void applyDisplayMode(AppSettings settings, Resolution resolution, boolean fullscreen) {
        settings.setResolution(resolution.width(), resolution.height());
        settings.setWindowSize(resolution.width(), resolution.height());
        if (!fullscreen) {
            return;
        }
        DisplayMode mode = bestDisplayMode(resolution);
        settings.setBitsPerPixel(mode.getBitDepth());
        settings.setFrequency(mode.getRefreshRate());
    }

    private static DisplayMode bestDisplayMode(Resolution resolution) {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode best = device.getDisplayMode();
        for (DisplayMode mode : device.getDisplayModes()) {
            if (mode.getWidth() != resolution.width() || mode.getHeight() != resolution.height()) {
                continue;
            }
            if (isBetterDisplayMode(mode, best)) {
                best = mode;
            }
        }
        return best;
    }

    private static boolean isBetterDisplayMode(DisplayMode candidate, DisplayMode current) {
        int candidateDepth = normalizeDisplayValue(candidate.getBitDepth());
        int currentDepth = normalizeDisplayValue(current.getBitDepth());
        if (candidateDepth != currentDepth) {
            return candidateDepth > currentDepth;
        }
        int candidateRefresh = normalizeDisplayValue(candidate.getRefreshRate());
        int currentRefresh = normalizeDisplayValue(current.getRefreshRate());
        return candidateRefresh > currentRefresh;
    }

    private static int normalizeDisplayValue(int value) {
        return value == DisplayMode.BIT_DEPTH_MULTI || value == DisplayMode.REFRESH_RATE_UNKNOWN ? 0 : value;
    }
}
