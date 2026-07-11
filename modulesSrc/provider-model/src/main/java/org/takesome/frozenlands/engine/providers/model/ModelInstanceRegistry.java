package org.takesome.frozenlands.engine.providers.model;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModelInstanceRegistry {
    private final EngineContext context;
    private final AssetLibrary assets;
    private final BulletAppState bulletAppState;
    private final Node rootNode;
    private final Map<String, Spatial> activeModels = new LinkedHashMap<>();
    private int sequence;

    ModelInstanceRegistry(EngineContext context) {
        this.context = context;
        this.assets = context.assets();
        this.bulletAppState = context.getBulletAppState();
        this.rootNode = context.getRootNode();
    }

    Spatial create(
            String reference,
            String materialReference,
            String requestedId,
            boolean attach,
            boolean collision
    ) {
        String id = requestedId == null || requestedId.isBlank()
                ? nextId()
                : requestedId.trim();
        if (activeModels.containsKey(id)) {
            throw new IllegalArgumentException("Model instance id is already active: " + id);
        }

        Spatial model = assets.model(reference);
        model.setName(id);
        if (materialReference != null && !materialReference.isBlank()) {
            model.setMaterial(assets.material(materialReference));
        }
        if (attach) {
            rootNode.attachChild(model);
        }
        if (collision) {
            attachCollision(model);
        }

        activeModels.put(id, model);
        context.getProviderRegistry().publishEvent(
                "provider.model.loaded",
                Map.of(
                        "id", id,
                        "reference", reference,
                        "source", assets.resolve(AssetLibrary.Type.MODEL, reference)
                )
        );
        return model;
    }

    boolean detach(String id) {
        Spatial model = activeModels.remove(id);
        if (model == null) {
            return false;
        }
        removeCollision(model);
        model.removeFromParent();
        context.getProviderRegistry().publishEvent(
                "provider.model.detached",
                Map.of("id", id)
        );
        return true;
    }

    Spatial get(String id) {
        return activeModels.get(id);
    }

    List<String> ids() {
        return List.copyOf(activeModels.keySet());
    }

    String nextId() {
        return "model-" + sequence++;
    }

    private void attachCollision(Spatial spatial) {
        if (bulletAppState == null) {
            throw new IllegalStateException(
                    "BulletAppState is not available for model collision"
            );
        }
        CollisionShape shape = CollisionShapeFactory.createMeshShape(spatial);
        RigidBodyControl control = new RigidBodyControl(shape, 0f);
        spatial.addControl(control);
        bulletAppState.getPhysicsSpace().add(control);
    }

    private void removeCollision(Spatial spatial) {
        RigidBodyControl control = spatial.getControl(RigidBodyControl.class);
        if (control == null || bulletAppState == null) {
            return;
        }
        bulletAppState.getPhysicsSpace().remove(control);
        spatial.removeControl(RigidBodyControl.class);
    }
}
