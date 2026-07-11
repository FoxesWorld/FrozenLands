package org.takesome.frozenlands.engine.ui.window;

public record EngineFrameOptions(boolean showWindow, boolean applySkin) {
    public static EngineFrameOptions defaults() {
        return new EngineFrameOptions(true, true);
    }

    public static EngineFrameOptions of(boolean showWindow, boolean applySkin) {
        return new EngineFrameOptions(showWindow, applySkin);
    }
}
