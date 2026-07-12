package org.takesome.frozenlands.engine.ui.html;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jme3.app.Application;
import dev.takesome.htmldom.desktop.HtmlDomEventDispatcher;
import dev.takesome.htmldom.desktop.HtmlDomSwingPanel;
import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomNode;
import dev.takesome.htmldom.scripting.lua.HtmlDomLuaRuntime;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.operations.EngineOperationCoordinator;
import org.takesome.frozenlands.engine.shutdown.RuntimeShutdown;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FrozenLandsHtmlUiRuntime {
    public static final String DEFAULT_UI_ROOT = "assets/ui";
    public static final String BUILTIN_UI_ROOT = "builtin/ui";
    public static final String DEFAULT_MANIFEST = "ui.manifest.json";

    private static final String DEFAULT_SAVE_SLOT = "quick";
    private static final String UI_MODULE_ID = "frozenlands.ui";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter SAVE_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SAVE_LOCAL_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final EngineContext context;
    private final Path uiRoot;
    private final FrozenLandsHtmlUiManifest manifest;
    private final HtmlDomSwingPanel panel;
    private final HtmlDomLuaRuntime luaRuntime;
    private final String html;
    private final String css;
    private final List<String> loadedScripts;
    private final List<AutoCloseable> eventSubscriptions = new ArrayList<>();
    private final Set<String> busyOperations = new LinkedHashSet<>();

    private String lastClickFingerprint = "";
    private long lastClickFingerprintAtNanos;
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private String settingsReturnScreen = "main-menu";
    private String settingsSection = "overview";

    private FrozenLandsHtmlUiRuntime(
            EngineContext context,
            Path uiRoot,
            FrozenLandsHtmlUiManifest manifest,
            HtmlDomSwingPanel panel,
            HtmlDomLuaRuntime luaRuntime,
            String html,
            String css,
            List<String> loadedScripts
    ) {
        this.context = context;
        this.uiRoot = uiRoot;
        this.manifest = manifest;
        this.panel = panel;
        this.luaRuntime = luaRuntime;
        this.html = html;
        this.css = css;
        this.loadedScripts = List.copyOf(loadedScripts);
    }

    public static FrozenLandsHtmlUiRuntime loadDefault(EngineContext context) throws IOException {
        String explicit = System.getProperty("frozenlands.uiRoot");
        if (explicit != null && !explicit.isBlank()) {
            Path uiRoot = Path.of(explicit).toAbsolutePath().normalize();
            return load(context, uiRoot.resolve(DEFAULT_MANIFEST));
        }
        return loadBuiltIn(context);
    }

    private static FrozenLandsHtmlUiRuntime loadBuiltIn(EngineContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        FrozenLandsHtmlUiManifest manifest = OBJECT_MAPPER.readValue(readBuiltIn(DEFAULT_MANIFEST), FrozenLandsHtmlUiManifest.class);
        validateBuiltInManifest(manifest);

        String html = readBuiltIn(manifest.getEntry());
        String css = readBuiltInStyles(manifest.getStyles());
        HtmlDomSwingPanel panel = new HtmlDomSwingPanel(html, css);
        panel.setOpaque(false);
        panel.setBackground(new Color(0, 0, 0, 0));
        panel.setDoubleBuffered(false);
        panel.setFocusable(false);

        HtmlDomLuaRuntime luaRuntime = new HtmlDomLuaRuntime(panel.document());
        List<String> loadedScripts = executeBuiltInScripts(manifest.getScripts(), luaRuntime);
        FrozenLandsHtmlUiRuntime runtime = new FrozenLandsHtmlUiRuntime(context, Path.of(BUILTIN_UI_ROOT), manifest, panel, luaRuntime, html, css, loadedScripts);
        runtime.installDefaultEventBridge();
        runtime.installOperationEventBridge();
        runtime.setScreen(manifest.getStartupScreen());
        runtime.refreshSaveSlots();
        runtime.applyGameplayGate(runtime.isMenuScreen(runtime.screen()), "html-ui-startup");
        return runtime;
    }

    public static FrozenLandsHtmlUiRuntime load(EngineContext context, Path manifestPath) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(manifestPath, "manifestPath");

        Path normalizedManifest = manifestPath.toAbsolutePath().normalize();
        Path uiRoot = normalizedManifest.getParent();
        FrozenLandsHtmlUiManifest manifest = OBJECT_MAPPER.readValue(normalizedManifest.toFile(), FrozenLandsHtmlUiManifest.class);
        validateManifest(manifest, uiRoot);

        String html = readRelative(uiRoot, manifest.getEntry());
        String css = readStyles(uiRoot, manifest.getStyles());
        HtmlDomSwingPanel panel = new HtmlDomSwingPanel(html, css);
        panel.setOpaque(false);
        panel.setBackground(new Color(0, 0, 0, 0));
        panel.setDoubleBuffered(false);
        panel.setFocusable(false);

        HtmlDomLuaRuntime luaRuntime = new HtmlDomLuaRuntime(panel.document());
        List<String> loadedScripts = executeScripts(uiRoot, manifest.getScripts(), luaRuntime);
        FrozenLandsHtmlUiRuntime runtime = new FrozenLandsHtmlUiRuntime(context, uiRoot, manifest, panel, luaRuntime, html, css, loadedScripts);
        runtime.installDefaultEventBridge();
        runtime.installOperationEventBridge();
        runtime.setScreen(manifest.getStartupScreen());
        runtime.refreshSaveSlots();
        runtime.applyGameplayGate(runtime.isMenuScreen(runtime.screen()), "html-ui-startup");
        return runtime;
    }

    private static void validateBuiltInManifest(FrozenLandsHtmlUiManifest manifest) throws IOException {
        if (manifest.getEntry() == null || manifest.getEntry().isBlank()) {
            throw new IOException("Built-in HTML UI manifest entry is blank");
        }
        readBuiltIn(manifest.getEntry());
        for (String style : manifest.getStyles()) {
            readBuiltIn(style);
        }
        for (String script : manifest.getScripts()) {
            readBuiltIn(script);
        }
        if (manifest.getBindings() != null && !manifest.getBindings().isBlank()) {
            readBuiltIn(manifest.getBindings());
        }
    }

    private static String readBuiltInStyles(List<String> styles) throws IOException {
        StringBuilder css = new StringBuilder(8192);
        for (String style : styles) {
            css.append("\n/* built-in ").append(style).append(" */\n");
            css.append(readBuiltIn(style)).append('\n');
        }
        return css.toString();
    }

    private static List<String> executeBuiltInScripts(List<String> scripts, HtmlDomLuaRuntime luaRuntime) throws IOException {
        List<String> loaded = new ArrayList<>();
        for (String script : scripts) {
            luaRuntime.execute(readBuiltIn(script), script);
            loaded.add(script);
        }
        return loaded;
    }

    private static String readBuiltIn(String relative) throws IOException {
        String normalizedRelative = normalizeBuiltInRelative(relative);
        String resource = BUILTIN_UI_ROOT + "/" + normalizedRelative;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = FrozenLandsHtmlUiRuntime.class.getClassLoader();
        }
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Built-in HTML UI resource is missing: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalizeBuiltInRelative(String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            throw new IOException("Blank built-in HTML UI resource path");
        }
        String normalizedRelative = relative.replace('\\', '/');
        if (normalizedRelative.startsWith("/") || normalizedRelative.contains("../")) {
            throw new IOException("Built-in HTML UI resource escapes root: " + relative);
        }
        return normalizedRelative;
    }

    private static Path resolveUiRoot() {
        String explicit = System.getProperty("frozenlands.uiRoot");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }

        String runtimeRoot = System.getProperty("frozenlands.runtimeRoot");
        if (runtimeRoot != null && !runtimeRoot.isBlank()) {
            Path candidate = Path.of(runtimeRoot).resolve(DEFAULT_UI_ROOT).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        return Path.of(DEFAULT_UI_ROOT).toAbsolutePath().normalize();
    }

    private static void validateManifest(FrozenLandsHtmlUiManifest manifest, Path uiRoot) throws IOException {
        if (manifest.getEntry() == null || manifest.getEntry().isBlank()) {
            throw new IOException("HTML UI manifest entry is blank");
        }
        requireFile(uiRoot, manifest.getEntry(), "entry");
        for (String style : manifest.getStyles()) {
            requireFile(uiRoot, style, "style");
        }
        for (String script : manifest.getScripts()) {
            requireFile(uiRoot, script, "script");
        }
        if (manifest.getBindings() != null && !manifest.getBindings().isBlank()) {
            requireFile(uiRoot, manifest.getBindings(), "bindings");
        }
    }

    private static void requireFile(Path uiRoot, String relative, String role) throws IOException {
        Path path = resolveInsideUiRoot(uiRoot, relative);
        if (!Files.isRegularFile(path)) {
            throw new IOException("HTML UI " + role + " file is missing: " + relative + " resolved=" + path);
        }
    }

    private static String readRelative(Path uiRoot, String relative) throws IOException {
        return Files.readString(resolveInsideUiRoot(uiRoot, relative), StandardCharsets.UTF_8);
    }

    private static String readStyles(Path uiRoot, List<String> styles) throws IOException {
        StringBuilder css = new StringBuilder(8192);
        for (String style : styles) {
            css.append("\n/* ").append(style).append(" */\n");
            css.append(readRelative(uiRoot, style)).append('\n');
        }
        return css.toString();
    }

    private static List<String> executeScripts(Path uiRoot, List<String> scripts, HtmlDomLuaRuntime luaRuntime) throws IOException {
        List<String> loaded = new ArrayList<>();
        for (String script : scripts) {
            String code = readRelative(uiRoot, script);
            luaRuntime.execute(code, script);
            loaded.add(script);
        }
        return loaded;
    }

    private static Path resolveInsideUiRoot(Path uiRoot, String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            throw new IOException("Blank HTML UI resource path");
        }
        String normalizedRelative = relative.replace('\\', '/');
        if (normalizedRelative.startsWith("/") || normalizedRelative.contains("../")) {
            throw new IOException("HTML UI resource escapes ui root: " + relative);
        }
        Path resolved = uiRoot.resolve(normalizedRelative).toAbsolutePath().normalize();
        if (!resolved.startsWith(uiRoot.toAbsolutePath().normalize())) {
            throw new IOException("HTML UI resource escapes ui root: " + relative);
        }
        return resolved;
    }

    private void installDefaultEventBridge() {
        try {
            panel.addEventListener("[data-action]", "click", this::handleClick);
            for (UiDomElement input : document().getElementsByTagName("input")) {
                if (dataBoolean(input, "range-event", false)) {
                    panel.addEventListener(
                            input,
                            "input",
                            this::handleRangeInput
                    );
                }
            }
        } catch (RuntimeException exception) {
            context.getLogger().warn(
                    "Html UI event bridge was not installed: {}",
                    exception.toString()
            );
        }
    }

    private void installOperationEventBridge() {
        eventSubscriptions.add(context.getModuleRegistry().getEventBus().subscribe(
                "engine.operation.*",
                this::onOperationEvent,
                false
        ));
        context.findService(EngineOperationCoordinator.class).ifPresent(coordinator -> {
            for (Map<String, Object> snapshot : coordinator.activeSnapshots()) {
                setOperationBusy(
                        safeString(snapshot.get("operation")),
                        true,
                        safeString(snapshot.get("operationId")),
                        safeString(snapshot.get("state"))
                );
            }
        });
    }

    private void onOperationEvent(Map<String, Object> event) {
        String topic = safeString(event == null ? null : event.get("topic"));
        Map<String, Object> payload = eventPayload(event);
        String operation = safeString(payload.get("operation"));
        if (operation.isBlank()) {
            return;
        }

        boolean busy = EngineEventTopics.ENGINE_OPERATION_STARTED.equals(topic)
                || EngineEventTopics.ENGINE_OPERATION_PROGRESS.equals(topic)
                || (EngineEventTopics.ENGINE_OPERATION_REJECTED.equals(topic)
                && booleanValue(payload.get("busy"), false));
        boolean terminal = EngineEventTopics.ENGINE_OPERATION_COMPLETED.equals(topic)
                || EngineEventTopics.ENGINE_OPERATION_FAILED.equals(topic)
                || EngineEventTopics.ENGINE_OPERATION_CANCELLED.equals(topic);

        if (busy) {
            setOperationBusy(operation, true, safeString(payload.get("operationId")), safeString(payload.get("state")));
        } else if (terminal) {
            setOperationBusy(operation, false, safeString(payload.get("operationId")), safeString(payload.get("state")));
        }

        if (EngineEventTopics.ENGINE_OPERATION_REJECTED.equals(topic)) {
            context.getLogger().info(
                    "Html UI operation request rejected operation={} operationId={} reason={}",
                    operation,
                    payload.get("operationId"),
                    payload.get("reason")
            );
        }
    }

    private void setOperationBusy(String operation, boolean busy, String operationId, String state) {
        synchronized (busyOperations) {
            if (busy) {
                busyOperations.add(operation);
            } else {
                busyOperations.remove(operation);
            }
        }
        applyOperationState(document().documentElement(), operation, busy, operationId, state);
        panel.invalidateLayout();
        panel.repaintHost();
    }

    private void applyOperationState(
            UiDomNode node,
            String operation,
            boolean busy,
            String operationId,
            String state
    ) {
        if (node instanceof UiDomElement element && operation.equals(element.data("operation", ""))) {
            if (busy) {
                if (!element.hasAttribute("disabled")) {
                    element.setAttribute("disabled", "disabled");
                    element.setAttribute("data-operation-disabled-owned", "true");
                }
                element.setAttribute("data-operation-state", state == null || state.isBlank() ? "running" : state);
                element.setAttribute("data-operation-id", operationId == null ? "" : operationId);
                element.classList().add("operation-busy");
            } else {
                if ("true".equals(element.data("operation-disabled-owned", ""))) {
                    element.removeAttribute("disabled");
                }
                element.removeAttribute("data-operation-disabled-owned");
                element.removeAttribute("data-operation-state");
                element.removeAttribute("data-operation-id");
                element.classList().remove("operation-busy");
            }
        }
        for (UiDomNode child : node.children()) {
            applyOperationState(child, operation, busy, operationId, state);
        }
    }

    private boolean operationBusy(String operation) {
        if (operation == null || operation.isBlank()) {
            return false;
        }
        synchronized (busyOperations) {
            return busyOperations.contains(operation);
        }
    }

    private void reapplyBusyOperations() {
        List<String> operations;
        synchronized (busyOperations) {
            operations = List.copyOf(busyOperations);
        }
        for (String operation : operations) {
            applyOperationState(document().documentElement(), operation, true, "", "running");
        }
    }

    private void handleClick(HtmlDomEventDispatcher.DomEvent event) {
        UiDomElement target = event.currentTarget() == null
                ? event.target()
                : event.currentTarget();
        if (target == null) {
            return;
        }
        if (executeUiClick(target, "html-dom-click")) {
            event.preventDefault();
        }
    }

    private void handleRangeInput(HtmlDomEventDispatcher.DomEvent event) {
        UiDomElement target = event.currentTarget() == null
                ? event.target()
                : event.currentTarget();
        if (target == null) {
            return;
        }
        if (executeUiValueEvent(target, "html-dom-range-input")) {
            event.preventDefault();
        }
    }

    private boolean executeUiValueEvent(
            UiDomElement target,
            String origin
    ) {
        Map<String, Object> payload = buildUiEventPayload(target);
        String topic = safeString(payload.get("event"));
        String elementId = safeString(payload.get("elementId"));
        if (topic.isBlank()) {
            context.getLogger().warn(
                    "Html UI range has no data-event: elementId={}",
                    elementId
            );
            return false;
        }
        if (target.hasAttribute("disabled")) {
            return rejectUiAction(payload, origin, "element-disabled");
        }

        publishUiLifecycle(
                EngineEventTopics.UI_ACTION_REQUESTED,
                payload,
                origin,
                "input"
        );
        payload.put("consumed", true);
        payload.put("origin", origin);
        Map<String, Object> published = publishUiEvent(topic, payload, origin);
        Map<String, Object> dispatched = new LinkedHashMap<>(payload);
        dispatched.put("eventId", published.get("eventId"));
        dispatched.put("delivered", published.get("delivered"));
        dispatched.put("listenerFailures", published.get("listenerFailures"));
        publishUiLifecycle(
                EngineEventTopics.UI_ACTION_DISPATCHED,
                dispatched,
                origin,
                "input-dispatched"
        );
        context.getLogger().debug(
                "Html UI range event topic={} elementId={} value={} "
                        + "volume={} sensitivity={} delivered={}",
                topic,
                elementId,
                payload.get("value"),
                payload.get("volume"),
                payload.get("sensitivity"),
                published.get("delivered")
        );
        panel.repaintHost();
        return true;
    }

    public boolean emitPointerClick(UiDomElement target, String origin) {
        return executeUiClick(target, origin == null || origin.isBlank() ? "ui-pointer-click" : origin);
    }

    private boolean executeUiClick(UiDomElement target, String origin) {
        Map<String, Object> payload = buildUiEventPayload(target);
        String topic = safeString(payload.get("event"));
        String elementId = safeString(payload.get("elementId"));
        String operation = safeString(payload.get("operation"));
        if (topic.isBlank()) {
            context.getLogger().warn("Html UI element has no data-event/action mapping: elementId={} action={}", elementId, payload.get("action"));
            return false;
        }

        publishUiLifecycle(EngineEventTopics.UI_ACTION_REQUESTED, payload, origin, "requested");
        if (target.hasAttribute("disabled")) {
            return rejectUiAction(payload, origin, "element-disabled");
        }
        if (operationBusy(operation)) {
            return rejectUiAction(payload, origin, "operation-already-running");
        }
        if (duplicateClick(payload)) {
            context.getLogger().debug("Html UI duplicate click suppressed topic={} elementId={} origin={}", topic, elementId, origin);
            return rejectUiAction(payload, origin, "duplicate-click-window");
        }

        boolean luaConsumed = false;
        try {
            luaConsumed = luaRuntime.call("onUiEvent", topic, elementId);
        } catch (RuntimeException exception) {
            context.getLogger().error("Html UI Lua onUiEvent failed topic={} elementId={} message={}", topic, elementId, exception.toString());
        }

        boolean consumed = luaConsumed || booleanValue(payload.get("consume"), true);
        payload.put("luaConsumed", luaConsumed);
        payload.put("consumed", consumed);
        payload.put("origin", origin);

        context.getLogger().info(
                "Html UI event emitted topic={} elementId={} screen={} action={} "
                        + "operation={} slot={} category={} preset={} volume={} "
                        + "sensitivity={} origin={}",
                topic,
                elementId,
                payload.get("screen"),
                payload.get("action"),
                operation,
                payload.get("slot"),
                payload.get("category"),
                payload.get("preset"),
                payload.get("volume"),
                payload.get("sensitivity"),
                origin
        );

        Map<String, Object> published = publishUiEvent(topic, payload, origin);
        Map<String, Object> dispatched = new LinkedHashMap<>(payload);
        dispatched.put("eventId", published.get("eventId"));
        dispatched.put("delivered", published.get("delivered"));
        dispatched.put("listenerFailures", published.get("listenerFailures"));
        publishUiLifecycle(EngineEventTopics.UI_ACTION_DISPATCHED, dispatched, origin, "dispatched");
        context.getLogger().info("Html UI event published topic={} eventId={} delivered={} listenerFailures={} origin={}",
                topic,
                published.get("eventId"),
                published.get("delivered"),
                published.get("listenerFailures"),
                origin);

        try {
            luaRuntime.call("afterUiEvent", topic, elementId);
        } catch (RuntimeException exception) {
            context.getLogger().error("Html UI Lua afterUiEvent failed topic={} elementId={} message={}", topic, elementId, exception.toString());
        }
        panel.invalidateLayout();
        panel.repaintHost();
        return consumed || intValue(published.get("delivered"), 0) > 0;
    }

    private boolean rejectUiAction(Map<String, Object> payload, String origin, String reason) {
        publishUiLifecycle(EngineEventTopics.UI_ACTION_REJECTED, payload, origin, reason);
        context.getLogger().info(
                "Html UI action rejected topic={} elementId={} operation={} reason={} origin={}",
                payload.get("event"),
                payload.get("elementId"),
                payload.get("operation"),
                reason,
                origin
        );
        return true;
    }

    private void publishUiLifecycle(String topic, Map<String, Object> action, String origin, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>(action == null ? Map.of() : action);
        payload.put("origin", origin == null ? "" : origin);
        payload.put("reason", reason == null ? "" : reason);
        context.getModuleRegistry().publishEvent(topic, payload, UI_MODULE_ID, Map.of(
                "uiAction", true,
                "screen", screen()
        ));
    }

    private boolean duplicateClick(Map<String, Object> payload) {
        String fingerprint = safeString(payload.get("event"))
                + "|" + safeString(payload.get("elementId"))
                + "|" + safeString(payload.get("action"))
                + "|" + safeString(payload.get("slot"))
                + "|" + safeString(payload.get("category"))
                + "|" + safeString(payload.get("preset"))
                + "|" + safeString(payload.get("volume"))
                + "|" + safeString(payload.get("sensitivity"));
        long now = System.nanoTime();
        if (fingerprint.equals(lastClickFingerprint) && now - lastClickFingerprintAtNanos < 120_000_000L) {
            return true;
        }
        lastClickFingerprint = fingerprint;
        lastClickFingerprintAtNanos = now;
        return false;
    }

    private Map<String, Object> buildUiEventPayload(UiDomElement target) {
        Map<String, Object> payload = readDataPayload(target.data("payload", ""));
        String topic = target.data("event", "");
        if (topic.isBlank()) {
            topic = safeString(payload.get("event"));
        }
        if (topic.isBlank()) {
            topic = safeString(payload.get("topic"));
        }

        String elementId = target.id() == null ? "" : target.id();
        String action = target.data("action", "");
        if (topic.isBlank()) {
            topic = legacyActionTopic(action);
        }
        String slot = target.data("slot", "");
        String operation = target.data("operation", "");
        String module = target.data("module", "");
        String command = target.data("command", "");

        copyDataValue(target, payload, "category");
        copyDataValue(target, payload, "preset");
        copyDataValue(target, payload, "volume");
        copyDataValue(target, payload, "sensitivity");
        copyDataValue(target, payload, "section");
        copyDataValue(target, payload, "value");
        copyDataValue(target, payload, "setting");

        String controlValue = target.attribute("value", "").trim();
        if (!controlValue.isBlank()) {
            payload.put("value", controlValue);
            String setting = target.data("setting", "").trim();
            if ("volume".equals(setting)) {
                payload.put("volume", controlValue);
            } else if ("sensitivity".equals(setting)) {
                payload.put("sensitivity", controlValue);
            }
        }

        payload.put("event", topic);
        payload.put("topic", topic);
        payload.put("action", action);
        payload.put("elementId", elementId);
        payload.put("slot", slot);
        payload.put("operation", operation);
        payload.put("screen", screen());
        payload.put("source", "html-dom");
        payload.put("htmlUi", true);
        payload.put("consume", dataBoolean(target, "consume", true));
        if (!module.isBlank()) {
            payload.put("module", module);
        }
        if (!command.isBlank()) {
            payload.put("command", command);
        }
        return payload;
    }

    private void copyDataValue(
            UiDomElement target,
            Map<String, Object> payload,
            String key
    ) {
        String value = target.data(key, "");
        if (!value.isBlank()) {
            payload.put(key, value);
        }
    }

    private String legacyActionTopic(String action) {
        String safeAction = action == null ? "" : action.trim();
        if ("game.new".equals(safeAction)) return "ui.menu.newGame";
        if ("game.continue".equals(safeAction) || "game.loadLatest".equals(safeAction)) return "ui.menu.continue";
        if ("game.refreshSaves".equals(safeAction)) return "ui.menu.refreshSaves";
        if ("game.loadSlot".equals(safeAction)) return "ui.menu.loadSlot";
        if ("game.save".equals(safeAction)) return "ui.menu.save";
        if ("game.overwriteSave".equals(safeAction)) return "ui.menu.overwriteSave";
        if ("game.deleteSave".equals(safeAction)) return "ui.menu.deleteSave";
        if ("game.resume".equals(safeAction)) return "ui.menu.resume";
        if ("ui.settings".equals(safeAction)) return "ui.menu.settings";
        if ("ui.settingsBack".equals(safeAction)) return "ui.menu.settingsBack";
        if ("ui.mainMenu".equals(safeAction)) return "ui.menu.mainMenu";
        if (("game." + "exitToDesktop").equals(safeAction)) return "ui.menu.exitToDesktop";
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDataPayload(String rawPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (rawPayload == null || rawPayload.isBlank()) {
            return payload;
        }
        try {
            Object decoded = OBJECT_MAPPER.readValue(rawPayload, Object.class);
            if (decoded instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    payload.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                payload.put("value", decoded);
            }
        } catch (IOException exception) {
            payload.put("payloadParseError", exception.getMessage());
            payload.put("rawPayload", rawPayload);
        }
        return payload;
    }

    public Map<String, Object> dispatchNativeAction(String action, String slot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action == null ? "" : action);
        payload.put("slot", slot == null ? "" : slot);
        payload.put("source", "module-command");
        payload.put("htmlUi", true);
        return dispatchUiEvent(action, payload, "module-command-action.dispatch");
    }

    public Map<String, Object> dispatchUiEvent(String topic, Map<String, Object> payload, String origin) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        String safeTopic = topic == null ? "" : topic.trim();
        if (safeTopic.isBlank()) {
            safeTopic = safeString(safePayload.get("event"));
        }
        if (safeTopic.isBlank()) {
            safeTopic = safeString(safePayload.get("topic"));
        }
        if (safeTopic.isBlank()) {
            safeTopic = safeString(safePayload.get("action"));
        }
        if (safeTopic.isBlank()) {
            Map<String, Object> result = base(false);
            result.put("error", "blank-ui-event-topic");
            result.put("message", "UI event topic is blank; declare data-event on the emitting element.");
            return result;
        }

        safePayload.put("event", safeTopic);
        safePayload.put("topic", safeTopic);
        safePayload.putIfAbsent("screen", screen());
        safePayload.putIfAbsent("htmlUi", true);
        safePayload.putIfAbsent("source", origin == null || origin.isBlank() ? "ui-runtime" : origin);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", origin == null || origin.isBlank() ? "ui-runtime" : origin);
        metadata.put("screen", screen());
        metadata.put("module", UI_MODULE_ID);

        Map<String, Object> event = context.getModuleRegistry().publishEvent(safeTopic, safePayload, UI_MODULE_ID, metadata);
        Map<String, Object> result = base(true);
        result.put("event", safeTopic);
        result.put("topic", safeTopic);
        result.put("eventId", event.get("id"));
        result.put("sequence", event.get("sequence"));
        result.put("delivered", event.get("delivered"));
        result.put("listenerFailures", event.get("listenerFailures"));
        return result;
    }

    private Map<String, Object> publishUiEvent(String topic, Map<String, Object> payload, String origin) {
        return dispatchUiEvent(topic, payload, origin);
    }

    private Map<String, Object> eventPayload(Map<String, Object> event) {
        if (event == null) {
            return Map.of();
        }
        Object payload = event.get("payload");
        if (!(payload instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private boolean dataBoolean(UiDomElement target, String key, boolean fallback) {
        return booleanValue(target.data(key, String.valueOf(fallback)), fallback);
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text) || "off".equalsIgnoreCase(text)) {
            return false;
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Map<String, Object> base(boolean ok) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("module", UI_MODULE_ID);
        return result;
    }

    private Map<String, Object> callModule(String moduleId, String commandId, Map<String, Object> arguments) {
        return context.getModuleRegistry().call(moduleId, commandId, arguments);
    }

    public void enterGameplay(String reason) {
        setScreen("hud");
        applyGameplayGate(false, reason);
    }

    public void openSettings(String requestedReturnScreen, String reason) {
        String current = screen();
        String requested = requestedReturnScreen == null
                ? ""
                : requestedReturnScreen.trim();
        if ("main-menu".equals(requested) || "pause".equals(requested)) {
            settingsReturnScreen = requested;
        } else if ("main-menu".equals(current) || "pause".equals(current)) {
            settingsReturnScreen = current;
        }
        setSettingsSection("overview");
        setScreen("settings");
        applyGameplayGate(true, reason == null || reason.isBlank()
                ? "html-ui-settings-open"
                : reason);
    }

    public void closeSettings(String reason) {
        String target = "pause".equals(settingsReturnScreen)
                ? "pause"
                : "main-menu";
        setSettingsSection("overview");
        setScreen(target);
        applyGameplayGate(true, reason == null || reason.isBlank()
                ? "html-ui-settings-close"
                : reason);
    }

    public String settingsReturnScreen() {
        return settingsReturnScreen;
    }

    public String settingsSection() {
        return settingsSection;
    }

    public void setSettingsSection(String section) {
        String normalized = section == null
                ? "overview"
                : section.trim().toLowerCase(Locale.ROOT);
        if (!"graphics".equals(normalized)
                && !"audio".equals(normalized)
                && !"controls".equals(normalized)) {
            normalized = "overview";
        }
        settingsSection = normalized;
        setSettingsPanelVisible(
                "settings-panel-overview",
                "overview".equals(normalized)
        );
        setSettingsPanelVisible(
                "settings-panel-graphics",
                "graphics".equals(normalized)
        );
        setSettingsPanelVisible(
                "settings-panel-audio",
                "audio".equals(normalized)
        );
        setSettingsPanelVisible(
                "settings-panel-controls",
                "controls".equals(normalized)
        );
        panel.invalidateLayout();
        panel.repaintHost();
    }

    private void setSettingsPanelVisible(String elementId, boolean visible) {
        document().getElementById(elementId).ifPresent(panelElement -> {
            panelElement.setAttribute(
                    "data-visible",
                    visible ? "true" : "false"
            );
            if (visible) {
                panelElement.classList().add("active");
            } else {
                panelElement.classList().remove("active");
            }
            panelElement.removeAttribute("style");
        });
    }

    public void setRangeControlValue(
            String rangeId,
            float value,
            String valueLabelId,
            String displayValue
    ) {
        String formatted = formatControlValue(value);
        document().getElementById(rangeId).ifPresent(range ->
                range.setAttribute("value", formatted)
        );
        if (valueLabelId != null && !valueLabelId.isBlank()) {
            setText(valueLabelId, displayValue);
        } else {
            panel.repaintHost();
        }
    }

    private String formatControlValue(float value) {
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted.endsWith(".")
                ? formatted.substring(0, formatted.length() - 1)
                : formatted;
    }

    public void setSelectedGraphicsPreset(String preset) {
        String selected = preset == null
                ? "HIGH"
                : preset.trim().toUpperCase(Locale.ROOT);
        Map<String, String> buttons = Map.of(
                "LOW", "btn-graphics-low",
                "MEDIUM", "btn-graphics-medium",
                "HIGH", "btn-graphics-high",
                "ULTRA", "btn-graphics-ultra"
        );
        buttons.forEach((name, elementId) ->
                document().getElementById(elementId).ifPresent(button -> {
                    if (name.equals(selected)) {
                        button.classList().add("selected");
                    } else {
                        button.classList().remove("selected");
                    }
                })
        );
        panel.invalidateLayout();
        panel.repaintHost();
    }

    public void reapplyCurrentGameplayGate(String reason) {
        applyGameplayGate(isMenuScreen(screen()), reason);
    }

    public void applyGameplayGate(boolean menuVisible, String reason) {
        if (context.getInputManager() != null) {
            context.getInputManager().setCursorVisible(menuVisible);
        }
        Map<String, Object> focusPayload = new LinkedHashMap<>();
        focusPayload.put("focused", !menuVisible);
        focusPayload.put("paused", menuVisible);
        focusPayload.put("reason", reason);
        focusPayload.put("htmlUi", true);
        focusPayload.put("screen", screen());
        context.getModuleRegistry().publishEvent(EngineEventTopics.APPLICATION_FOCUS_CHANGED, focusPayload);
        context.getModuleRegistry().publishEvent(EngineEventTopics.APPLICATION_FOCUS_PAUSE_REQUESTED, Map.of("paused", menuVisible, "reason", reason, "htmlUi", true, "screen", screen()));
        context.getModuleRegistry().publishEvent(EngineEventTopics.GAME_MENU_VISIBILITY_CHANGED, Map.of("visible", menuVisible, "screen", screen(), "reason", reason, "htmlUi", true));
        context.getModuleRegistry().publishEvent(EngineEventTopics.CURSOR_VISIBILITY_REQUESTED, Map.of("visible", menuVisible));
        context.getModuleRegistry().publishEvent(EngineEventTopics.CAMERA_FOLLOW_PAUSE_REQUESTED, Map.of("paused", menuVisible));
        context.getModuleRegistry().publishEvent(EngineEventTopics.CAMERA_LOOK_INPUT_ENABLED_REQUESTED, Map.of("enabled", !menuVisible));
        context.getModuleRegistry().publishEvent(EngineEventTopics.PLAYER_INPUT_ENABLED_REQUESTED, Map.of("enabled", !menuVisible));
        context.getLogger().info("Html UI gameplay gate menuVisible={} screen={} reason={}", menuVisible, screen(), reason);
    }

    public void requestExitToDesktop(String reason) {
        showStatus("Stopping runtime.");
        String safeReason = reason == null || reason.isBlank() ? "html-ui-exit-to-desktop" : reason;
        RuntimeShutdown.request(context, safeReason);
    }

    public boolean handleEscape() {
        String current = screen();
        if ("main-menu".equals(current)) {
            showStatus("Main menu ready.");
            applyGameplayGate(true, "escape-main-menu");
            return true;
        }
        if ("pause".equals(current)) {
            enterGameplay("escape-resume");
            return true;
        }
        if ("settings".equals(current)) {
            if (!"overview".equals(settingsSection)) {
                setSettingsSection("overview");
                showStatus("Select a category.");
                return true;
            }
            closeSettings("escape-settings");
            return true;
        }
        setScreen("pause");
        applyGameplayGate(true, "escape-pause");
        return true;
    }

    public void togglePauseScreen() {
        String current = screen();
        if ("pause".equals(current)) {
            enterGameplay("toggle-resume");
            return;
        }
        if ("main-menu".equals(current)) {
            applyGameplayGate(true, "toggle-main-menu");
            return;
        }
        if ("settings".equals(current)) {
            if (!"overview".equals(settingsSection)) {
                setSettingsSection("overview");
                showStatus("Select a category.");
                return;
            }
            closeSettings("toggle-settings");
            return;
        }
        setScreen("pause");
        applyGameplayGate(true, "toggle-pause");
    }

    public void refreshSaveSlots() {
        List<Map<String, Object>> saves = saves();
        renderSaveList("save-list", saves);
        renderSaveList("pause-save-list", saves);
        updateContinueButton(saves);
        String summary = saves.size() + " save" + (saves.size() == 1 ? "" : "s");
        setText("save-count", saves.isEmpty() ? "" : summary);
        setSaveCountVisible(!saves.isEmpty());
        if (saves.isEmpty()) {
            showStatus("No saves. Start expedition.");
        }
    }

    private List<Map<String, Object>> saves() {
        try {
            Map<String, Object> result = callModule("engine.save", "list", Map.of());
            Object savesObject = result.get("saves");
            if (!(savesObject instanceof List<?> rawSaves)) {
                return List.of();
            }
            List<Map<String, Object>> saves = new ArrayList<>();
            for (Object rawSave : rawSaves) {
                if (rawSave instanceof Map<?, ?> rawMap) {
                    Map<String, Object> save = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        save.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    saves.add(save);
                }
            }
            saves.sort(Comparator
                    .comparing((Map<String, Object> save) -> safeString(save.get("modifiedAt")))
                    .thenComparing(save -> safeString(save.get("slot"))));
            return saves;
        } catch (RuntimeException exception) {
            context.getLogger().warn("Failed to read save list for HTML UI: {}", exception.toString());
            showStatus("Save list unavailable: " + exception.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> latestSave(List<Map<String, Object>> saves) {
        return saves.get(saves.size() - 1);
    }

    private void renderSaveList(String elementId, List<Map<String, Object>> saves) {
        boolean pauseList = "pause-save-list".equals(elementId);
        document().getElementById(elementId).ifPresent(list -> {
            list.clearChildren();
            updateSaveListScrollState(elementId, saves.size());
            if (saves.isEmpty()) {
                UiDomElement empty = document().createElement("p");
                empty.setAttribute("class", "save-empty");
                empty.appendChild(document().createText("No saved expeditions yet."));
                list.appendChild(empty);
                return;
            }
            for (int index = saves.size() - 1; index >= 0; index--) {
                Map<String, Object> save = saves.get(index);
                boolean selected = index == saves.size() - 1;
                list.appendChild(saveRow(save, selected, pauseList));
            }
        });
        reapplyBusyOperations();
        panel.invalidateLayout();
        panel.repaintHost();
    }


    private void updateSaveListScrollState(String listElementId, int saveCount) {
        boolean pauseList = "pause-save-list".equals(listElementId);
        boolean scrollRequired = saveCount > 2;
        String shellClass = pauseList
                ? (scrollRequired ? "pause-save-list-shell" : "pause-save-list-shell no-scroll")
                : (scrollRequired ? "save-list-shell" : "save-list-shell no-scroll");
        String parentShellId = pauseList ? "pause-save-list-shell-runtime" : "save-list-shell-runtime";
        document().getElementById(parentShellId).ifPresent(shell -> shell.setAttribute("class", shellClass));
    }

    private UiDomElement saveRow(Map<String, Object> save, boolean selected, boolean pauseList) {
        String slot = safeString(save.get("slot"));
        String modifiedAt = safeString(save.get("modifiedAt"));

        UiDomElement row = document().createElement("div");
        row.setAttribute("class", pauseList ? "save-slot-row pause-save-slot-row" : "save-slot-row");
        row.setAttribute("data-slot", slot);

        UiDomElement loadButton = document().createElement("button");
        loadButton.setAttribute("class", selected ? "ui-button save-slot-button selected" : "ui-button save-slot-button");
        loadButton.setAttribute("data-event", "ui.menu.loadSlot");
        loadButton.setAttribute("data-action", "game.loadSlot");
        loadButton.setAttribute("data-operation", "runtime.game.start");
        loadButton.setAttribute("data-slot", slot);
        loadButton.setAttribute("title", "Load " + saveDisplayName(slot));

        UiDomElement name = textElement("span", "save-slot-name", saveDisplayName(slot));
        UiDomElement meta = document().createElement("span");
        meta.setAttribute("class", "save-slot-meta");
        meta.appendChild(textElement("i", "fa-solid fa-clock save-slot-clock", ""));
        meta.appendChild(textElement("span", "save-slot-time", saveTimestamp(modifiedAt)));
        loadButton.appendChild(name);
        loadButton.appendChild(meta);
        row.appendChild(loadButton);

        if (pauseList) {
            row.appendChild(saveActionButton(
                    slot,
                    "ui.menu.overwriteSave",
                    "game.overwriteSave",
                    "Overwrite " + saveDisplayName(slot),
                    "fa-floppy-disk",
                    "overwrite"
            ));
        }
        row.appendChild(saveActionButton(
                slot,
                "ui.menu.deleteSave",
                "game.deleteSave",
                "Delete " + saveDisplayName(slot),
                "fa-trash",
                "delete"
        ));
        return row;
    }

    private UiDomElement saveActionButton(
            String slot,
            String event,
            String action,
            String title,
            String iconClass,
            String kind
    ) {
        UiDomElement button = document().createElement("button");
        button.setAttribute("class", "ui-button save-slot-action save-slot-action-" + kind);
        button.setAttribute("data-event", event);
        button.setAttribute("data-action", action);
        button.setAttribute("data-slot", slot);
        button.setAttribute("title", title);
        button.appendChild(textElement("i", "fa-solid " + iconClass + " save-slot-action-icon", ""));
        return button;
    }

    private UiDomElement textElement(String tag, String className, String text) {
        UiDomElement element = document().createElement(tag);
        element.setAttribute("class", className);
        element.appendChild(document().createText(text == null ? "" : text));
        return element;
    }

    private String saveDisplayName(String slot) {
        String value = slot == null || slot.isBlank() ? DEFAULT_SAVE_SLOT : slot.trim();
        if (DEFAULT_SAVE_SLOT.equals(value)) {
            return value + " /";
        }
        return value;
    }

    private String saveTimestamp(String modifiedAt) {
        String value = formatSaveTimestamp(modifiedAt);
        return value.isBlank() ? "Saved checkpoint" : value;
    }

    private String formatSaveTimestamp(String modifiedAt) {
        if (modifiedAt == null || modifiedAt.isBlank()) {
            return "";
        }
        String value = modifiedAt.trim();
        try {
            return SAVE_TIMESTAMP_FORMAT.format(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return SAVE_TIMESTAMP_FORMAT.format(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return SAVE_LOCAL_TIMESTAMP_FORMAT.format(LocalDateTime.parse(value));
        } catch (DateTimeParseException ignored) {
        }
        int dot = value.indexOf('.');
        if (dot > 0 && value.length() >= 16) {
            return value.substring(0, dot).replace('T', ' ');
        }
        return value;
    }

    private void updateContinueButton(List<Map<String, Object>> saves) {
        document().getElementById("btn-continue").ifPresent(button -> {
            if (saves.isEmpty()) {
                button.setAttribute("disabled", "disabled");
                button.removeAttribute("data-operation-disabled-owned");
                button.setAttribute("data-slot", "");
            } else {
                Map<String, Object> latest = latestSave(saves);
                if (!operationBusy(button.data("operation", ""))) {
                    button.removeAttribute("disabled");
                }
                button.setAttribute("data-slot", safeString(latest.get("slot")));
            }
        });
    }

    private void setSaveCountVisible(boolean visible) {
        document().getElementById("save-count").ifPresent(element -> {
            element.setAttribute("style", visible ? "display: block;" : "display: none;");
        });
        panel.invalidateLayout();
        panel.repaintHost();
    }

    public void showStatus(String message) {
        setText("load-status", message);
        setText("pause-load-status", message);
        setText("main-menu-status", message);
        setText("settings-status", message);
    }

    private void setText(String elementId, String value) {
        document().getElementById(elementId).ifPresent(element -> {
            element.clearChildren();
            element.appendChild(document().createText(value == null ? "" : value));
        });
        panel.invalidateLayout();
        panel.repaintHost();
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void applyViewportSize(int width, int height) {
        if (viewportWidth == width && viewportHeight == height) {
            return;
        }
        viewportWidth = width;
        viewportHeight = height;
        String sizeStyle = viewportSizeStyle();
        document().getElementById("fl-root")
                .ifPresent(element -> element.setAttribute("style", sizeStyle));
        document().getElementsByTagName("body").stream().findFirst()
                .ifPresent(element -> element.setAttribute("style", sizeStyle));
        document().getElementById("ui-root")
                .ifPresent(element -> element.setAttribute("style", sizeStyle));
        setScreenNodeVisible("screen-hud", "hud".equals(screen()));
        setScreenNodeVisible("screen-pause", "pause".equals(screen()));
        setScreenNodeVisible("screen-main-menu", "main-menu".equals(screen()));
        setScreenNodeVisible("screen-settings", "settings".equals(screen()));
        document().getElementById("screen-overlay")
                .ifPresent(element -> element.setAttribute("style", sizeStyle));
        panel.invalidateLayout();
    }

    private String viewportSizeStyle() {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return "";
        }
        return "width: " + viewportWidth + "px; height: " + viewportHeight + "px;";
    }

    private String screenNodeStyle(boolean visible) {
        String display = visible ? "display: block;" : "display: none;";
        String size = viewportSizeStyle();
        return size.isBlank() ? display : display + " " + size;
    }

    public BufferedImage renderToImage(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        applyViewportSize(safeWidth, safeHeight);
        panel.setSize(safeWidth, safeHeight);
        panel.doLayout();
        panel.ensureLayout();

        BufferedImage image = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, safeWidth, safeHeight);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }



    public void setScreen(String screen) {
        String safeScreen = screen == null || screen.isBlank() ? "hud" : screen;
        String currentScreen = screen();
        if ("settings".equals(safeScreen)
                && ("main-menu".equals(currentScreen)
                || "pause".equals(currentScreen))) {
            settingsReturnScreen = currentScreen;
        }
        document().getElementById("fl-root").ifPresent(root -> {
            root.setAttribute("data-screen", safeScreen);
            root.setAttribute("data-mode", modeForScreen(safeScreen));
        });
        setScreenNodeVisible("screen-hud", "hud".equals(safeScreen));
        setScreenNodeVisible("screen-pause", "pause".equals(safeScreen));
        setScreenNodeVisible("screen-main-menu", "main-menu".equals(safeScreen));
        setScreenNodeVisible("screen-settings", "settings".equals(safeScreen));
        if ("settings".equals(safeScreen)) {
            setSettingsSection(settingsSection);
        }
        applyScreenChrome(safeScreen);
        panel.invalidateLayout();
        panel.repaintHost();
    }

    private void applyScreenChrome(String screen) {
        boolean settings = "settings".equals(screen);
        boolean settingsFromPause = settings
                && "pause".equals(settingsReturnScreen);
        setElementVisibleClass("main-menu-background", "main-menu".equals(screen));
        setElementVisibleClass(
                "screen-overlay",
                "pause".equals(screen) || settingsFromPause
        );
        document().getElementById("screen-settings").ifPresent(element ->
                element.setAttribute(
                        "class",
                        (settingsFromPause
                                ? "screen settings settings-from-pause"
                                : "screen settings settings-from-main")
                                + (settings ? " settings-open" : "")
                )
        );
        document().getElementById("settings-card").ifPresent(element ->
                element.setAttribute(
                        "class",
                        "menu-card settings-card"
                                + (settings ? " settings-open" : "")
                )
        );
    }

    private void setElementVisibleClass(String elementId, boolean visible) {
        document().getElementById(elementId).ifPresent(element -> {
            String baseClass = elementId == null ? "" : elementId;
            element.setAttribute("class", visible ? baseClass + " visible" : baseClass);
        });
    }

    private void setScreenNodeVisible(String elementId, boolean visible) {
        document().getElementById(elementId).ifPresent(node -> {
            node.setAttribute("data-visible", visible ? "true" : "false");
            node.setAttribute(
                    "style",
                    "screen-settings".equals(elementId)
                            ? settingsScreenNodeStyle()
                            : screenNodeStyle(visible)
            );
        });
    }

    private String settingsScreenNodeStyle() {
        String size = viewportSizeStyle();
        String layout = "display: flex; align-items: center; "
                + "justify-content: center;";
        return size.isBlank() ? layout : layout + " " + size;
    }

    private String modeForScreen(String screen) {
        if ("main-menu".equals(screen)) {
            return "boot";
        }
        if ("pause".equals(screen)) {
            return "paused";
        }
        if ("settings".equals(screen)) {
            return "pause".equals(settingsReturnScreen)
                    ? "paused"
                    : "boot";
        }
        return "gameplay";
    }

    private boolean isMenuScreen(String screen) {
        return "main-menu".equals(screen)
                || "pause".equals(screen)
                || "settings".equals(screen);
    }

    public String screen() {
        return document().getElementById("fl-root").map(root -> root.data("screen", "hud")).orElse("hud");
    }

    public void close() {
        for (AutoCloseable subscription : List.copyOf(eventSubscriptions)) {
            try {
                subscription.close();
            } catch (Exception ignored) {
            }
        }
        eventSubscriptions.clear();
        synchronized (busyOperations) {
            busyOperations.clear();
        }
    }

    public void openDevTools() {
        panel.openDevTools();
    }

    public UiDomDocument document() {
        return panel.document();
    }

    public long documentVersion() {
        return panel.document().version();
    }

    public HtmlDomSwingPanel panel() {
        return panel;
    }

    public Path uiRoot() {
        return uiRoot;
    }

    public FrozenLandsHtmlUiManifest manifest() {
        return manifest;
    }

    public String html() {
        return html;
    }

    public String css() {
        return css;
    }

    public List<String> loadedScripts() {
        return loadedScripts;
    }

    public Optional<String> bindingPath() {
        return Optional.ofNullable(manifest.getBindings()).filter(value -> !value.isBlank());
    }
}
