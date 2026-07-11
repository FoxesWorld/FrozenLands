package org.takesome.frozenlands.engine.weather;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.player.Player;
import org.takesome.frozenlands.engine.weather.snow.SnowPrecipitationSystem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JME weather-effects adapter.
 *
 * <p>This state no longer owns weather semantics. It consumes the canonical
 * {@link WeatherState} published by {@link WeatherController} and translates it
 * into snow particles, wind and scene attachment.</p>
 */
public final class WeatherService extends BaseAppState {
    private final EngineContext context;
    private final WeatherController controller;
    private final WeatherRuntimeConfig runtimeConfig;
    private final Node weatherRoot = new Node("weather.root");
    private final Vector3f anchor = new Vector3f();
    private final AutoCloseable stateSubscription;

    private volatile WeatherState currentState;
    private SnowPrecipitationSystem snow;
    private long appliedRevision = -1L;
    private boolean initialized;

    public WeatherService(
            EngineContext context,
            WeatherController controller,
            WeatherRuntimeConfig runtimeConfig
    ) {
        this.context = context;
        this.controller = controller;
        this.runtimeConfig = runtimeConfig == null
                ? WeatherRuntimeConfig.defaultConfig()
                : runtimeConfig;
        this.currentState = controller.snapshot();
        this.stateSubscription = context.getModuleRegistry().getEventBus().subscribe(
                WeatherEvents.WEATHER_STATE_CHANGED,
                this::onWeatherStateChanged,
                false
        );
    }

    @Override
    protected void initialize(Application app) {
        if (weatherRoot.getParent() == null) {
            context.getRootNode().attachChild(weatherRoot);
        }
        snow = new SnowPrecipitationSystem(context.assets(), runtimeConfig.snowConfig());
        weatherRoot.attachChild(snow.root());
        initialized = true;
        applyState(currentState, "effects-initialized");
    }

    @Override
    public void update(float tpf) {
        if (snow == null) {
            return;
        }
        resolveAnchor();
        snow.update(tpf, anchor, context.getCamera());
    }

    public WeatherEnvironmentState snapshot() {
        return WeatherEnvironmentState.from(
                initialized,
                runtimeConfig,
                currentState,
                snow,
                anchor
        );
    }

    public Map<String, Object> status() {
        return snapshot().toMap();
    }

    private void resolveAnchor() {
        Player player = context.findService(Player.class).orElse(null);
        if (player != null) {
            anchor.set(player.getWorldTranslation());
            return;
        }
        if (context.getCamera() != null) {
            anchor.set(context.getCamera().getLocation());
        }
    }

    private void onWeatherStateChanged(Map<String, Object> event) {
        Map<String, Object> payload = WeatherPayloads.payload(event);
        WeatherState state = controller.snapshot();
        currentState = state;
        if (snow != null) {
            applyState(state, WeatherPayloads.string(payload, "reason", "weather-state-changed"));
        }
    }

    private void applyState(WeatherState state, String reason) {
        if (snow == null || state == null || appliedRevision == state.revision()) {
            return;
        }
        snow.setWind(state.wind());
        snow.setIntensity(state.intensity().value());
        if (state.precipitationActive() && state.intensity().active()) {
            snow.start();
        } else {
            snow.stop();
        }

        appliedRevision = state.revision();
        Map<String, Object> payload = new LinkedHashMap<>(snapshot().toMap());
        payload.put("reason", reason);
        payload.put("state", state.toMap());
        context.getModuleRegistry().publishEvent(
                WeatherEvents.WEATHER_EFFECTS_APPLIED,
                payload,
                WeatherModule.ID,
                Map.of("adapter", "snow")
        );
    }

    @Override
    protected void cleanup(Application app) {
        close(stateSubscription);
        weatherRoot.removeFromParent();
        snow = null;
        initialized = false;
    }

    @Override
    protected void onEnable() {
        weatherRoot.setCullHint(Spatial.CullHint.Inherit);
    }

    @Override
    protected void onDisable() {
        weatherRoot.setCullHint(Spatial.CullHint.Always);
    }

    private void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
