package org.foxesworld.newgame;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import org.foxesworld.newgame.engine.Kernel;
import org.foxesworld.newgame.engine.config.ConfigReader;

import java.util.Map;


public class NewGame extends SimpleApplication {

    protected BulletAppState bulletAppState = new BulletAppState();
    protected FilterPostProcessor fpp;
    protected static Map CONFIG;

    public static void main(String[] args) {
        NewGame app = new NewGame();
        CONFIG = new ConfigReader(new String[]{"userInput", "internal/sounds"}).getCfgMaps();
        AppSettings cfg = new AppSettings(true);
        cfg.setVSync(false);
        cfg.setResolution(1360, 768);
        cfg.setFullscreen(false);
        cfg.setSamples(16);    // anti-aliasing
        cfg.setTitle("NewGame"); // branding: window name
        app.setShowSettings(true); // or don't display splashscreen
        app.setDisplayFps(true);
        app.setDisplayStatView(false);
        app.setSettings(cfg);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        stateManager.attach(bulletAppState);
        fpp = new FilterPostProcessor(assetManager);
        new Kernel(assetManager, cam, rootNode, fpp, inputManager, bulletAppState, CONFIG);
    }

    @Override
    public void simpleUpdate(float tpf) {
        // Update the player controller each frame
    }


}