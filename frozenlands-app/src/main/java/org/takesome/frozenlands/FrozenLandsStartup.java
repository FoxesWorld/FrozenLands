package org.takesome.frozenlands;

import com.jme3.asset.plugins.FileLocator;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.app.LostFocusBehavior;
import org.takesome.frozenlands.engine.Kernel;
import org.takesome.frozenlands.engine.config.ConfigReader;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.events.EngineEventPayload;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.events.EngineStartupEvents;
import org.takesome.frozenlands.engine.events.EventSubscriptionBag;
import org.takesome.frozenlands.engine.ui.screen.UiScreen;
import org.takesome.frozenlands.engine.ui.screen.UiScreenModel;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FrozenLandsStartup {
    private static final long MIN_LOADING_SCREEN_VISIBLE_NANOS = minLoadingScreenVisibleNanos();

    private final FrozenLands application;
    private final FrozenLandsRuntimeHost runtime;
    private final Node rootNode;
    private final Node guiNode;
    private final FrozenLandsFocusController focusController;
    private final EngineEventBus eventBus = new EngineEventBus();
    private final EventSubscriptionBag startupSubscriptions = new EventSubscriptionBag();

    private BulletAppState bulletAppState;
    private FilterPostProcessor fpp;
    private Kernel kernel;
    private Map config;
    private int numSamples;
    private final Map<String, UiScreen> uiScreens = new LinkedHashMap<>();
    private UiScreen activeUiScreen;
    private int startupStage;
    private boolean startupComplete;
    private boolean startupEventHandlersInstalled;
    private long startupLoadingShownAtNanos;

    FrozenLandsStartup(
            FrozenLands application,
            FrozenLandsRuntimeHost runtime,
            Node rootNode,
            Node guiNode,
            FrozenLandsFocusController focusController
    ) {
        this.application = application;
        this.runtime = runtime;
        this.rootNode = rootNode;
        this.guiNode = guiNode;
        this.focusController = focusController;
    }

    void initializeRuntimeHost() {
        runtime.setLostFocusBehavior(LostFocusBehavior.Disabled);
        runtime.gainFocus();
        runtime.enableInput();
        initializeSceneGraph();
        installStartupEventHandlers();
        initializeStartupLoadingScreen();
        eventBus.publish(
                EngineEventTopics.STARTUP_STARTED,
                EngineStartupEvents.stage("Opening FrozenLands window", "runtime.host"),
                "frozenlands.startup"
        );
        reportStartupProgress("Opening FrozenLands window", 0.05f);
    }

    void destroyRuntimeHost() {
        startupSubscriptions.close();
        for (UiScreen screen : List.copyOf(uiScreens.values())) {
            screen.hide();
        }
        uiScreens.clear();
        activeUiScreen = null;
    }

    void updateRuntimeHost() {
        float tpf = runtime.getTimer().getTimePerFrame();
        if (!startupComplete) {
            try {
                advanceStartup();
            } catch (RuntimeException exception) {
                eventBus.publish(
                        EngineEventTopics.STARTUP_FAILED,
                        EngineStartupEvents.failed("FrozenLands startup failed", "startup", exception),
                        "frozenlands.startup"
                );
                throw exception;
            }
            renderStartupLoadingFrame();
            return;
        }
        runtime.getStateManager().update(tpf);
        rootNode.updateLogicalState(tpf);
        guiNode.updateLogicalState(tpf);
        rootNode.updateGeometricState();
        guiNode.updateGeometricState();
        RenderManager renderManager = runtime.getRenderManager();
        runtime.getStateManager().render(renderManager);
        renderManager.render(tpf, runtime.getContext().isRenderable());
        runtime.getStateManager().postRender();
    }

    void reportStartupProgress(String stage, float progress) {
        eventBus.publishLive(
                EngineEventTopics.STARTUP_PROGRESS,
                EngineStartupEvents.progress(stage, progress),
                "frozenlands.startup"
        );
    }

    EngineEventBus eventBus() {
        return eventBus;
    }

    void showUiScreen(UiScreenModel model) {
        UiScreenModel safeModel = model == null ? UiScreenModel.loading("Loading FrozenLands runtime", 0f) : model;
        UiScreen screen = uiScreen(safeModel.id());
        activeUiScreen = screen;
        screen.show(safeModel, runtime.getCamera().getWidth(), runtime.getCamera().getHeight());
        renderUiScreenFrame();
    }

    void updateUiScreen(UiScreenModel model) {
        UiScreenModel safeModel = model == null ? UiScreenModel.loading("Loading FrozenLands runtime", 0f) : model;
        UiScreen screen = activeUiScreen != null && activeUiScreen.matches(safeModel.id())
                ? activeUiScreen
                : uiScreen(safeModel.id());
        activeUiScreen = screen;
        screen.update(safeModel, runtime.getCamera().getWidth(), runtime.getCamera().getHeight());
        renderUiScreenFrame();
    }

    void hideUiScreen(String screenId) {
        String safeId = screenId == null || screenId.isBlank() ? UiScreenModel.LOADING : screenId;
        UiScreen screen = uiScreens.remove(safeId);
        if (screen == null && activeUiScreen != null && activeUiScreen.matches(safeId)) {
            screen = activeUiScreen;
        }
        if (screen != null) {
            screen.hide();
        }
        if (activeUiScreen != null && activeUiScreen.matches(safeId)) {
            activeUiScreen = null;
        }
        renderSceneFrame();
    }

    private UiScreen uiScreen(String screenId) {
        String safeId = screenId == null || screenId.isBlank() ? UiScreenModel.LOADING : screenId;
        if (!UiScreenModel.LOADING.equals(safeId)) {
            throw new IllegalArgumentException("Unsupported UI screen: " + safeId);
        }
        return uiScreens.computeIfAbsent(safeId, ignored -> new StartupLoadingScreen(runtime.getAssetManager(), guiNode));
    }

    BulletAppState bulletAppState() {
        return bulletAppState;
    }

    FilterPostProcessor fpp() {
        return fpp;
    }

    Kernel kernel() {
        return kernel;
    }

    Map config() {
        return config;
    }

    private void initializeSceneGraph() {
        runtime.getViewPort().setBackgroundColor(new ColorRGBA(0.025f, 0.035f, 0.052f, 1f));
        rootNode.setCullHint(Spatial.CullHint.Never);
        guiNode.setCullHint(Spatial.CullHint.Never);
        guiNode.setQueueBucket(RenderQueue.Bucket.Gui);
        runtime.getViewPort().attachScene(rootNode);
        runtime.getGuiViewPort().attachScene(guiNode);
    }

    private void installStartupEventHandlers() {
        if (startupEventHandlersInstalled) {
            return;
        }
        startupEventHandlersInstalled = true;

        startupSubscriptions.add(eventBus.subscribe(
                EngineEventTopics.UI_LOADING_SHOW_REQUESTED,
                event -> showUiScreen(loadingModel(event)),
                true
        ));

        startupSubscriptions.add(eventBus.subscribe(
                EngineEventTopics.STARTUP_PROGRESS,
                event -> updateUiScreen(loadingModel(event)),
                true
        ));

        startupSubscriptions.add(eventBus.subscribe(
                EngineEventTopics.UI_LOADING_UPDATE_REQUESTED,
                event -> updateUiScreen(loadingModel(event)),
                true
        ));

        startupSubscriptions.add(eventBus.subscribe(
                EngineEventTopics.UI_LOADING_HIDE_REQUESTED,
                event -> hideUiScreen(UiScreenModel.LOADING),
                false
        ));
    }

    private UiScreenModel loadingModel(Map<String, Object> event) {
        Map<String, Object> payload = EngineEventPayload.of(event);
        String stage = EngineEventPayload.string(payload, "stage", "Loading FrozenLands runtime");
        float progress = EngineEventPayload.floating(payload, "progress", 0f);
        return UiScreenModel.loading(stage, progress);
    }

    private void initializeStartupLoadingScreen() {
        startupLoadingShownAtNanos = System.nanoTime();
        eventBus.publishLive(
                EngineEventTopics.UI_LOADING_SHOW_REQUESTED,
                EngineStartupEvents.progress("Opening FrozenLands window", 0.05f),
                "frozenlands.startup"
        );
    }

    private void renderStartupLoadingFrame() {
        renderUiScreenFrame();
    }

    private void renderUiScreenFrame() {
        if (runtime.getContext() == null || !runtime.getContext().isRenderable()) {
            return;
        }
        if (activeUiScreen != null) {
            activeUiScreen.renderFrame(runtime.getCamera().getWidth(), runtime.getCamera().getHeight());
        }
        renderSceneFrame();
    }

    private void renderSceneFrame() {
        if (runtime.getContext() == null || !runtime.getContext().isRenderable()) {
            return;
        }
        rootNode.updateLogicalState(0f);
        guiNode.updateLogicalState(0f);
        rootNode.updateGeometricState();
        guiNode.updateGeometricState();
        runtime.getRenderManager().render(0f, true);
    }

    private void hideStartupLoadingScreen() {
        eventBus.publishLive(
                EngineEventTopics.UI_LOADING_HIDE_REQUESTED,
                EngineStartupEvents.progress("Entering FrozenLands", 1.0f, "startup.complete"),
                "frozenlands.startup"
        );
    }

    private void advanceStartup() {
        switch (startupStage) {
            case 0:
                reportStartupProgress("Preparing input bindings", 0.10f);
                focusController.installFocusToggleMapping();
                startupStage++;
                break;
            case 1:
                reportStartupProgress("Registering asset roots", 0.18f);
                registerMutableAssetRoots();
                startupStage++;
                break;
            case 2:
                reportStartupProgress("Reading runtime configuration", 0.26f);
                config = new ConfigReader(new String[]{"userInput"}).getCfgMaps();
                numSamples = runtime.getContext().getSettings().getSamples();
                startupStage++;
                break;
            case 3:
                reportStartupProgress("Starting physics", 0.34f);
                bulletAppState = new BulletAppState();
                startupStage++;
                break;
            case 4:
                reportStartupProgress("Preparing post-processing", 0.42f);
                fpp = new FilterPostProcessor(runtime.getAssetManager());
                if (numSamples > 0) {
                    fpp.setNumSamples(numSamples);
                }
                startupStage++;
                break;
            case 5:
                reportStartupProgress("Attaching physics state", 0.48f);
                runtime.getStateManager().attach(bulletAppState);
                runtime.getStateManager().update(0f);
                startupStage++;
                break;
            case 6:
                reportStartupProgress("Preparing engine kernel", 0.52f);
                kernel = new Kernel(application);
                startupStage++;
                break;
            case 7:
                if (kernel.continueStartup()) {
                    startupStage++;
                }
                break;
            case 8:
                reportStartupProgress("Activating engine kernel", 0.96f);
                runtime.getStateManager().attach(kernel);
                runtime.getStateManager().update(0f);
                startupStage++;
                break;
            case 9:
                reportStartupProgress("Entering FrozenLands", 1.0f);
                if (!startupLoadingMinimumElapsed()) {
                    break;
                }
                eventBus.publish(
                        EngineEventTopics.STARTUP_COMPLETED,
                        EngineStartupEvents.progress("Entering FrozenLands", 1.0f, "startup.complete"),
                        "frozenlands.startup"
                );
                hideStartupLoadingScreen();
                startupComplete = true;
                startupStage++;
                break;
            default:
                startupComplete = true;
                break;
        }
    }

    private boolean startupLoadingMinimumElapsed() {
        if (MIN_LOADING_SCREEN_VISIBLE_NANOS <= 0L || startupLoadingShownAtNanos <= 0L) {
            return true;
        }
        return System.nanoTime() - startupLoadingShownAtNanos >= MIN_LOADING_SCREEN_VISIBLE_NANOS;
    }

    private static long minLoadingScreenVisibleNanos() {
        long millis = Long.getLong("frozenlands.startup.minVisibleMillis", 1400L);
        return Math.max(0L, millis) * 1_000_000L;
    }

    private void registerMutableAssetRoots() {
        for (String assetRoot : ModuleIndexCatalog.defaultCatalog().assetRootPaths()) {
            registerMutableAssetRoot(assetRoot);
        }
    }

    private void registerMutableAssetRoot(String path) {
        File root = new File(path);
        if (root.isDirectory()) {
            runtime.getAssetManager().registerLocator(root.getPath(), FileLocator.class);
        }
    }
}
