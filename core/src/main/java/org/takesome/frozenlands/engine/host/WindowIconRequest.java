package org.takesome.frozenlands.engine.host;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record WindowIconRequest(
        String appId,
        String applicationName,
        String appOverrideReference,
        String engineDefaultReference,
        Path assetsDirectory,
        ClassLoader classLoader
) {
    public static final String ENGINE_DEFAULT_ICON = "org/takesome/frozenlands/engine/ui/prelaunch/icon.png";

    public WindowIconRequest {
        appId = appId == null || appId.isBlank() ? "frozenlands" : appId.trim();
        applicationName = applicationName == null || applicationName.isBlank() ? "FrozenLands" : applicationName.trim();
        appOverrideReference = appOverrideReference == null ? "" : appOverrideReference.trim();
        engineDefaultReference = engineDefaultReference == null || engineDefaultReference.isBlank()
                ? ENGINE_DEFAULT_ICON
                : engineDefaultReference.trim();
    }

    public String reference() {
        return !appOverrideReference.isBlank() ? appOverrideReference : engineDefaultReference;
    }

    public List<String> orderedReferences() {
        ArrayList<String> references = new ArrayList<>();
        add(references, appOverrideReference);
        add(references, System.getProperty("frozenlands." + appId + ".appIcon"));
        add(references, System.getProperty("frozenlands." + appId + ".windowIcon"));
        add(references, System.getProperty("frozenlands.appIcon"));
        add(references, System.getProperty("frozenlands.windowIcon"));
        add(references, engineDefaultReference);
        return List.copyOf(references);
    }

    private static void add(ArrayList<String> references, String value) {
        if (value != null && !value.isBlank()) {
            String trimmed = value.trim();
            if (!references.contains(trimmed)) {
                references.add(trimmed);
            }
        }
    }
}
