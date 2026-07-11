package org.takesome.frozenlands.engine.weather;

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
import org.takesome.frozenlands.engine.modules.ModuleRegistry;
import org.takesome.frozenlands.engine.providers.ProviderRegistry;
import org.takesome.frozenlands.engine.services.EngineServicePool;
import org.takesome.frozenlands.engine.tasks.EngineTaskPool;

import java.util.Map;
import java.util.Optional;

final class WeatherTestContext implements EngineContext {
    private final Logger logger = LoggerFactory.getLogger(WeatherTestContext.class);
    private final EngineEventBus engineEvents = new EngineEventBus("weather-test-engine", 256);
    private final ModuleRegistry modules = new ModuleRegistry(new EngineEventBus("weather-test-module", 512));
    private final ProviderRegistry providers = new ProviderRegistry(new EngineEventBus("weather-test-provider", 128));
    private final EngineServicePool services = new EngineServicePool(logger);
    private final Node root = new Node("weather-test-root");
    private final Node gui = new Node("weather-test-gui");

    ModuleRegistry modules() {
        return modules;
    }

    @Override public Node getRootNode() { return root; }
    @Override public Node getGuiNode() { return gui; }
    @Override public ViewPort getViewPort() { return null; }
    @Override public FilterPostProcessor getFpp() { return null; }
    @Override public AssetManager getAssetManager() { return null; }
    @Override public AssetLibrary assets() { return null; }
    @Override public Map getConfig() { return Map.of(); }
    @Override public AppStateManager appStateManager() { return null; }
    @Override public InputManager getInputManager() { return null; }
    @Override public Camera getCamera() { return null; }
    @Override public Logger getLogger() { return logger; }
    @Override public BulletAppState getBulletAppState() { return null; }
    @Override public ProviderRegistry getProviderRegistry() { return providers; }
    @Override public ModuleRegistry getModuleRegistry() { return modules; }
    @Override public EngineTaskPool getTaskPool() { return null; }
    @Override public EngineServicePool getServicePool() { return services; }
    @Override public <T> void registerService(Class<T> type, T service) { services.register(type, service); }
    @Override public <T> void registerService(String id, Class<T> type, T service) { services.register(id, type, service); }
    @Override public <T> Optional<T> findService(Class<T> type) { return services.find(type); }
    @Override public <T> Optional<T> findService(String id, Class<T> type) { return services.find(id, type); }
}
