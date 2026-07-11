package org.takesome.frozenlands.engine.quality;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsQualityServiceTest {
    @Test
    void appliesPresetToEveryTargetAndPublishesRevision() {
        GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();
        List<String> topics = new ArrayList<>();
        GraphicsQualityService service = new GraphicsQualityService(
                catalog,
                (topic, payload) -> topics.add(topic)
        );
        RecordingTarget assets = new RecordingTarget("assets");
        RecordingTarget shaders = new RecordingTarget("shaders");
        service.registerTarget(assets);
        service.registerTarget(shaders);

        GraphicsQualityProfile profile = service.apply(GraphicsPreset.MEDIUM);

        assertEquals(GraphicsPreset.MEDIUM, profile.preset());
        assertEquals(GraphicsPreset.MEDIUM, assets.lastPreset);
        assertEquals(GraphicsPreset.MEDIUM, shaders.lastPreset);
        assertEquals(1L, service.status().get("revision"));
        assertTrue(topics.contains(QualityEvents.PROFILE_CHANGED));
    }

    @Test
    void rejectsProfileWhenAnyTargetFails() {
        GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();
        List<String> topics = new ArrayList<>();
        GraphicsQualityService service = new GraphicsQualityService(
                catalog,
                (topic, payload) -> topics.add(topic)
        );
        service.registerTarget(new RecordingTarget("assets"));
        service.registerTarget(new FailingTarget());

        assertThrows(
                IllegalStateException.class,
                () -> service.apply(GraphicsPreset.ULTRA)
        );
        assertEquals(GraphicsPreset.HIGH, service.currentProfile().preset());
        assertTrue(topics.contains(QualityEvents.PROFILE_FAILED));
    }

    private static final class RecordingTarget implements GraphicsQualityTarget {
        private final String id;
        private GraphicsPreset lastPreset;

        private RecordingTarget(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Map<String, Object> apply(GraphicsQualityProfile profile) {
            lastPreset = profile.preset();
            return Map.of("preset", profile.preset().name());
        }
    }

    private static final class FailingTarget implements GraphicsQualityTarget {
        @Override
        public String id() {
            return "failing";
        }

        @Override
        public Map<String, Object> apply(GraphicsQualityProfile profile) {
            throw new IllegalStateException("synthetic failure");
        }
    }
}
