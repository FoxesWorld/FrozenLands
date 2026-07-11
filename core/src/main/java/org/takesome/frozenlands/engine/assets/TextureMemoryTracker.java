package org.takesome.frozenlands.engine.assets;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Tracks unique texture images and estimates their native memory footprint. */
final class TextureMemoryTracker {
    private final ConcurrentMap<String, Set<Long>> imageIdsBySource =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<String>> sourcesByImage =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> bytesByImage =
            new ConcurrentHashMap<>();

    void track(String source, Texture texture) {
        Image image = texture == null ? null : texture.getImage();
        if (image == null) {
            return;
        }

        long imageId = image.getUniqueId();
        bytesByImage.putIfAbsent(imageId, estimateBytes(image));
        imageIdsBySource
                .computeIfAbsent(source, ignored -> ConcurrentHashMap.newKeySet())
                .add(imageId);
        sourcesByImage
                .computeIfAbsent(imageId, ignored -> ConcurrentHashMap.newKeySet())
                .add(source);
    }

    void release(String source) {
        Set<Long> imageIds = imageIdsBySource.remove(source);
        if (imageIds == null) {
            return;
        }
        for (Long imageId : imageIds) {
            Set<String> sources = sourcesByImage.get(imageId);
            if (sources == null) {
                continue;
            }
            sources.remove(source);
            if (sources.isEmpty()) {
                sourcesByImage.remove(imageId);
                bytesByImage.remove(imageId);
            }
        }
    }

    void clear() {
        imageIdsBySource.clear();
        sourcesByImage.clear();
        bytesByImage.clear();
    }

    int imageCount() {
        return bytesByImage.size();
    }

    long estimatedBytes() {
        return bytesByImage.values().stream().mapToLong(Long::longValue).sum();
    }

    private long estimateBytes(Image image) {
        long bufferedBytes = bufferedBytes(image);
        if (bufferedBytes > 0L) {
            return bufferedBytes;
        }

        long depth = Math.max(1, image.getDepth());
        long bitsPerPixel = Math.max(1, image.getFormat().getBitsPerPixel());
        long baseBytes = (long) image.getWidth()
                * image.getHeight()
                * depth
                * bitsPerPixel
                / 8L;
        return image.hasMipmaps() || image.isMipmapsGenerated()
                ? Math.round(baseBytes * 4.0 / 3.0)
                : baseBytes;
    }

    private long bufferedBytes(Image image) {
        if (image.getData() == null || image.getData().isEmpty()) {
            return 0L;
        }
        long bytes = 0L;
        for (ByteBuffer buffer : image.getData()) {
            if (buffer != null) {
                bytes += buffer.capacity();
            }
        }
        return bytes;
    }
}
