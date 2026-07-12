package org.takesome.frozenlands.engine.icons;

import org.takesome.frozenlands.engine.host.WindowIconDecoder;
import org.takesome.frozenlands.engine.host.WindowIconRequest;
import org.takesome.frozenlands.engine.icons.window.EngineWindowIconInstaller;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class IcoWindowIconDecoder implements WindowIconDecoder {
    private final EngineIconLoader loader = new EngineIconLoader();

    @Override
    public BufferedImage[] decode(Path iconPath) throws IOException {
        return loader.loadAppIcons(iconPath == null ? "" : iconPath.toString());
    }

    @Override
    public BufferedImage[] decode(WindowIconRequest request) throws IOException {
        return EngineWindowIconInstaller.loadIcons(request);
    }

    @Override
    public boolean install(Window window, WindowIconRequest request) throws IOException {
        return EngineWindowIconInstaller.install(window, request);
    }
}
