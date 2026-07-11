package org.takesome.frozenlands.engine.assets;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureMemoryTrackerTest {
    @Test
    void countsSharedNativeImageOnceUntilLastSourceIsReleased() {
        ByteBuffer data = ByteBuffer.allocateDirect(16);
        Image image = new Image();
        image.setFormat(Image.Format.RGBA8);
        image.setWidth(2);
        image.setHeight(2);
        image.setData(data);
        Texture2D first = new Texture2D();
        first.setImage(image);
        Texture2D second = new Texture2D();
        second.setImage(image);
        TextureMemoryTracker tracker = new TextureMemoryTracker();

        tracker.track("textures/shared-a.png", first);
        tracker.track("textures/shared-b.png", second);

        assertEquals(1, tracker.imageCount());
        assertEquals(16L, tracker.estimatedBytes());

        tracker.release("textures/shared-a.png");
        assertEquals(1, tracker.imageCount());
        assertEquals(16L, tracker.estimatedBytes());

        tracker.release("textures/shared-b.png");
        assertEquals(0, tracker.imageCount());
        assertEquals(0L, tracker.estimatedBytes());
    }
}
