package org.takesome.frozenlands.engine.core.console;

import org.takesome.frozenlands.engine.events.EngineEventTopics;

public final class CoreConsoleEvents {
    public static final String VISIBILITY_CHANGED = EngineEventTopics.CONSOLE_VISIBILITY_CHANGED;
    public static final String OPENED = EngineEventTopics.CONSOLE_OPENED;
    public static final String CLOSED = EngineEventTopics.CONSOLE_CLOSED;
    public static final String INPUT_CAPTURE_CHANGED = EngineEventTopics.CONSOLE_INPUT_CAPTURE_CHANGED;

    private CoreConsoleEvents() {
    }
}
