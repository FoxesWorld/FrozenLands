package org.takesome.frozenlands.engine;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.events.EngineEventBus;
import org.takesome.frozenlands.engine.modules.ModuleRegistry;
import org.takesome.frozenlands.engine.providers.ProviderRegistry;
import org.takesome.frozenlands.engine.services.EngineServicePool;
import org.takesome.frozenlands.engine.tasks.EngineTaskPool;

import java.util.Map;
import java.util.Optional;

public interface EngineContext {
    Node getRootNode();
    Node getGuiNode();
    ViewPort getViewPort();
    FilterPostProcessor getFpp();
    AssetManager getAssetManager();
    AssetLibrary assets();
    Map getConfig();
    AppStateManager appStateManager();
    InputManager getInputManager();
    Camera getCamera();
    Logger getLogger();
    EngineEventBus getEventBus();
    BulletAppState getBulletAppState();
    ProviderRegistry getProviderRegistry();
    ModuleRegistry getModuleRegistry();
    EngineTaskPool getTaskPool();
    EngineServicePool getServicePool();

    <T> void registerService(Class<T> serviceType, T service);

    <T> void registerService(String serviceId, Class<T> serviceType, T service);

    <T> Optional<T> findService(Class<T> serviceType);

    <T> Optional<T> findService(String serviceId, Class<T> serviceType);

    default <T> T requireService(Class<T> serviceType) {
        return findService(serviceType).orElseThrow(() -> new IllegalStateException(
                "Required engine runtime service is not registered: " + serviceType.getName()
        ));
    }

    default <T> T serviceOrNull(Class<T> serviceType) {
        return findService(serviceType).orElse(null);
    }

    default Map<String, Object> servicePoolStatus() {
        return getServicePool().status();
    }
}
