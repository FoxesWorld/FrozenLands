package org.takesome.frozenlands.engine.testing;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.modules.ModuleRegistry;
import org.takesome.frozenlands.engine.providers.ProviderRegistry;
import org.takesome.frozenlands.engine.services.EngineServicePool;
import org.takesome.frozenlands.engine.tasks.EngineTaskPool;

import java.util.Map;
import java.util.Optional;

/** Minimal deterministic EngineContext for infrastructure tests. */
public final class TestEngineContext implements EngineContext {
    private final Logger logger = LoggerFactory.getLogger(TestEngineContext.class);
    private final EngineEventBus engineEvents = new EngineEventBus("test-engine", 512);
    private final ModuleRegistry modules = new ModuleRegistry(new EngineEventBus("test-module", 512));
    private final ProviderRegistry providers = new ProviderRegistry(new EngineEventBus("test-provider", 256));
    private final EngineServicePool services = new EngineServicePool(logger);
    private final Node rootNode = new Node("test-root");
    private final Node guiNode = new Node("test-gui");

    public ModuleRegistry modules() {
        return modules;
    }

    @Override
    public Node getRootNode() {
        return rootNode;
    }

    @Override
    public Node getGuiNode() {
        return guiNode;
    }

    @Override
    public ViewPort getViewPort() {
        return null;
    }

    @Override
    public FilterPostProcessor getFpp() {
        return null;
    }

    @Override
    public AssetManager getAssetManager() {
        return null;
    }

    @Override
    public AssetLibrary assets() {
        return null;
    }

    @Override
    public Map getConfig() {
        return Map.of();
    }

    @Override
    public AppStateManager appStateManager() {
        return null;
    }

    @Override
    public InputManager getInputManager() {
        return null;
    }

    @Override
    public Camera getCamera() {
        return null;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public EngineEventBus getEventBus() {
        return engineEvents;
    }

    @Override
    public BulletAppState getBulletAppState() {
        return null;
    }

    @Override
    public ProviderRegistry getProviderRegistry() {
        return providers;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return modules;
    }

    @Override
    public EngineTaskPool getTaskPool() {
        return null;
    }

    @Override
    public EngineServicePool getServicePool() {
        return services;
    }

    @Override
    public <T> void registerService(Class<T> serviceType, T service) {
        services.register(serviceType, service);
    }

    @Override
    public <T> void registerService(String serviceId, Class<T> serviceType, T service) {
        services.register(serviceId, serviceType, service);
    }

    @Override
    public <T> Optional<T> findService(Class<T> serviceType) {
        return services.find(serviceType);
    }

    @Override
    public <T> Optional<T> findService(String serviceId, Class<T> serviceType) {
        return services.find(serviceId, serviceType);
    }
}
