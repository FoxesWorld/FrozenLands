package org.takesome.frozenlands;

import com.jme3.system.AppSettings;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;

final class PreLaunchSettingsDialog {
    private static final String PREFERENCES_KEY = "FrozenLands";

    private static final Color BACKGROUND = new Color(8, 12, 18);
    private static final Color CARD = new Color(17, 24, 34);
    private static final Color CARD_SOFT = new Color(22, 31, 44);
    private static final Color INPUT = new Color(27, 37, 52);
    private static final Color BORDER = new Color(54, 70, 92);
    private static final Color ACCENT = new Color(112, 196, 255);
    private static final Color ACCENT_DARK = new Color(34, 82, 120);
    private static final Color TEXT = new Color(232, 238, 247);
    private static final Color MUTED = new Color(151, 164, 184);
    private static final Color DISABLED = new Color(98, 108, 123);

    private static final MsaaOption[] SAMPLE_OPTIONS = {
            new MsaaOption(0, "Off"),
            new MsaaOption(2, "2x MSAA"),
            new MsaaOption(4, "4x MSAA"),
            new MsaaOption(8, "8x MSAA"),
            new MsaaOption(16, "16x MSAA")
    };

    private PreLaunchSettingsDialog() {
    }

    static AppSettings request(String title) {
        AppSettings settings = defaultSettings(title);
        loadSavedSettings(settings);
        if (GraphicsEnvironment.isHeadless()) {
            return settings;
        }

        AtomicReference<AppSettings> selected = new AtomicReference<>();
        Runnable showDialog = () -> {
            try {
                selected.set(HtmlPreLaunchSettingsDialog.request(title, settings));
            } catch (RuntimeException exception) {
                selected.set(showDialog(settings));
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(showDialog);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException("Pre-launch settings dialog failed", exception.getCause());
            }
        }
        return selected.get();
    }

    private static AppSettings defaultSettings(String title) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(title);
        Resolution nativeResolution = currentResolution();
        settings.setResolution(nativeResolution.width, nativeResolution.height);
        settings.setWindowSize(nativeResolution.width, nativeResolution.height);
        settings.setFullscreen(false);
        settings.setCenterWindow(true);
        settings.setSamples(0);
        settings.setVSync(false);
        return settings;
    }

    private static void loadSavedSettings(AppSettings settings) {
        try {
            settings.load(PREFERENCES_KEY);
        } catch (BackingStoreException ignored) {
        }
    }

    private static AppSettings showDialog(AppSettings initialSettings) {
        SettingsForm form = new SettingsForm(initialSettings);
        JDialog dialog = new JDialog((java.awt.Frame) null, "FrozenLands Launcher", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.add(form.panel(), BorderLayout.CENTER);

        ButtonBar buttonBar = buttonPanel(dialog, form);
        root.add(buttonBar.panel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        installKeyboardShortcuts(dialog, form);
        dialog.getRootPane().setDefaultButton(buttonBar.primaryButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(680, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return form.result();
    }

    private static void installKeyboardShortcuts(JDialog dialog, SettingsForm form) {
        dialog.getRootPane().registerKeyboardAction(event -> {
            form.cancel();
            dialog.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static ButtonBar buttonPanel(JDialog dialog, SettingsForm form) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 24, 24, 24));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        JButton reset = createButton("Reset defaults", false);
        reset.addActionListener(event -> form.resetToDefaults());
        left.add(reset);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JButton cancel = createButton("Cancel", false);
        cancel.addActionListener(event -> {
            form.cancel();
            dialog.dispose();
        });

        JButton start = createButton("Launch FrozenLands", true);
        start.addActionListener(event -> {
            form.confirm();
            dialog.dispose();
        });

        right.add(cancel);
        right.add(start);
        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return new ButtonBar(panel, start);
    }

    private static JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        styleButton(button, primary);
        return button;
    }

    private static void styleButton(AbstractButton button, boolean primary) {
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? ACCENT : BORDER),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)
        ));
        if (primary) {
            button.setBackground(ACCENT_DARK);
            button.setForeground(TEXT);
        } else {
            button.setBackground(CARD_SOFT);
            button.setForeground(MUTED);
        }
    }

    private static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBackground(INPUT);
        comboBox.setForeground(TEXT);
        comboBox.setMaximumRowCount(10);
        comboBox.setFocusable(false);
        comboBox.setPreferredSize(new Dimension(300, 34));
        comboBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
    }

    private static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setForeground(TEXT);
        checkBox.setBackground(CARD);
        checkBox.setOpaque(false);
        checkBox.setFocusPainted(false);
        checkBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    private static JLabel createLabel(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        return label;
    }

    private static final class SettingsForm {
        private final JPanel panel = new JPanel(new BorderLayout(0, 18));
        private final JPanel card = new JPanel(new GridBagLayout());
        private final JLabel summary = createLabel("", 12, Font.BOLD, TEXT);
        private final JComboBox<LaunchPreset> preset;
        private final JComboBox<Resolution> resolution;
        private final JComboBox<MsaaOption> samples;
        private final JCheckBox fullscreen;
        private final JCheckBox centerWindow;
        private final JCheckBox vsync;
        private final AppSettings initialSettings;
        private final Resolution nativeResolution;
        private AppSettings result;
        private boolean adjusting;

        private SettingsForm(AppSettings initialSettings) {
            this.initialSettings = initialSettings;
            this.nativeResolution = currentResolution();
            this.preset = new JComboBox<>(LaunchPreset.values());
            this.resolution = new JComboBox<>(availableResolutions().toArray(new Resolution[0]));
            this.samples = new JComboBox<>(SAMPLE_OPTIONS);
            this.fullscreen = new JCheckBox("Use exclusive fullscreen");
            this.centerWindow = new JCheckBox("Center game window");
            this.vsync = new JCheckBox("Synchronize frame output with display refresh");

            configureControls();
            applySettings(initialSettings);
            buildPanel();
            installInteractions();
            updateModeControls();
            updateSummary();
        }

        private JPanel panel() {
            return panel;
        }

        private AppSettings result() {
            return result;
        }

        private void confirm() {
            AppSettings settings = new AppSettings(true);
            settings.copyFrom(initialSettings);
            Resolution selectedResolution = (Resolution) resolution.getSelectedItem();
            boolean fullscreenSelected = fullscreen.isSelected();
            if (selectedResolution != null) {
                applyDisplayMode(settings, selectedResolution, fullscreenSelected);
            }
            MsaaOption selectedSamples = (MsaaOption) Objects.requireNonNull(samples.getSelectedItem());
            settings.setFullscreen(fullscreenSelected);
            settings.setCenterWindow(!fullscreenSelected && centerWindow.isSelected());
            settings.setSamples(fullscreenSelected ? 0 : selectedSamples.value);
            settings.setVSync(vsync.isSelected());
            settings.setTitle(initialSettings.getTitle());
            try {
                settings.save(PREFERENCES_KEY);
            } catch (BackingStoreException ignored) {
            }
            result = settings;
        }

        private void cancel() {
            result = null;
        }

        private void resetToDefaults() {
            applySettings(defaultSettings(initialSettings.getTitle()));
        }

        private void configureControls() {
            styleComboBox(preset);
            styleComboBox(resolution);
            styleComboBox(samples);
            styleCheckBox(fullscreen);
            styleCheckBox(centerWindow);
            styleCheckBox(vsync);

            preset.setToolTipText("Apply a fast display profile, then adjust individual options if needed.");
            resolution.setToolTipText("Choose the render/window resolution. Native monitor resolution is marked.");
            samples.setToolTipText("Multisample anti-aliasing for windowed mode.");
            fullscreen.setToolTipText("Switch to exclusive fullscreen using the selected display mode.");
            centerWindow.setToolTipText("Only applies to windowed mode.");
            vsync.setToolTipText("Helps reduce tearing, but can add input latency.");

            resolution.setRenderer(new ResolutionRenderer(nativeResolution));
        }

        private void applySettings(AppSettings settings) {
            adjusting = true;
            selectResolution(new Resolution(settings.getWidth(), settings.getHeight()));
            samples.setSelectedItem(normalizeSamples(settings.getSamples()));
            fullscreen.setSelected(settings.isFullscreen());
            centerWindow.setSelected(settings.getCenterWindow());
            vsync.setSelected(settings.isVSync());
            preset.setSelectedItem(LaunchPreset.CUSTOM);
            adjusting = false;
            updateModeControls();
            updateSummary();
        }

        private void buildPanel() {
            panel.setBackground(BACKGROUND);
            panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 0, 24));
            panel.add(headerPanel(), BorderLayout.NORTH);

            JPanel content = new JPanel(new BorderLayout(0, 12));
            content.setOpaque(false);

            card.setBackground(CARD);
            card.setOpaque(true);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(18, 18, 18, 18)
            ));

            addRow(0, "Launch profile", "Fast profile selector for common display setups.", preset);
            addRow(1, "Resolution", "Native monitor resolution is marked as recommended.", resolution);
            addRow(2, "Display mode", "Exclusive fullscreen uses the best refresh/depth match.", fullscreen);
            addRow(3, "Anti-aliasing", "Windowed MSAA level. Fullscreen keeps this disabled for safety.", samples);
            addRow(4, "Window placement", "Keeps the window centered when not fullscreen.", centerWindow);
            addRow(5, "Vertical sync", "Reduces tearing; disable for lowest input latency.", vsync);

            content.add(card, BorderLayout.CENTER);
            content.add(summaryPanel(), BorderLayout.SOUTH);
            panel.add(content, BorderLayout.CENTER);
        }

        private JPanel headerPanel() {
            JPanel header = new JPanel(new BorderLayout(18, 0));
            header.setOpaque(false);

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

            JLabel overline = createLabel("TAKE SOME() RUNTIME", 11, Font.BOLD, ACCENT);
            JLabel title = createLabel("FrozenLands Launch Control", 26, Font.BOLD, TEXT);
            JLabel subtitle = createLabel("Configure the display before the cold world boots.", 13, Font.PLAIN, MUTED);

            copy.add(overline);
            copy.add(Box.createVerticalStrut(5));
            copy.add(title);
            copy.add(Box.createVerticalStrut(6));
            copy.add(subtitle);

            JPanel badge = new JPanel(new BorderLayout());
            badge.setOpaque(true);
            badge.setBackground(CARD_SOFT);
            badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
            JLabel badgeText = createLabel("JME / MINIE", 11, Font.BOLD, MUTED);
            badgeText.setHorizontalAlignment(SwingConstants.CENTER);
            badge.add(badgeText, BorderLayout.CENTER);

            header.add(copy, BorderLayout.CENTER);
            header.add(badge, BorderLayout.EAST);
            return header;
        }

        private JPanel summaryPanel() {
            JPanel panel = new JPanel(new BorderLayout(12, 0));
            panel.setBackground(CARD_SOFT);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
            JLabel label = createLabel("Current plan", 11, Font.BOLD, ACCENT);
            panel.add(label, BorderLayout.WEST);
            panel.add(summary, BorderLayout.CENTER);
            return panel;
        }

        private void addRow(int row, String label, String hint, Component component) {
            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            JLabel title = createLabel(label, 13, Font.BOLD, TEXT);
            JLabel description = createLabel(hint, 11, Font.PLAIN, MUTED);
            labels.add(title);
            labels.add(Box.createVerticalStrut(3));
            labels.add(description);

            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.weightx = 0.0;
            labelConstraints.fill = GridBagConstraints.HORIZONTAL;
            labelConstraints.anchor = GridBagConstraints.WEST;
            labelConstraints.insets = new Insets(row == 0 ? 0 : 10, 0, 10, 20);
            card.add(labels, labelConstraints);

            GridBagConstraints valueConstraints = new GridBagConstraints();
            valueConstraints.gridx = 1;
            valueConstraints.gridy = row;
            valueConstraints.weightx = 1.0;
            valueConstraints.fill = GridBagConstraints.HORIZONTAL;
            valueConstraints.anchor = GridBagConstraints.WEST;
            valueConstraints.insets = new Insets(row == 0 ? 0 : 10, 0, 10, 0);
            card.add(component, valueConstraints);
        }

        private void installInteractions() {
            preset.addActionListener(event -> applySelectedPreset());
            resolution.addActionListener(event -> markCustomAndRefresh());
            samples.addActionListener(event -> markCustomAndRefresh());
            fullscreen.addActionListener(event -> markCustomAndRefresh());
            centerWindow.addActionListener(event -> markCustomAndRefresh());
            vsync.addActionListener(event -> markCustomAndRefresh());
        }

        private void applySelectedPreset() {
            if (adjusting) {
                return;
            }
            LaunchPreset selected = (LaunchPreset) preset.getSelectedItem();
            if (selected == null || selected == LaunchPreset.CUSTOM) {
                updateModeControls();
                updateSummary();
                return;
            }
            applyPreset(selected);
        }

        private void applyPreset(LaunchPreset selected) {
            adjusting = true;
            selectResolution(nativeResolution);
            switch (selected) {
                case PERFORMANCE:
                    fullscreen.setSelected(false);
                    samples.setSelectedItem(SAMPLE_OPTIONS[0]);
                    centerWindow.setSelected(true);
                    vsync.setSelected(false);
                    break;
                case BALANCED:
                    fullscreen.setSelected(false);
                    samples.setSelectedItem(normalizeSamples(4));
                    centerWindow.setSelected(true);
                    vsync.setSelected(true);
                    break;
                case QUALITY:
                    fullscreen.setSelected(false);
                    samples.setSelectedItem(normalizeSamples(8));
                    centerWindow.setSelected(true);
                    vsync.setSelected(true);
                    break;
                case NATIVE_FULLSCREEN:
                    fullscreen.setSelected(true);
                    samples.setSelectedItem(SAMPLE_OPTIONS[0]);
                    centerWindow.setSelected(false);
                    vsync.setSelected(true);
                    break;
                case CUSTOM:
                default:
                    break;
            }
            adjusting = false;
            updateModeControls();
            updateSummary();
        }

        private void markCustomAndRefresh() {
            if (adjusting) {
                return;
            }
            adjusting = true;
            preset.setSelectedItem(LaunchPreset.CUSTOM);
            adjusting = false;
            updateModeControls();
            updateSummary();
        }

        private void updateModeControls() {
            boolean windowed = !fullscreen.isSelected();
            centerWindow.setEnabled(windowed);
            samples.setEnabled(windowed);
            centerWindow.setForeground(windowed ? TEXT : DISABLED);
            samples.setForeground(windowed ? TEXT : DISABLED);
        }

        private void updateSummary() {
            Resolution selectedResolution = (Resolution) resolution.getSelectedItem();
            MsaaOption selectedSamples = (MsaaOption) samples.getSelectedItem();
            String mode = fullscreen.isSelected() ? "Fullscreen" : "Windowed";
            String sync = vsync.isSelected() ? "VSync on" : "VSync off";
            String aa = fullscreen.isSelected()
                    ? "MSAA off in fullscreen"
                    : selectedSamples == null ? "MSAA off" : selectedSamples.toString();
            String display = selectedResolution == null ? "unknown resolution" : selectedResolution.shortText();
            summary.setText(mode + "  |  " + display + "  |  " + aa + "  |  " + sync);
        }

        private void selectResolution(Resolution target) {
            for (int i = 0; i < resolution.getItemCount(); i++) {
                Resolution option = resolution.getItemAt(i);
                if (option.equals(target)) {
                    resolution.setSelectedIndex(i);
                    return;
                }
            }
            resolution.addItem(target);
            resolution.setSelectedItem(target);
        }
    }

    private static MsaaOption normalizeSamples(int samples) {
        for (MsaaOption option : SAMPLE_OPTIONS) {
            if (option.value == samples) {
                return option;
            }
        }
        return SAMPLE_OPTIONS[0];
    }

    private static void applyDisplayMode(AppSettings settings, Resolution resolution, boolean fullscreen) {
        settings.setResolution(resolution.width, resolution.height);
        settings.setWindowSize(resolution.width, resolution.height);
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
            if (mode.getWidth() != resolution.width || mode.getHeight() != resolution.height) {
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

    private static List<Resolution> availableResolutions() {
        TreeSet<Resolution> resolutions = new TreeSet<>((left, right) -> {
            int byWidth = Integer.compare(right.width, left.width);
            if (byWidth != 0) {
                return byWidth;
            }
            return Integer.compare(right.height, left.height);
        });
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        for (DisplayMode mode : device.getDisplayModes()) {
            if (mode.getWidth() >= 800 && mode.getHeight() >= 600) {
                resolutions.add(new Resolution(mode.getWidth(), mode.getHeight()));
            }
        }
        resolutions.add(currentResolution());
        return new ArrayList<>(resolutions);
    }

    private static Resolution currentResolution() {
        DisplayMode currentMode = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDisplayMode();
        return new Resolution(currentMode.getWidth(), currentMode.getHeight());
    }

    private enum LaunchPreset {
        CUSTOM("Custom"),
        PERFORMANCE("Performance"),
        BALANCED("Balanced"),
        QUALITY("Quality"),
        NATIVE_FULLSCREEN("Native fullscreen");

        private final String label;

        LaunchPreset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class MsaaOption {
        private final int value;
        private final String label;

        private MsaaOption(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class Resolution {
        private final int width;
        private final int height;

        private Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private String shortText() {
            return width + " x " + height;
        }

        private String displayText(boolean nativeResolution) {
            String text = shortText() + "  |  " + aspectRatio();
            return nativeResolution ? text + "  |  native" : text;
        }

        private String aspectRatio() {
            int divisor = greatestCommonDivisor(width, height);
            return width / divisor + ":" + height / divisor;
        }

        private static int greatestCommonDivisor(int left, int right) {
            int a = Math.abs(left);
            int b = Math.abs(right);
            while (b != 0) {
                int next = a % b;
                a = b;
                b = next;
            }
            return a == 0 ? 1 : a;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Resolution resolution)) {
                return false;
            }
            return width == resolution.width && height == resolution.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }

        @Override
        public String toString() {
            return shortText();
        }
    }

    private static final class ResolutionRenderer extends DefaultListCellRenderer {
        private final Resolution nativeResolution;

        private ResolutionRenderer(Resolution nativeResolution) {
            this.nativeResolution = nativeResolution;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (component instanceof JLabel label && value instanceof Resolution resolution) {
                label.setText(resolution.displayText(resolution.equals(nativeResolution)));
                label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                label.setOpaque(true);
                label.setForeground(isSelected ? TEXT : TEXT);
                label.setBackground(isSelected ? ACCENT_DARK : INPUT);
            }
            return component;
        }
    }

    private static final class ButtonBar {
        private final JPanel panel;
        private final JButton primaryButton;

        private ButtonBar(JPanel panel, JButton primaryButton) {
            this.panel = panel;
            this.primaryButton = primaryButton;
        }
    }
}
