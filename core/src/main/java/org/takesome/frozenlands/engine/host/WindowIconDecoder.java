package org.takesome.frozenlands.engine.host;

import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Decodes and installs launcher/window icons from an external runtime module jar. */
public interface WindowIconDecoder {
    BufferedImage[] decode(Path iconPath) throws IOException;

    default BufferedImage[] decode(WindowIconRequest request) throws IOException {
        if (request == null || request.reference().isBlank()) {
            return new BufferedImage[0];
        }
        return decode(Path.of(request.reference()));
    }

    default boolean install(Window window, WindowIconRequest request) throws IOException {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        BufferedImage[] icons = decode(request);
        List<Image> images = imageList(icons);
        if (images.isEmpty()) {
            return false;
        }
        window.setIconImages(images);
        return true;
    }

    private static List<Image> imageList(BufferedImage[] icons) {
        List<Image> images = new ArrayList<>();
        if (icons != null) {
            for (BufferedImage icon : icons) {
                if (icon != null) {
                    images.add(icon);
                }
            }
        }
        return images;
    }
}
