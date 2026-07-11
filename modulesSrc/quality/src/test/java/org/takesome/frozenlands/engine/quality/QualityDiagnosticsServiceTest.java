package org.takesome.frozenlands.engine.quality;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityDiagnosticsServiceTest {
    @Test
    void buildsDashboardFromCoreRuntimeServices() {
        TestContext context = new TestContext();
        try {
            GraphicsQualityCatalog catalog = new GraphicsQualityCatalog();
            GraphicsQualityService quality = new GraphicsQualityService(
                    catalog,
                    (topic, payload) -> { }
            );
            QualityFrameSamplerState sampler = new QualityFrameSamplerState(null);
            sampler.update(0.016f);
            QualityDiagnosticsService diagnostics = new QualityDiagnosticsService(
                    context,
                    quality,
                    sampler
            );

            Map<String, Object> status = diagnostics.status();

            assertEquals("HIGH", status.get("preset"));
            assertNotNull(status.get("frame"));
            assertNotNull(status.get("render"));
            assertNotNull(status.get("physics"));
            assertNotNull(status.get("world"));
            assertNotNull(status.get("assets"));
            Map<?, ?> tasks = (Map<?, ?>) status.get("tasks");
            assertEquals(0, tasks.get("pending"));
            Map<?, ?> events = (Map<?, ?>) status.get("events");
            assertEquals("synchronous", events.get("deliveryMode"));
            assertEquals(0, events.get("queueDepth"));
            assertTrue((Boolean) status.get("ok"));
        } finally {
            context.tasks.close();
        }
    }

    private static final class TestContext implements EngineContext {
        private final Logger logger = LoggerFactory.getLogger(TestContext.class);
        private final AssetManager assetManager = new DesktopAssetManager(false);
        private final AssetLibrary assets = new AssetLibrary(assetManager, logger);
        private final ProviderRegistry providers = new ProviderRegistry();
        private final ModuleRegistry modules = new ModuleRegistry();
        private final EngineServicePool services = new EngineServicePool(logger);
        private final EngineTaskPool tasks = new EngineTaskPool(this);
        private final EngineEventBus events = new EngineEventBus("quality-test", 128);
        private final Node root = new Node("root");
        private final Node gui = new Node("gui");

        @Override public Node getRootNode() { return root; }
        @Override public Node getGuiNode() { return gui; }
        @Override public ViewPort getViewPort() { return null; }
        @Override public FilterPostProcessor getFpp() { return null; }
        @Override public AssetManager getAssetManager() { return assetManager; }
        @Override public AssetLibrary assets() { return assets; }
        @Override public Map getConfig() { return Map.of(); }
        @Override public AppStateManager appStateManager() { return null; }
        @Override public InputManager getInputManager() { return null; }
        @Override public Camera getCamera() { return null; }
        @Override public Logger getLogger() { return logger; }
        @Override public EngineEventBus getEventBus() { return events; }
        @Override public BulletAppState getBulletAppState() { return null; }
        @Override public ProviderRegistry getProviderRegistry() { return providers; }
        @Override public ModuleRegistry getModuleRegistry() { return modules; }
        @Override public EngineTaskPool getTaskPool() { return tasks; }
        @Override public EngineServicePool getServicePool() { return services; }

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
}
