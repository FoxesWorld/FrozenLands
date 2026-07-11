package org.takesome.frozenlands.engine.quality;

import java.util.Map;

@FunctionalInterface
interface QualityEventPublisher {
    void publish(String topic, Map<String, Object> payload);
}
