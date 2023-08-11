package org.foxesworld.engine.player;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.NewGame;
import org.foxesworld.engine.player.camera.CameraFollowSpatial;
import org.foxesworld.engine.player.input.FPSViewControl;
import org.foxesworld.engine.player.input.UserInputHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Player extends Node {

    private Vector3f jumpForce = new Vector3f(0, 300, 0);
    private AssetManager assetManager;
    private Node rootNode;
    private PhysicsSpace pspace;
    private NewGame GameInstance;
    private Map<String, Map> CFG;

    public Player(NewGame app){
        this.GameInstance = app;
        this.assetManager = app.getAssetManager();
        this.rootNode = app.getRootNode();
        this.pspace = app.getBullet().getPhysicsSpace();
        this.CFG = app.getCONFIG();

        Spatial actorLoad = assetManager.loadModel("assets/models/Jesse.glb");
        actorLoad.setLocalScale(1f);
        attachChild(actorLoad);
        setCullHint(CullHint.Never);
    }

    public void addPlayer(Camera cam, Vector3f spawnPoint){
        Player fpsPlayer = (Player) this.clone();
        Camera fpsCam = cam.clone();
        this.loadFPSLogicWorld(cam, fpsCam, fpsPlayer, spawnPoint);
        fpsPlayer.loadFPSLogicFPSView(cam, fpsCam, this);
    }
    public void loadFPSLogicWorld(Camera cam, Camera fpsCam, Spatial fpsJesse, Vector3f spawnPoint){
        BoundingBox jesseBbox=(BoundingBox)getWorldBound();
        BetterCharacterControl characterControl = new BetterCharacterControl(jesseBbox.getXExtent(), jesseBbox.getYExtent(), 50f);
        characterControl.setJumpForce(jumpForce);
        addControl(characterControl);

        // Установка позиции спавна игрока
        characterControl.warp(spawnPoint);

        // Load character logic
        addControl(new UserInputHandler(GameInstance,()->{
            fpsJesse.getControl(ActionsControl.class).shot( assetManager,cam.getLocation().add(cam.getDirection().mult(1)),cam.getDirection(),this.rootNode, this.pspace);
        }, (HashMap<String, List<Object>>) CFG.get("userInput")));
        addControl(new CameraFollowSpatial(cam));
        addControl(new ActionsControl(assetManager,true));
        addControl(new FPSViewControl(FPSViewControl.Mode.WORLD_SCENE));
    }

    public void loadFPSLogicFPSView(Camera cam, Camera fpsCam, Spatial jesse){
        addControl(new AbstractControl(){
            protected void controlUpdate(float tpf) {
                setLocalTransform(jesse.getWorldTransform());
                fpsCam.setLocation(cam.getLocation());
                fpsCam.lookAtDirection(cam.getDirection(),cam.getUp());
            }
            protected void controlRender(RenderManager rm, ViewPort vp) { }
        });
        addControl(new FPSViewControl(FPSViewControl.Mode.FPS_SCENE));
        addControl(new ActionsControl(assetManager,true,jesse.getControl(BetterCharacterControl.class)));

    }

    public Node getPlayerNode() {
        return  this;
    }
}