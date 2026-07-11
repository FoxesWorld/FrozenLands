package org.takesome.frozenlands.engine.weather.snow;

import org.takesome.frozenlands.engine.assets.AssetLibrary;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SnowPrecipitationSystem {
    private final AssetLibrary assets;
    private final Node root = new Node("weather.snow.root");
    private final List<Snowflake> flakes = new ArrayList<>();
    private final Random random = new Random();
    private final Vector3f anchor = new Vector3f();
    private final Vector3f wind = new Vector3f();
    private final Vector3f left = new Vector3f();
    private final Vector3f up = new Vector3f();

    private Config config = Config.defaultConfig();
    private Geometry geometry;
    private Mesh mesh;
    private FloatBuffer positionBuffer;
    private FloatBuffer colorBuffer;
    private FloatBuffer texCoordBuffer;
    private IntBuffer indexBuffer;
    private boolean running;
    private float intensity = 1f;
    private int activeFlakeCount;

    public SnowPrecipitationSystem(AssetLibrary assets) {
        this.assets = assets;
    }

    public SnowPrecipitationSystem(AssetLibrary assets, Config initialConfig) {
        this.assets = assets;
        setConfig(initialConfig);
    }

    public Node root() {
        return root;
    }

    public boolean running() {
        return running;
    }

    public Config config() {
        return config;
    }

    public Vector3f wind() {
        return wind.clone();
    }

    public int flakeCount() {
        return activeFlakeCount;
    }

    public int allocatedFlakeCount() {
        return flakes.size();
    }

    public float intensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        int previousActive = activeFlakeCount;
        this.intensity = clampIntensity(intensity);
        updateActiveFlakeCount();
        for (int index = previousActive; index < activeFlakeCount; index++) {
            flakes.get(index).reset(random, anchor, config);
        }
        root.setCullHint(running && activeFlakeCount > 0
                ? Spatial.CullHint.Inherit
                : Spatial.CullHint.Always);
    }

    public void start() {
        running = true;
        root.setCullHint(activeFlakeCount > 0 ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        for (int index = 0; index < activeFlakeCount; index++) {
            flakes.get(index).reset(random, anchor, config);
        }
    }

    public void stop() {
        running = false;
        root.setCullHint(Spatial.CullHint.Always);
    }

    public void setConfig(Config config) {
        boolean wasRunning = running;
        Config next = config == null ? Config.defaultConfig() : config;
        if ((next.texture() == null || next.texture().isBlank()) && this.config.texture() != null && !this.config.texture().isBlank()) {
            next = next.withTexture(this.config.texture());
        }
        this.config = next;
        allocateFlakes(this.config.maxFlakes());
        if (wasRunning) {
            start();
        }
    }

    public void setWind(Vector3f wind) {
        this.wind.set(wind == null ? Vector3f.ZERO : wind);
    }

    public void update(float tpf, Vector3f anchorPosition, Camera camera) {
        if (!running || activeFlakeCount <= 0 || anchorPosition == null || camera == null) {
            return;
        }

        anchor.set(anchorPosition);
        float killY = anchor.y + config.killBelowHeight();
        for (int index = 0; index < activeFlakeCount; index++) {
            Snowflake flake = flakes.get(index);
            if (!flake.update(tpf, wind, killY)) {
                flake.reset(random, anchor, config);
            }
        }
        updateMesh(camera);
    }

    private void allocateFlakes(int count) {
        int safeCount = Math.max(0, count);
        while (flakes.size() < safeCount) {
            Snowflake flake = new Snowflake();
            flake.reset(random, anchor, config);
            flakes.add(flake);
        }
        while (flakes.size() > safeCount) {
            flakes.remove(flakes.size() - 1);
        }
        updateActiveFlakeCount();
        rebuildMesh();
    }

    private void rebuildMesh() {
        root.detachAllChildren();

        int flakeCount = flakes.size();
        mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Triangles);

        positionBuffer = BufferUtils.createFloatBuffer(flakeCount * 4 * 3);
        colorBuffer = BufferUtils.createFloatBuffer(flakeCount * 4 * 4);
        texCoordBuffer = BufferUtils.createFloatBuffer(flakeCount * 4 * 2);
        indexBuffer = BufferUtils.createIntBuffer(flakeCount * 6);

        for (int i = 0; i < flakeCount; i++) {
            int vertexBase = i * 4;
            texCoordBuffer.put(0f).put(0f);
            texCoordBuffer.put(1f).put(0f);
            texCoordBuffer.put(1f).put(1f);
            texCoordBuffer.put(0f).put(1f);

            indexBuffer.put(vertexBase).put(vertexBase + 1).put(vertexBase + 2);
            indexBuffer.put(vertexBase).put(vertexBase + 2).put(vertexBase + 3);
        }
        texCoordBuffer.flip();
        indexBuffer.flip();

        // Initialize dynamic buffers with zeroed content.
        positionBuffer.limit(positionBuffer.capacity());
        colorBuffer.limit(colorBuffer.capacity());
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positionBuffer);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoordBuffer);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colorBuffer);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuffer);
        mesh.getBuffer(VertexBuffer.Type.Position).setUsage(VertexBuffer.Usage.Dynamic);
        mesh.getBuffer(VertexBuffer.Type.Color).setUsage(VertexBuffer.Usage.Dynamic);
        mesh.updateBound();

        geometry = new Geometry("weather.snow.geometry", mesh);
        geometry.setMaterial(SnowMaterialFactory.create(assets, config.texture()));
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(geometry);
        root.setCullHint(running ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    private void updateMesh(Camera camera) {
        if (mesh == null || positionBuffer == null || colorBuffer == null) {
            return;
        }

        left.set(camera.getLeft()).normalizeLocal();
        up.set(camera.getUp()).normalizeLocal();

        positionBuffer.clear();
        colorBuffer.clear();

        for (int index = 0; index < flakes.size(); index++) {
            if (index < activeFlakeCount) {
                writeFlake(flakes.get(index));
            } else {
                writeHiddenFlake();
            }
        }

        positionBuffer.flip();
        colorBuffer.flip();
        mesh.getBuffer(VertexBuffer.Type.Position).updateData(positionBuffer);
        mesh.getBuffer(VertexBuffer.Type.Color).updateData(colorBuffer);
        mesh.updateBound();
    }

    private void writeFlake(Snowflake flake) {
        float half = flake.size * 0.5f;
        float cos = FastMath.cos(flake.rotation);
        float sin = FastMath.sin(flake.rotation);

        float lx = left.x * half;
        float ly = left.y * half;
        float lz = left.z * half;
        float ux = up.x * half;
        float uy = up.y * half;
        float uz = up.z * half;

        writeVertex(flake, -lx * cos + -ux * sin, -ly * cos + -uy * sin, -lz * cos + -uz * sin);
        writeVertex(flake,  lx * cos + -ux * sin,  ly * cos + -uy * sin,  lz * cos + -uz * sin);
        writeVertex(flake,  lx * cos +  ux * sin,  ly * cos +  uy * sin,  lz * cos +  uz * sin);
        writeVertex(flake, -lx * cos +  ux * sin, -ly * cos +  uy * sin, -lz * cos +  uz * sin);
    }

    private void writeVertex(Snowflake flake, float dx, float dy, float dz) {
        positionBuffer.put(flake.position.x + dx).put(flake.position.y + dy).put(flake.position.z + dz);
        float alphaScale = 0.35f + 0.65f * intensity;
        colorBuffer.put(1f).put(1f).put(1f).put(flake.visibleAlpha() * alphaScale);
    }

    private void writeHiddenFlake() {
        for (int vertex = 0; vertex < 4; vertex++) {
            positionBuffer.put(anchor.x).put(anchor.y).put(anchor.z);
            colorBuffer.put(1f).put(1f).put(1f).put(0f);
        }
    }

    private void updateActiveFlakeCount() {
        activeFlakeCount = effectiveFlakeCount(flakes.size(), intensity);
    }

    public static int effectiveFlakeCount(int allocated, float intensity) {
        int safeAllocated = Math.max(0, allocated);
        return Math.max(0, Math.min(safeAllocated, Math.round(safeAllocated * clampIntensity(intensity))));
    }

    private static float clampIntensity(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    public record Config(
            int maxFlakes,
            float spawnWidth,
            float spawnDepth,
            float spawnMinHeight,
            float spawnMaxHeight,
            float killBelowHeight,
            float minFallSpeed,
            float maxFallSpeed,
            float drift,
            float minSize,
            float maxSize,
            float minAlpha,
            float maxAlpha,
            float minLife,
            float maxLife,
            float maxAngularVelocity,
            String texture
    ) {
        public Config withTexture(String texture) {
            return new Config(
                    maxFlakes,
                    spawnWidth,
                    spawnDepth,
                    spawnMinHeight,
                    spawnMaxHeight,
                    killBelowHeight,
                    minFallSpeed,
                    maxFallSpeed,
                    drift,
                    minSize,
                    maxSize,
                    minAlpha,
                    maxAlpha,
                    minLife,
                    maxLife,
                    maxAngularVelocity,
                    texture
            );
        }

        public static Config defaultConfig() {
            return new Config(
                    900,
                    42f,
                    42f,
                    8f,
                    30f,
                    -4f,
                    2.8f,
                    7.5f,
                    0.45f,
                    0.035f,
                    0.12f,
                    0.35f,
                    0.85f,
                    4f,
                    12f,
                    1.4f,
                    ""
            );
        }

        public static Config blizzard() {
            return new Config(
                    2200,
                    55f,
                    55f,
                    8f,
                    34f,
                    -5f,
                    5.5f,
                    12f,
                    1.5f,
                    0.035f,
                    0.16f,
                    0.45f,
                    0.95f,
                    3f,
                    9f,
                    2.5f,
                    defaultConfig().texture()
            );
        }

        public static Config light() {
            return new Config(
                    450,
                    38f,
                    38f,
                    8f,
                    28f,
                    -4f,
                    1.8f,
                    4.8f,
                    0.25f,
                    0.03f,
                    0.095f,
                    0.25f,
                    0.65f,
                    5f,
                    14f,
                    0.8f,
                    defaultConfig().texture()
            );
        }

        public static Config from(Map<String, Object> values, Config fallback) {
            Config base = fallback == null ? defaultConfig() : fallback;
            if (values == null || values.isEmpty()) {
                return base;
            }
            return new Config(
                    intValue(values, "maxFlakes", base.maxFlakes()),
                    floatValue(values, "spawnWidth", base.spawnWidth()),
                    floatValue(values, "spawnDepth", base.spawnDepth()),
                    floatValue(values, "spawnMinHeight", base.spawnMinHeight()),
                    floatValue(values, "spawnMaxHeight", base.spawnMaxHeight()),
                    floatValue(values, "killBelowHeight", base.killBelowHeight()),
                    floatValue(values, "minFallSpeed", base.minFallSpeed()),
                    floatValue(values, "maxFallSpeed", base.maxFallSpeed()),
                    floatValue(values, "drift", base.drift()),
                    floatValue(values, "minSize", base.minSize()),
                    floatValue(values, "maxSize", base.maxSize()),
                    floatValue(values, "minAlpha", base.minAlpha()),
                    floatValue(values, "maxAlpha", base.maxAlpha()),
                    floatValue(values, "minLife", base.minLife()),
                    floatValue(values, "maxLife", base.maxLife()),
                    floatValue(values, "maxAngularVelocity", base.maxAngularVelocity()),
                    stringValue(values, "texture", base.texture())
            );
        }

        private static int intValue(Map<String, Object> values, String key, int fallback) {
            Object value = values.get(key);
            return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value));
        }

        private static String stringValue(Map<String, Object> values, String key, String fallback) {
            Object value = values.get(key);
            return value == null ? fallback : String.valueOf(value);
        }

        private static float floatValue(Map<String, Object> values, String key, float fallback) {
            Object value = values.get(key);
            return value instanceof Number number ? number.floatValue() : value == null ? fallback : Float.parseFloat(String.valueOf(value));
        }
    }
}
