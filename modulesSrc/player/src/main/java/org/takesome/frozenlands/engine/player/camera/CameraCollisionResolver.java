package org.takesome.frozenlands.engine.player.camera;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.player.PlayerOptions;

import java.util.ArrayList;
import java.util.List;

/** Keeps orbit cameras in front of world geometry. */
final class CameraCollisionResolver {
    private static final float HIT_EPSILON = 0.0001f;

    private final EngineContext context;
    private final PlayerOptions options;
    private final List<PhysicsRayTestResult> hits = new ArrayList<>();
    private final Vector3f rayDirection = new Vector3f();

    CameraCollisionResolver(EngineContext context, PlayerOptions options) {
        this.context = context;
        this.options = options;
    }

    boolean resolve(
            Vector3f target,
            Vector3f desiredLocation,
            BetterCharacterControl character,
            Vector3f store
    ) {
        store.set(desiredLocation);
        if (!options.isCameraCollisionEnabled()) {
            return false;
        }

        PhysicsSpace physicsSpace = physicsSpace();
        if (physicsSpace == null) {
            return false;
        }

        hits.clear();
        physicsSpace.rayTest(target, desiredLocation, hits);
        PhysicsCollisionObject ownBody = character == null
                ? null
                : character.getRigidBody();
        float closestFraction = 1f;
        for (PhysicsRayTestResult hit : hits) {
            PhysicsCollisionObject object = hit.getCollisionObject();
            if (object == null || object == ownBody) {
                continue;
            }
            float fraction = hit.getHitFraction();
            if (fraction > HIT_EPSILON && fraction < closestFraction) {
                closestFraction = fraction;
            }
        }
        if (closestFraction >= 1f) {
            return false;
        }

        rayDirection.set(desiredLocation).subtractLocal(target);
        float desiredDistance = rayDirection.length();
        if (desiredDistance <= HIT_EPSILON) {
            store.set(target);
            return true;
        }

        rayDirection.divideLocal(desiredDistance);
        float safeDistance = desiredDistance * closestFraction
                - options.getCameraCollisionPadding();
        safeDistance = Math.max(options.getCameraMinimumDistance(), safeDistance);
        safeDistance = Math.min(desiredDistance, safeDistance);
        store.set(target).addLocal(rayDirection.mult(safeDistance));
        return true;
    }

    private PhysicsSpace physicsSpace() {
        if (context == null || context.getBulletAppState() == null) {
            return null;
        }
        return context.getBulletAppState().getPhysicsSpace();
    }
}
