package org.takesome.frozenlands.engine.world.terrain.chunk;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TerrainChunkTracker {
    private final Map<String, TerrainChunkSnapshot> chunksByKey = new LinkedHashMap<>();
    private final Set<String> collisionReadyByKey = new LinkedHashSet<>();
    private final Map<String, CollisionBounds> collisionBoundsByKey = new LinkedHashMap<>();
    private int attachedCount;
    private int detachedCount;
    private int collisionInstalledCount;
    private int collisionRemovedCount;

    public synchronized void tileAttached(Vector3f cell, TerrainQuad quad) {
        attachedCount++;
        String key = key(cell);
        chunksByKey.put(key, new TerrainChunkSnapshot(key, cell, quad.getName(), true, Instant.now()));
    }

    public synchronized void tileDetached(Vector3f cell, TerrainQuad quad) {
        detachedCount++;
        String key = key(cell);
        chunksByKey.put(key, new TerrainChunkSnapshot(key, cell, quad.getName(), false, Instant.now()));
        if (collisionReadyByKey.remove(key)) {
            collisionRemovedCount++;
        }
        collisionBoundsByKey.remove(key);
    }

    public synchronized void tileCollisionInstalled(Vector3f cell, TerrainQuad quad) {
        collisionInstalledCount++;
        String key = key(cell);
        chunksByKey.put(key, new TerrainChunkSnapshot(key, cell, quad.getName(), true, Instant.now()));
        collisionReadyByKey.add(key);
        collisionBoundsByKey.put(key, CollisionBounds.from(quad));
    }

    public synchronized boolean hasCollisionReadyChunks() {
        return !collisionReadyByKey.isEmpty();
    }

    public synchronized boolean hasCollisionReadyAt(float x, float z, float inset) {
        return resolveCollisionReadyPoint(x, z, inset).isPresent();
    }

    /**
     * Resolve a point safely inside a collision-ready tile. When the requested
     * point lies on a tile seam, it is moved inward by {@code inset} so the
     * character capsule never starts over a Bullet heightfield boundary.
     */
    public synchronized Optional<Vector2f> resolveCollisionReadyPoint(float x, float z, float inset) {
        float safeInset = Math.max(0f, inset);
        for (CollisionBounds bounds : collisionBoundsByKey.values()) {
            if (bounds.contains(x, z, safeInset)) {
                return Optional.of(new Vector2f(x, z));
            }
        }

        Vector2f nearest = null;
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (CollisionBounds bounds : collisionBoundsByKey.values()) {
            if (!bounds.contains(x, z, 0f)) {
                continue;
            }
            Vector2f candidate = bounds.clampInside(x, z, safeInset);
            float dx = candidate.x - x;
            float dz = candidate.y - z;
            float distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearest = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public synchronized int getLoadedChunkCount() {
        int count = 0;
        for (TerrainChunkSnapshot snapshot : chunksByKey.values()) {
            if (snapshot.isAttached()) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<Map<String, Object>> snapshotMaps() {
        return chunksByKey.values().stream().map(TerrainChunkSnapshot::toMap).toList();
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("knownChunks", chunksByKey.size());
        status.put("loadedChunks", getLoadedChunkCount());
        status.put("attachedEvents", attachedCount);
        status.put("detachedEvents", detachedCount);
        status.put("collisionInstalledEvents", collisionInstalledCount);
        status.put("collisionRemovedEvents", collisionRemovedCount);
        status.put("collisionReady", hasCollisionReadyChunks());
        status.put("collisionReadyChunks", collisionReadyByKey.size());
        status.put("collisionBounds", collisionBoundsByKey.size());
        return status;
    }

    private String key(Vector3f cell) {
        return Math.round(cell.x) + ":" + Math.round(cell.z);
    }

    private record CollisionBounds(float minX, float maxX, float minZ, float maxZ) {
        private static CollisionBounds from(TerrainQuad quad) {
            quad.updateGeometricState();
            Vector3f center = quad.getWorldTranslation();
            Vector3f scale = quad.getWorldScale();
            float halfX = (quad.getTotalSize() - 1) * Math.abs(scale.x) * 0.5f;
            float halfZ = (quad.getTotalSize() - 1) * Math.abs(scale.z) * 0.5f;
            return new CollisionBounds(
                    center.x - halfX,
                    center.x + halfX,
                    center.z - halfZ,
                    center.z + halfZ
            );
        }

        private boolean contains(float x, float z, float inset) {
            float xInset = boundedInset(inset, maxX - minX);
            float zInset = boundedInset(inset, maxZ - minZ);
            return x >= minX + xInset && x <= maxX - xInset
                    && z >= minZ + zInset && z <= maxZ - zInset;
        }

        private Vector2f clampInside(float x, float z, float inset) {
            float xInset = boundedInset(inset, maxX - minX);
            float zInset = boundedInset(inset, maxZ - minZ);
            return new Vector2f(
                    Math.max(minX + xInset, Math.min(maxX - xInset, x)),
                    Math.max(minZ + zInset, Math.min(maxZ - zInset, z))
            );
        }

        private static float boundedInset(float requested, float extent) {
            return Math.min(Math.max(0f, requested), Math.max(0f, extent * 0.5f - 0.001f));
        }
    }
}
