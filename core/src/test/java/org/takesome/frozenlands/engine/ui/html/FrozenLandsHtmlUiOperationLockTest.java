package org.takesome.frozenlands.engine.ui.html;

import dev.takesome.htmldom.desktop.HtmlDomSwingPanel;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.scripting.lua.HtmlDomLuaRuntime;
import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.operations.EngineOperationCoordinator;
import org.takesome.frozenlands.engine.testing.TestEngineContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrozenLandsHtmlUiOperationLockTest {
    private static final String START_OPERATION = "runtime.game.start";
    private static final String MARKUP = """
            <!doctype html>
            <html id="fl-root" data-screen="main-menu">
            <body>
              <button id="btn-new-game"
                      class="menu-button"
                      data-event="ui.menu.newGame"
                      data-action="game.new"
                      data-operation="runtime.game.start">New game</button>
              <button id="btn-continue"
                      class="menu-button"
                      data-event="ui.menu.continue"
                      data-action="game.continue"
                      data-operation="runtime.game.start">Continue</button>
            </body>
            </html>
            """;
    private static final String CSS = """
            html, body { display: block; width: 640px; height: 360px; }
            button { display: block; width: 240px; height: 48px; }
            [data-operation].operation-busy, [data-operation][disabled] { opacity: 0.58; }
            """;

    @Test
    void hydratesFromActiveOperationRejectsClickAndUnlocksOnCompletion() throws Exception {
        TestEngineContext context = new TestEngineContext();
        EngineOperationCoordinator coordinator = new EngineOperationCoordinator(context);
        context.registerService("core.operations", EngineOperationCoordinator.class, coordinator);

        EngineOperationCoordinator.BeginResult begin = coordinator.begin(
                START_OPERATION,
                Map.of("operation", "newGame"),
                "test"
        );
        assertTrue(begin.accepted());

        HtmlDomSwingPanel panel = new HtmlDomSwingPanel(MARKUP, CSS);
        panel.setSize(640, 360);
        panel.ensureLayout();
        FrozenLandsHtmlUiRuntime runtime = createRuntime(context, panel);
        installOperationBridge(runtime);

        UiDomElement newGame = panel.document().getElementById("btn-new-game").orElseThrow();
        UiDomElement continueGame = panel.document().getElementById("btn-continue").orElseThrow();
        assertTrue(newGame.hasAttribute("disabled"));
        assertTrue(continueGame.hasAttribute("disabled"));
        assertTrue(newGame.classList().contains("operation-busy"));
        assertEquals(begin.operation().operationId(), newGame.data("operation-id", ""));

        assertTrue(runtime.emitPointerClick(newGame, "junit"));
        Map<String, Object> rejectedEvent = context.modules()
                .getEventBus()
                .latest(EngineEventTopics.UI_ACTION_REJECTED);
        assertFalse(rejectedEvent.isEmpty());
        assertEquals("element-disabled", payload(rejectedEvent).get("reason"));
        assertEquals(START_OPERATION, payload(rejectedEvent).get("operation"));

        assertTrue(coordinator.complete(begin.operation().operationId(), Map.of("playerRef", 9)));
        assertFalse(newGame.hasAttribute("disabled"));
        assertFalse(continueGame.hasAttribute("disabled"));
        assertFalse(newGame.classList().contains("operation-busy"));
        runtime.close();
    }

    @Test
    void liveOperationEventsLockAllElementsSharingTheOperationKey() throws Exception {
        TestEngineContext context = new TestEngineContext();
        EngineOperationCoordinator coordinator = new EngineOperationCoordinator(context);
        context.registerService(EngineOperationCoordinator.class, coordinator);

        HtmlDomSwingPanel panel = new HtmlDomSwingPanel(MARKUP, CSS);
        panel.setSize(640, 360);
        panel.ensureLayout();
        FrozenLandsHtmlUiRuntime runtime = createRuntime(context, panel);
        installOperationBridge(runtime);

        UiDomElement newGame = panel.document().getElementById("btn-new-game").orElseThrow();
        UiDomElement continueGame = panel.document().getElementById("btn-continue").orElseThrow();
        assertFalse(newGame.hasAttribute("disabled"));

        EngineOperationCoordinator.BeginResult begin = coordinator.begin(
                START_OPERATION,
                Map.of("operation", "loadSlot", "slot", "quick"),
                "test"
        );
        assertTrue(newGame.hasAttribute("disabled"));
        assertTrue(continueGame.hasAttribute("disabled"));

        assertTrue(coordinator.fail(
                begin.operation().operationId(),
                "test failure",
                Map.of("failedModule", "engine.world")
        ));
        assertFalse(newGame.hasAttribute("disabled"));
        assertFalse(continueGame.hasAttribute("disabled"));
        runtime.close();
    }

    private FrozenLandsHtmlUiRuntime createRuntime(
            TestEngineContext context,
            HtmlDomSwingPanel panel
    ) throws Exception {
        Constructor<FrozenLandsHtmlUiRuntime> constructor = FrozenLandsHtmlUiRuntime.class.getDeclaredConstructor(
                org.takesome.frozenlands.engine.EngineContext.class,
                Path.class,
                FrozenLandsHtmlUiManifest.class,
                HtmlDomSwingPanel.class,
                HtmlDomLuaRuntime.class,
                String.class,
                String.class,
                List.class
        );
        constructor.setAccessible(true);
        FrozenLandsHtmlUiRuntime runtime = constructor.newInstance(
                context,
                Path.of("."),
                null,
                panel,
                new HtmlDomLuaRuntime(panel.document()),
                MARKUP,
                CSS,
                List.of()
        );
        assertNotNull(runtime);
        return runtime;
    }

    private void installOperationBridge(FrozenLandsHtmlUiRuntime runtime) throws Exception {
        Method method = FrozenLandsHtmlUiRuntime.class.getDeclaredMethod("installOperationEventBridge");
        method.setAccessible(true);
        method.invoke(runtime);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object value = event.get("payload");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
