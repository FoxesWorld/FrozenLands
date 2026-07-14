package org.takesome.frozenlands.engine.ui.html;

import dev.takesome.htmldom.desktop.HtmlDomHitTestEngine;
import dev.takesome.htmldom.desktop.HtmlDomSwingPanel;
import dev.takesome.htmldom.dom.UiDomElement;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JmeHtmlDomInputBridgeTest {
    @Test
    void mouseMotionBecomesDragWhileAnyButtonIsHeld() {
        assertEquals(
                MouseEvent.MOUSE_MOVED,
                JmeHtmlDomInputBridge.awtMotionEventId(0)
        );
        assertEquals(
                MouseEvent.MOUSE_DRAGGED,
                JmeHtmlDomInputBridge.awtMotionEventId(
                        MouseEvent.BUTTON1_DOWN_MASK
                )
        );
        assertEquals(
                MouseEvent.MOUSE_DRAGGED,
                JmeHtmlDomInputBridge.awtMotionEventId(
                        MouseEvent.BUTTON2_DOWN_MASK
                )
        );
        assertEquals(
                MouseEvent.MOUSE_DRAGGED,
                JmeHtmlDomInputBridge.awtMotionEventId(
                        MouseEvent.BUTTON3_DOWN_MASK
                )
        );
    }

    @Test
    void offscreenDispatchReachesTransformedRangeAndKeepsCapture() {
        String markup = """
                <html>
                  <body>
                    <div id="holder">
                      <input id="volume" type="range"
                             min="0" max="1" step="0.05" value="0.5">
                    </div>
                  </body>
                </html>
                """;
        String css = """
                html, body {
                  width: 500px;
                  height: 140px;
                  margin: 0;
                  padding: 0;
                }
                #holder {
                  display: block;
                  position: absolute;
                  left: 140px;
                  top: 30px;
                  width: 320px;
                  height: 42px;
                  transform: translate(-80px, 0px) scale(1) rotate(0deg);
                }
                #volume {
                  display: block;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width: 320px;
                  height: 42px;
                  background-color: transparent;
                  accent-color: #69d8ff;
                  color: #ffffff;
                  border-color: #a8ecff;
                }
                """;
        HtmlDomSwingPanel panel = new HtmlDomSwingPanel(markup, css);
        panel.setSize(500, 140);
        panel.doLayout();
        paint(panel, 500, 140);

        UiDomElement range = panel.document()
                .getElementById("volume")
                .orElseThrow();
        Rectangle bounds = rangeHitBounds(panel, range);
        int thumbDiameter = Math.max(20, Math.min(28, bounds.height - 4));
        int thumbRadius = thumbDiameter / 2;
        int trackLeft = bounds.x + thumbRadius + 2;
        int trackRight = bounds.x + bounds.width - thumbRadius - 2;
        int thumbCenter = trackLeft + (trackRight - trackLeft) / 2;
        int y = bounds.y + bounds.height / 2;
        int pressX = thumbCenter + 9;
        int dragX = Math.max(0, bounds.x - 40);

        JmeHtmlDomInputBridge.dispatchMouseEvent(
                panel,
                mouse(
                        panel,
                        MouseEvent.MOUSE_PRESSED,
                        pressX,
                        y,
                        MouseEvent.BUTTON1_DOWN_MASK
                )
        );
        assertTrue(
                range.hasPseudoClass("active"),
                "offscreen press should activate the range"
        );
        assertEquals(
                0.5f,
                Float.parseFloat(range.attribute("value", "-1")),
                0.0001f,
                "grabbing the thumb must not jump its value"
        );

        JmeHtmlDomInputBridge.dispatchMouseEvent(
                panel,
                mouse(
                        panel,
                        MouseEvent.MOUSE_DRAGGED,
                        dragX,
                        y,
                        MouseEvent.BUTTON1_DOWN_MASK
                )
        );
        JmeHtmlDomInputBridge.dispatchMouseEvent(
                panel,
                mouse(
                        panel,
                        MouseEvent.MOUSE_RELEASED,
                        dragX,
                        y,
                        0
                )
        );

        assertEquals(
                0f,
                Float.parseFloat(range.attribute("value", "-1")),
                0.0001f,
                "captured range drag should continue outside the track"
        );
    }

    private Rectangle rangeHitBounds(
            HtmlDomSwingPanel panel,
            UiDomElement range
    ) {
        Rectangle bounds = null;
        for (HtmlDomHitTestEngine.Hit hit : panel.hitTest().hits()) {
            if (hit.element() == range) {
                bounds = hit.bounds();
            }
        }
        if (bounds == null) {
            throw new IllegalStateException("range hit target missing");
        }
        return bounds;
    }

    private BufferedImage paint(
            HtmlDomSwingPanel panel,
            int width,
            int height
    ) {
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private MouseEvent mouse(
            HtmlDomSwingPanel panel,
            int id,
            int x,
            int y,
            int modifiers
    ) {
        return new MouseEvent(
                panel,
                id,
                System.currentTimeMillis(),
                modifiers,
                x,
                y,
                1,
                false,
                MouseEvent.BUTTON1
        );
    }
}
