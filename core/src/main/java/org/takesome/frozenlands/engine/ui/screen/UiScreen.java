package org.takesome.frozenlands.engine.ui.screen;

public interface UiScreen {
    String id();

    void show(UiScreenModel model, int width, int height);

    void update(UiScreenModel model, int width, int height);

    void renderFrame(int width, int height);

    void hide();

    default boolean matches(String screenId) {
        return screenId != null && id().equals(screenId);
    }
}
