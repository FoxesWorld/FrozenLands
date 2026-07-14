package org.takesome.frozenlands.engine.host;

import com.jme3.system.AppSettings;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ServiceLoader;

/** Engine-owned canonical entry point for application/window icon handling. */
public final class EngineWindowIcons {
    private EngineWindowIcons() {
    }

    public static boolean applyToSettings(AppSettings settings, WindowIconRequest request) {
        if (settings == null) {
            return false;
        }
        for (WindowIconDecoder decoder : ServiceLoader.load(WindowIconDecoder.class)) {
            try {
                BufferedImage[] icons = decoder.decode(request);
                if (icons != null && icons.length > 0) {
                    settings.setIcons(icons);
                    return true;
                }
            } catch (RuntimeException | java.io.IOException ignored) {
            }
        }
        return false;
    }

    public static boolean install(Window window, WindowIconRequest request) {
        if (window == null) {
            return false;
        }
        for (WindowIconDecoder decoder : ServiceLoader.load(WindowIconDecoder.class)) {
            try {
                if (decoder.install(window, request)) {
                    return true;
                }
            } catch (RuntimeException | java.io.IOException ignored) {
            }
        }
        return false;
    }
}
