package org.foxesworld.frozenlands.engine.providers.model;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.frozenlands.engine.KernelInterface;

import java.util.HashMap;
import java.util.Map;

public class ModelProvider {

    private final KernelInterface kernelInterface;
    private final AssetManager assetManager;
    private final BulletAppState bulletAppState;
    private final  Node rootNode;
    private Map<String, Spatial> modelsMap = new HashMap<>();

    public ModelProvider(KernelInterface kernelInterface) {
        this.kernelInterface = kernelInterface;
        this.assetManager = kernelInterface.getAssetManager();
        this.bulletAppState = kernelInterface.getBulletAppState();
        this.rootNode = kernelInterface.getRootNode();
    }

    public Spatial loadModels(String modelPath, String material) {
        Spatial model = loadModel(modelPath);
        model.setMaterial(kernelInterface.getMaterialManager().getMaterial(material));
        this.rootNode.attachChild(model);
        this.rootNode.addControl(createCollisionControl(model));
        return model;
    }

    private Spatial loadModel(String modelPath) {
        Spatial model = assetManager.loadModel(modelPath);
        model.setName("Model");
        return model;
    }

    private RigidBodyControl createCollisionControl(Spatial spatial) {
        CollisionShape shape = CollisionShapeFactory.createMeshShape(spatial);
        RigidBodyControl control = new RigidBodyControl(shape, 0);
        spatial.addControl(control);
        bulletAppState.getPhysicsSpace().add(control);
        return control;
    }
}
