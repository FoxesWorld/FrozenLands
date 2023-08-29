package org.foxesworld.newgame;

import codex.j3map.J3mapFactory;
import codex.j3map.processors.BooleanProcessor;
import codex.j3map.processors.FloatProcessor;
import codex.j3map.processors.IntegerProcessor;
import codex.j3map.processors.StringProcessor;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.post.FilterPostProcessor;
import com.jme3.system.AppSettings;
import org.foxesworld.newgame.engine.Kernel;
import org.foxesworld.newgame.engine.config.ConfigReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
        cfg.setSamples(16);
        cfg.setTitle("FrozenLand");
        app.setShowSettings(true);
        app.setDisplayFps(true);
        app.setDisplayStatView(false);
        app.setSettings(cfg);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setMoveSpeed(50);
        stateManager.attach(bulletAppState);
        bulletAppState.setDebugEnabled(true);
        assetManager.registerLoader(J3mapFactory.class, "j3map");
        J3mapFactory.registerAllProcessors(
                BooleanProcessor.class,
                StringProcessor.class,
                IntegerProcessor.class,
                FloatProcessor.class);
        NiftyJmeDisplay niftyDisplay = new NiftyJmeDisplay(assetManager, inputManager, audioRenderer, guiViewPort);
        fpp = new FilterPostProcessor(assetManager);
        new Kernel(this, niftyDisplay, fpp, bulletAppState, CONFIG);
        int numSamples = getContext().getSettings().getSamples();
        if (numSamples > 0) {
            fpp.setNumSamples(numSamples);
        }
        guiViewPort.addProcessor(niftyDisplay);
    }

    private  void setIcon(AppSettings settings, NewGame app){
        BufferedImage[] icons = null;
        try {
            icons = new BufferedImage[]{
                    ImageIO.read(app.getClass().getResourceAsStream("Icons/icon128x128.png")),
                    ImageIO.read(app.getClass().getResourceAsStream("Icons/icon64x64.png")),
                    ImageIO.read(app.getClass().getResourceAsStream("Icons/icon32x32.png"))
            };
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        settings.setIcons(icons);
    }



}