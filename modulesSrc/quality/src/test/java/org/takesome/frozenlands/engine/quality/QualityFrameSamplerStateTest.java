package org.takesome.frozenlands.engine.quality;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityFrameSamplerStateTest {
    @Test
    void samplesFrameAndRenderDurationsWithoutApplicationInitialization()
            throws InterruptedException {
        QualityFrameSamplerState sampler = new QualityFrameSamplerState(null);

        sampler.update(0.016f);
        sampler.update(0.018f);
        sampler.render(null);
        Thread.sleep(2L);
        sampler.postRender();

        Map<String, Object> frame = sampler.frameStatus();
        Map<String, Object> render = sampler.renderStatus();
        Map<String, Object> physics = sampler.physicsStatus();

        assertTrue(((Number) frame.get("fps")).doubleValue() > 0.0);
        assertTrue(((Number) frame.get("milliseconds")).doubleValue() > 0.0);
        assertEquals(2L, frame.get("sampledFrames"));
        assertTrue(((Number) render.get("milliseconds")).doubleValue() > 0.0);
        assertFalse((Boolean) physics.get("available"));
    }
}
