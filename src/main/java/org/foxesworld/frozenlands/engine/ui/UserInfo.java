package org.foxesworld.frozenlands.engine.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.foxesworld.automaton.Automaton;
import org.foxesworld.automaton.elements.components.ComponentManager;
import org.foxesworld.frozenlands.engine.player.PlayerInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class UserInfo {

    private Node guiNode;
    private Camera cam;
    private  PlayerInterface playerInterface;

    public UserInfo(PlayerInterface playerInterface) {
        this.guiNode = playerInterface.getGuiNode();
        this.cam = playerInterface.getPlayerOptions().getFpsCam();
        this.playerInterface = playerInterface;
    }

    public void userInfo(ComponentManager componentManager){
        Automaton automaton = new Automaton(componentManager);
        automaton.setScreenHeight(this.cam.getHeight());
        automaton.setScreenWidth(this.cam.getWidth());
        JsonObject object = parseJsonObjectFromStream(UserInfo.class.getClassLoader().getResourceAsStream("ui/forms/userInfo.json"));
        guiNode.attachChild(automaton.createContainerFromJson(object));
    }


    private JsonObject parseJsonObjectFromStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder jsonStr = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStr.append(line);
            }
            return JsonParser.parseString(jsonStr.toString()).getAsJsonObject();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
