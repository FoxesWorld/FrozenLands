package org.takesome.frozenlands.engine;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.FrozenLands;
import org.takesome.frozenlands.engine.core.CoreModule;
import org.takesome.frozenlands.engine.core.console.ConsoleCursorPolicyState;
import org.takesome.frozenlands.engine.core.console.ConsoleInteractionPolicyState;
import org.takesome.frozenlands.engine.core.console.CoreConsoleState;
import org.takesome.frozenlands.engine.lua.RuntimeManifestReporter;
import org.takesome.frozenlands.engine.modules.ModuleRegistry;
import org.takesome.frozenlands.engine.providers.ProviderRegistry;
import org.takesome.frozenlands.engine.runtime.EngineRuntimeInstaller;
import org.takesome.frozenlands.engine.services.EngineServicePool;
import org.takesome.frozenlands.engine.tasks.EngineTaskPool;
import org.takesome.frozenlands.engine.ui.html.HtmlUiState;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public class Kernel extends BaseAppState implements EngineContext {
    private final FrozenLands frozenLands;
    private final Logger logger;
    private final Map CONFIG;
    private final ProviderRegistry providerRegistry;
    private final AssetLibrary assetLibrary;
    private final ModuleRegistry moduleRegistry;
    private final CoreModule coreModule;
    private final EngineTaskPool taskPool;
    private final EngineServicePool servicePool;
    private final List<EngineRuntimeInstaller> runtimeInstallers;

    private StartupPhase startupPhase = StartupPhase.RUNTIME_MODULES;
    private int runtimeInstallerIndex;
    private boolean runtimeInstallerAnnounced;
    private boolean startupComplete;

    public Kernel(FrozenLands frozenLands) {
        this.frozenLands = frozenLands;
        this.CONFIG = frozenLands.getCONFIG();
        this.logger = frozenLands.getLogger();
        this.providerRegistry = new ProviderRegistry();
        this.assetLibrary = new AssetLibrary(frozenLands.getAssetManager(), logger);
        this.moduleRegistry = new ModuleRegistry();
        this.servicePool = new EngineServicePool(logger);
        this.taskPool = new EngineTaskPool(this);
        this.coreModule = new CoreModule(this);

        frozenLands.reportStartupProgress("Preparing module registry", 0.54f);
        registerService("core.providers", ProviderRegistry.class, providerRegistry);
        registerService("core.modules", ModuleRegistry.class, moduleRegistry);
        registerService("core.services", EngineServicePool.class, servicePool);
        registerService("core.tasks", EngineTaskPool.class, taskPool);
        moduleRegistry.register(coreModule, this);
        runtimeInstallers = discoverRuntimeInstallers();
    }

    public boolean continueStartup() {
        if (startupComplete) {
            return true;
        }

        switch (startupPhase) {
            case RUNTIME_MODULES:
                installNextRuntimeModuleStep();
                break;
            case CORE_STATES:
                frozenLands.reportStartupProgress("Attaching core states", 0.88f);
                attachCoreAppStates();
                startupPhase = StartupPhase.AUTORUN;
                break;
            case AUTORUN:
                frozenLands.reportStartupProgress("Running startup scripts", 0.91f);
                runCoreAutoRunScripts();
                startupPhase = StartupPhase.MANIFEST;
                break;
            case MANIFEST:
                frozenLands.reportStartupProgress("Reporting runtime manifest", 0.93f);
                RuntimeManifestReporter.reportIfRequested(this);
                if (Boolean.getBoolean("frozenlands.runtimeManifestExit")) {
                    frozenLands.stop();
                }
                startupPhase = StartupPhase.COMPLETE;
                startupComplete = true;
                frozenLands.reportStartupProgress("Runtime ready", 0.95f);
                break;
            case COMPLETE:
            default:
                startupComplete = true;
                break;
        }
        return startupComplete;
    }

    private List<EngineRuntimeInstaller> discoverRuntimeInstallers() {
        return ServiceLoader.load(EngineRuntimeInstaller.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator
                        .comparingInt(EngineRuntimeInstaller::priority)
                        .thenComparing(EngineRuntimeInstaller::id)
                        .thenComparing(installer -> installer.getClass().getName()))
                .toList();
    }

    private void installNextRuntimeModuleStep() {
        if (runtimeInstallers.isEmpty()) {
            frozenLands.reportStartupProgress("No runtime modules discovered", 0.86f);
            startupPhase = StartupPhase.CORE_STATES;
            return;
        }
        if (runtimeInstallerIndex >= runtimeInstallers.size()) {
            frozenLands.reportStartupProgress("Runtime modules ready", 0.86f);
            startupPhase = StartupPhase.CORE_STATES;
            return;
        }

        EngineRuntimeInstaller installer = runtimeInstallers.get(runtimeInstallerIndex);
        float baseProgress = 0.58f;
        float moduleProgressSpan = 0.28f;
        float before = baseProgress + moduleProgressSpan * runtimeInstallerIndex / runtimeInstallers.size();
        float after = baseProgress + moduleProgressSpan * (runtimeInstallerIndex + 1) / runtimeInstallers.size();

        if (!runtimeInstallerAnnounced) {
            frozenLands.reportStartupProgress("Loading module " + installer.id(), before);
            runtimeInstallerAnnounced = true;
            return;
        }

        logger.info("Installing FrozenLands runtime module: {} [{}]", installer.id(), installer.getClass().getName());
        installer.install(this);
        frozenLands.reportStartupProgress("Loaded module " + installer.id(), after);
        runtimeInstallerIndex++;
        runtimeInstallerAnnounced = false;
    }

    private void attachCoreAppStates() {
        appStateManager().attach(new ConsoleInteractionPolicyState(this));
        appStateManager().attach(new ConsoleCursorPolicyState(this));
        appStateManager().attach(new CoreConsoleState(this));
        appStateManager().attach(new HtmlUiState(this));
    }

    private void runCoreAutoRunScripts() {
        List<Map<String, Object>> results = coreModule.runConfiguredAutoRunScripts();
        for (Map<String, Object> result : results) {
            if (Boolean.TRUE.equals(result.get("ok"))) {
                logger.info("Lua autorun executed: {}", result.get("script"));
            } else {
                logger.error("Lua autorun failed: {} {}", result.get("script"), result.get("message"));
            }
        }
    }

    @Override
    protected void initialize(Application application) {
    }

    @Override
    protected void cleanup(Application application) {
        taskPool.close();
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void update(float tpf) {
    }

    @Override
    public Map getConfig() {
        return CONFIG;
    }

    @Override
    public AssetManager getAssetManager() {
        return frozenLands.getAssetManager();
    }

    @Override
    public AssetLibrary assets() {
        return assetLibrary;
    }

    @Override
    public AppStateManager appStateManager() {
        return frozenLands.getStateManager();
    }

    @Override
    public InputManager getInputManager() {
        return frozenLands.getInputManager();
    }

    @Override
    public Camera getCamera() {
        return frozenLands.getCamera();
    }

    @Override
    public BulletAppState getBulletAppState() {
        return frozenLands.getBulletAppState();
    }

    @Override
    public Node getRootNode() {
        return frozenLands.getRootNode();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public ViewPort getViewPort() {
        return frozenLands.getViewPort();
    }

    @Override
    public Node getGuiNode() {
        return frozenLands.getGuiNode();
    }

    @Override
    public FilterPostProcessor getFpp() {
        return frozenLands.getFpp();
    }

    @Override
    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public EngineTaskPool getTaskPool() {
        return taskPool;
    }

    @Override
    public EngineServicePool getServicePool() {
        return servicePool;
    }

    @Override
    public <T> void registerService(Class<T> serviceType, T service) {
        servicePool.register(serviceType, service);
    }

    @Override
    public <T> void registerService(String serviceId, Class<T> serviceType, T service) {
        servicePool.register(serviceId, serviceType, service);
    }

    public <T> void registerService(String serviceId, Class<T> serviceType, T service, String source) {
        servicePool.register(serviceId, serviceType, service, source);
    }

    @Override
    public <T> Optional<T> findService(Class<T> serviceType) {
        return servicePool.find(serviceType);
    }

    @Override
    public <T> Optional<T> findService(String serviceId, Class<T> serviceType) {
        return servicePool.find(serviceId, serviceType);
    }

    private enum StartupPhase {
        RUNTIME_MODULES,
        CORE_STATES,
        AUTORUN,
        MANIFEST,
        COMPLETE
    }
}
