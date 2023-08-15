package org.foxesworld.newgame.engine.player.input;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioSource;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.screen.Screen;
import org.foxesworld.newgame.engine.player.CharacterSettings;
import org.foxesworld.newgame.engine.sound.SoundManager;
import org.foxesworld.newgame.engine.ui.HUDController;

import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class UserInputHandler extends AbstractControl implements ActionListener, AnalogListener {

    @Deprecated
    private String audioType;
    private final Vector3f walkDirection = new Vector3f();
    private BetterCharacterControl characterControl;
    private CharacterSettings characterSettings;
    private final InputManager inputManager;
    private final Camera cam;
    private final Node rootNode;
    private AudioNode walkAudio;
    private Runnable attackCallback;
    private boolean initialised = false;
    private boolean[] directions = new boolean[4];
    private AssetManager assetManager;
    private SoundManager soundManager;
    private Nifty nifty;
    static HashMap<String, List<Object>> userInputConfig;
    private float timeSinceLastAudio = 0;

    public UserInputHandler(NiftyJmeDisplay niftyDisplay, SoundManager soundManager, InputManager inputManager, AssetManager assetManager, Camera cam, Node rootNode, Runnable attackCallback, HashMap<String, List<Object>> userInputConfig) {
        this.soundManager = soundManager;
        this.inputManager = inputManager;
        this.assetManager = assetManager;
        this.nifty = niftyDisplay.getNifty();
        this.rootNode = rootNode;
        this.attackCallback = attackCallback;
        this.cam = cam;
        this.userInputConfig = userInputConfig;
        this.characterSettings = new CharacterSettings();
        this.nifty.fromXml("ui/test.xml", "hud", new HUDController());
    }

    private void initialize() {
        if (!initialised) {
            characterControl = spatial.getControl(BetterCharacterControl.class);
            if (characterControl == null) {
                System.err.println(getClass() + " can be attached only to a spatial that has a BetterCharacterControl");
                return;
            }
            inputManager.setCursorVisible(false);
            keyBindingInit(UserInputHelper.getInputMaps(userInputConfig));
            initialised = true;
        }
    }

    protected void keyBindingInit(Stack<String> inputMaps) {
        inputMaps.forEach(inputMap -> userInputConfig.get(inputMap).forEach(inputLine -> {
            InputType inputType = InputType.valueOf(inputMap.toUpperCase());
            int inputKey = (Integer) ((HashMap<?, ?>) inputLine).get("inputKey");
            String inputName = (String) ((HashMap<?, ?>) inputLine).get("inputName");

            switch (inputType) {
                case KEYBOARD:
                    inputManager.addMapping(inputName, new KeyTrigger(inputKey));
                    break;
                case MOUSEAXIS:
                    boolean negative = (Boolean) ((HashMap<?, ?>) inputLine).get("negative");
                    inputManager.addMapping(inputName, new MouseAxisTrigger(inputKey, negative));
                    break;
                case MOUSEBUTTONS:
                    inputManager.addMapping(inputName, new MouseButtonTrigger(inputKey));
                    break;
            }

            inputManager.addListener(this, inputName);
        }));
    }

    final float angles[] = {0, 0, 0};
    final Quaternion tmpRot = new Quaternion();
    final Vector3f tmpV3 = new Vector3f();

    @Override
    public void onAnalog(String name, float value, float tpf) {
        float rotationMultiplier = characterSettings.isRunning() ? 0.04f : 0.1f;
        value *= characterSettings.getCurrentSpeed() * rotationMultiplier;

        switch (name) {
            case "Rotate_Left":
                angles[1] += value;
                break;
            case "Rotate_Right":
                angles[1] -= value;
                break;
            case "Rotate_Up":
                angles[0] -= value;
                break;
            case "Rotate_Down":
                angles[0] += value;
                break;
        }

        angles[0] = FastMath.clamp(angles[0], -0.85f, 1.1f);

        tmpV3.set(Vector3f.UNIT_Z);
        tmpRot.fromAngles(angles);
        tmpRot.multLocal(tmpV3);
        characterControl.setViewDirection(tmpV3);
    }

    @Override
    public void onAction(String binding, boolean isPressed, float tpf) {
        switch (binding) {
            case "Left":
                directions[0] = isPressed;
                break;
            case "Right":
                directions[1] = isPressed;
                break;
            case "Up":
                directions[2] = isPressed;
                break;
            case "Down":
                directions[3] = isPressed;
                break;
            case "Attack":
                characterSettings.setAttacking(isPressed);
                if (isPressed) {
                    attackCallback.run();
                }
                break;
            case "Jump":
                characterSettings.setJumping(isPressed);
                if (isPressed) {
                    characterControl.jump();
                    characterControl.setPhysicsDamping(0);
                }
                break;
            case "Run":
                characterSettings.setRunning(isPressed);
                break;
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        initialize();
        System.out.println(characterSettings.isWalking());
        Quaternion tmpQtr = new Quaternion();
        float currentSpeed = characterSettings.getCurrentSpeed();
        float targetSpeed = characterSettings.isRunning() ? characterSettings.getRunSpeed() : characterSettings.getWalkSpeed();
        float speedChange = targetSpeed - currentSpeed;
        float actualSpeedChange = Math.signum(speedChange) * Math.min(characterSettings.getMaxSmoothSpeedChange() * tpf, Math.abs(speedChange));
        characterSettings.setCurrentSpeed(currentSpeed += actualSpeedChange);

        walkDirection.set(directions[0] ? 1 : directions[1] ? -1 : 0, 0, directions[2] ? 1 : directions[3] ? -1 : 0);
        walkDirection.multLocal(currentSpeed);
        tmpV3.set(characterControl.getViewDirection());
        tmpV3.y = 0;
        tmpV3.normalizeLocal();
        tmpQtr.lookAt(tmpV3, Vector3f.UNIT_Y);
        tmpQtr.multLocal(walkDirection);
        characterControl.setWalkDirection(walkDirection);
        characterControl.setPhysicsDamping(0.9f);
        characterSettings.setWalking(walkDirection.lengthSquared() > 0 && characterSettings.getIsInAir() == 0);
        updateMovementAudio(tpf);
        soundManager.update(tpf);
        characterSettings.setIsInAir(characterControl.isOnGround() ? 0 : characterSettings.getIsInAir() + tpf);
        updateHUDText(new String[]{"movement", "speed", "jumping"}, new String[]{audioType, String.valueOf(characterSettings.getCurrentSpeed()), String.valueOf(characterSettings.isJumping())});
    }

    private void updateMovementAudio(float tpf) {
        timeSinceLastAudio += tpf;
        float interval = Math.max(0.05f, 0.3f - characterSettings.getCurrentSpeed() * 0.01f);

        if (characterSettings.isWalking()) {
            if ((walkAudio == null || !walkAudio.getStatus().equals(AudioSource.Status.Playing)) && timeSinceLastAudio >= interval) {
                playWalkAudio();
                timeSinceLastAudio = 0;
            }
        } else {
            stopWalkAudio();
        }
    }

    private void playWalkAudio() {
        if (walkAudio != null) {
            walkAudio.stop();
            rootNode.detachChild(walkAudio);
            walkAudio = null;
        }

        audioType = characterSettings.isRunning() ? "sprint" : "walk";
        walkAudio = soundManager.getRandomAudioNode(audioType);
        if (walkAudio != null) {
            walkAudio.setLocalTranslation(spatial.getWorldTranslation());
            walkAudio.setPitch(characterSettings.getCurrentSpeed()/4);
            rootNode.attachChild(walkAudio);
            walkAudio.play();
        }
        updateHUDText(new String[]{"movement", "speed", "jumping"}, new String[]{audioType, String.valueOf(characterSettings.getCurrentSpeed()), String.valueOf(characterSettings.isJumping())});
    }

    private void stopWalkAudio() {
        if (walkAudio != null && walkAudio.getStatus().equals(AudioSource.Status.Playing)) {
            walkAudio.stop();
            rootNode.detachChild(walkAudio);
            walkAudio = null;
        }
    }

    private void updateHUDText(String[] elementIds, String[] values) {
        if (nifty != null) {
            Screen currentScreen = nifty.getCurrentScreen();
            if (currentScreen != null) {
                for (int i = 0; i < elementIds.length; i++) {
                    String elementId = elementIds[i];
                    String value = values[i];

                    Element hudElement = currentScreen.findElementById(elementId);
                    if (hudElement != null) {
                        TextRenderer textRenderer = hudElement.getRenderer(TextRenderer.class);
                        if (textRenderer != null) {
                            textRenderer.setText(String.valueOf(value));
                        }
                    }
                }
            }
        }
    }


    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // Оставьте этот метод пустым, если вам не нужно рендерить что-либо.
    }
}
