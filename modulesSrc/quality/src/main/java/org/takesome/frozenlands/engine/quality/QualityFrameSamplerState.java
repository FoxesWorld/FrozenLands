package org.takesome.frozenlands.engine.quality;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Statistics;
import org.takesome.frozenlands.engine.EngineContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Low-overhead frame, render and physics sampler for the quality dashboard. */
public final class QualityFrameSamplerState extends BaseAppState
        implements PhysicsTickListener {
    private static final double SMOOTHING = 0.1;

    private final EngineContext context;
    private volatile double frameMilliseconds;
    private volatile double renderMilliseconds;
    private volatile double physicsMilliseconds;
    private volatile long sampledFrames;
    private volatile long renderStartedNanos;
    private volatile long physicsStartedNanos;
    private Statistics statistics;
    private PhysicsSpace physicsSpace;

    public QualityFrameSamplerState(EngineContext context) {
        this.context = context;
    }

    @Override
    protected void initialize(Application application) {
        statistics = application.getRenderer().getStatistics();
        statistics.setEnabled(true);
        if (context.getBulletAppState() != null) {
            physicsSpace = context.getBulletAppState().getPhysicsSpace();
            if (physicsSpace != null) {
                physicsSpace.addTickListener(this);
            }
        }
    }

    @Override
    protected void cleanup(Application application) {
        if (physicsSpace != null) {
            physicsSpace.removeTickListener(this);
        }
        physicsSpace = null;
        statistics = null;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void update(float tpf) {
        if (Float.isFinite(tpf) && tpf > 0f && tpf < 10f) {
            frameMilliseconds = smooth(frameMilliseconds, tpf * 1_000.0);
            sampledFrames++;
        }
    }

    @Override
    public void render(RenderManager renderManager) {
        renderStartedNanos = System.nanoTime();
    }

    @Override
    public void postRender() {
        long started = renderStartedNanos;
        if (started > 0L) {
            renderMilliseconds = smooth(
                    renderMilliseconds,
                    (System.nanoTime() - started) / 1_000_000.0
            );
        }
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        physicsStartedNanos = System.nanoTime();
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        long started = physicsStartedNanos;
        if (started > 0L) {
            physicsMilliseconds = smooth(
                    physicsMilliseconds,
                    (System.nanoTime() - started) / 1_000_000.0
            );
        }
    }

    public Map<String, Object> frameStatus() {
        double milliseconds = frameMilliseconds;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fps", milliseconds > 0.0 ? 1_000.0 / milliseconds : 0.0);
        result.put("milliseconds", milliseconds);
        result.put("sampledFrames", sampledFrames);
        return Map.copyOf(result);
    }

    public Map<String, Object> renderStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("milliseconds", renderMilliseconds);
        result.put("statistics", rendererStatistics());
        return Map.copyOf(result);
    }

    public Map<String, Object> physicsStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("milliseconds", physicsMilliseconds);
        result.put("available", physicsSpace != null);
        if (physicsSpace != null) {
            result.put("rigidBodies", physicsSpace.countRigidBodies());
            result.put("characters", physicsSpace.getCharacterList().size());
            result.put("vehicles", physicsSpace.getVehicleList().size());
            result.put("joints", physicsSpace.countJoints());
            result.put("manifolds", physicsSpace.countManifolds());
        }
        return Map.copyOf(result);
    }

    private Map<String, Integer> rendererStatistics() {
        Statistics current = statistics;
        if (current == null) {
            return Map.of();
        }
        String[] labels = current.getLabels();
        int[] data = new int[labels.length];
        current.getData(data);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < labels.length; index++) {
            result.put(labels[index], data[index]);
        }
        return Map.copyOf(result);
    }

    private double smooth(double previous, double sample) {
        return previous <= 0.0
                ? sample
                : previous + SMOOTHING * (sample - previous);
    }
}
