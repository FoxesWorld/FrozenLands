package org.takesome.frozenlands.engine.ui.window;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/**
 * Engine-owned top-level Swing frame.
 *
 * <p>This is the required host for engine UI skins that must behave like real
 * application windows on the OS: taskbar presence, normal focus behavior and
 * explicit lifecycle ownership. Skins are overrides; the frame always owns the
 * default FrozenLands engine style contract.</p>
 */
public abstract class AbstractEngineJFrame extends JFrame {
    private static final Color ENGINE_BACKGROUND = new Color(8, 12, 18);
    private static final Color ENGINE_PANEL = new Color(14, 22, 32);
    private static final Color ENGINE_BORDER = new Color(72, 198, 245, 190);
    private static final Color ENGINE_TEXT = new Color(232, 248, 255);
    private static final Color ENGINE_MUTED = new Color(145, 178, 196);

    private final String frameId;
    private final EngineFrameOptions options;
    private final JComponent standardSkin;
    private int roundedWindowCornerRadiusPx;
    private boolean roundedWindowListenerInstalled;

    protected AbstractEngineJFrame(String frameId, String title) {
        this(frameId, title, true, EngineFrameOptions.defaults());
    }

    protected AbstractEngineJFrame(String frameId, String title, boolean undecorated) {
        this(frameId, title, undecorated, EngineFrameOptions.defaults());
    }

    protected AbstractEngineJFrame(String frameId, String title, boolean undecorated, EngineFrameOptions options) {
        super(title == null || title.isBlank() ? "FrozenLands" : title);
        this.frameId = Objects.requireNonNull(frameId, "frameId");
        this.options = options == null ? EngineFrameOptions.defaults() : options;
        setName(frameId);
        setType(Window.Type.NORMAL);
        setUndecorated(undecorated);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setAutoRequestFocus(true);
        setFocusableWindowState(true);
        setBackground(ENGINE_BACKGROUND);
        getContentPane().setLayout(new BorderLayout());
        this.standardSkin = createStandardSkin(title);
        setContentPane(standardSkin);
    }

    public final String frameId() {
        return frameId;
    }

    public final EngineFrameOptions frameOptions() {
        return options;
    }

    public final boolean isShowWindowEnabled() {
        return options.showWindow();
    }

    public final boolean isApplySkinEnabled() {
        return options.applySkin();
    }

    /**
     * Enables native rounded clipping for undecorated Swing windows.
     *
     * <p>CSS {@code border-radius} paints the UI skin, but it cannot alter the
     * native rectangular top-level window by itself. This method installs an OS
     * window shape so the real frame corners are clipped as well.</p>
     *
     * @param cornerRadiusPx CSS-like corner radius in physical pixels.
     */
    public final void enableRoundedWindowClip(int cornerRadiusPx) {
        roundedWindowCornerRadiusPx = Math.max(0, cornerRadiusPx);
        if (!isUndecorated()) {
            return;
        }

        installTransparentWindowBackground();
        if (!roundedWindowListenerInstalled) {
            roundedWindowListenerInstalled = true;
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    updateRoundedWindowShape();
                }

                @Override
                public void componentShown(ComponentEvent event) {
                    updateRoundedWindowShape();
                }
            });
        }
        updateRoundedWindowShape();
    }

    public final void applySkin(JComponent skin, Dimension preferredSize, Dimension minimumSize) {
        Objects.requireNonNull(skin, "skin");
        if (!options.applySkin()) {
            applyStandardSkin(preferredSize, minimumSize);
            return;
        }
        applySizing(skin, preferredSize, minimumSize);
        if (roundedWindowCornerRadiusPx > 0) {
            skin.setOpaque(false);
        }
        setContentPane(skin);
        invalidate();
        updateRoundedWindowShape();
    }

    public final void applyStandardSkin(Dimension preferredSize, Dimension minimumSize) {
        applySizing(standardSkin, preferredSize, minimumSize);
        if (roundedWindowCornerRadiusPx > 0) {
            standardSkin.setOpaque(false);
        }
        setContentPane(standardSkin);
        invalidate();
        updateRoundedWindowShape();
    }

    public final void installCloseHandler(Runnable closeHandler) {
        Objects.requireNonNull(closeHandler, "closeHandler");
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeHandler.run();
            }
        });
    }

    public final void showEngineFrame() {
        if (!options.showWindow()) {
            return;
        }
        pack();
        updateRoundedWindowShape();
        setLocationRelativeTo(null);
        setVisible(true);
        updateRoundedWindowShape();
        toFront();
        requestFocus();
    }

    private JComponent createStandardSkin(String title) {
        JPanel root = new JPanel(new BorderLayout());
        root.setName(frameId + ".standardSkin");
        root.setOpaque(true);
        root.setBackground(ENGINE_BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ENGINE_BORDER, 1),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)
        ));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(true);
        content.setBackground(ENGINE_PANEL);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 226, 255, 80), 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 8, 0);

        JLabel label = new JLabel(title == null || title.isBlank() ? "FrozenLands Engine Window" : title);
        label.setForeground(ENGINE_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22.0f));
        content.add(label, gbc);

        gbc.gridy++;
        JLabel subtitle = new JLabel("FrozenLands Engine Frame · standard style · skin override disabled");
        subtitle.setForeground(ENGINE_MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13.0f));
        content.add(subtitle, gbc);

        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private void applySizing(JComponent component, Dimension preferredSize, Dimension minimumSize) {
        if (preferredSize != null) {
            component.setPreferredSize(preferredSize);
            component.setSize(preferredSize);
        }
        if (minimumSize != null) {
            component.setMinimumSize(minimumSize);
            setMinimumSize(minimumSize);
        }
    }

    private void installTransparentWindowBackground() {
        try {
            setBackground(new Color(0, 0, 0, 0));
            getRootPane().setOpaque(false);
            if (getContentPane() instanceof JComponent component) {
                component.setOpaque(false);
            }
        } catch (RuntimeException ignored) {
            // Some legacy desktops refuse per-pixel transparent windows. The
            // shaped region below is still the primary clipping mechanism.
        }
    }

    private void updateRoundedWindowShape() {
        if (!isUndecorated()) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            if (roundedWindowCornerRadiusPx <= 0) {
                setShape(null);
                return;
            }
            double arc = Math.min(Math.min(width, height), roundedWindowCornerRadiusPx * 2.0);
            setShape(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
        } catch (RuntimeException ignored) {
            // Window shaping is a platform capability. Failing closed here keeps
            // launcher startup safe on restricted desktops and remote sessions.
        }
    }
}
