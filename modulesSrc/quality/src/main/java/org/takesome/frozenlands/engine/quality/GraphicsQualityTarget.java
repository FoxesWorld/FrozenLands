package org.takesome.frozenlands.engine.quality;

import java.util.Map;

/** One independently testable consumer of the active graphics profile. */
public interface GraphicsQualityTarget {
    String id();

    Map<String, Object> apply(GraphicsQualityProfile profile);

    default Map<String, Object> status() {
        return Map.of();
    }
}
