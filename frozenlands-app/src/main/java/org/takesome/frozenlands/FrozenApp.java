package org.takesome.frozenlands;

import com.jme3.system.AppSettings;
import org.takesome.frozenlands.engine.host.EngineWindowIcons;
import org.takesome.frozenlands.engine.host.WindowIconRequest;

import java.nio.file.Path;

/** Application descriptor. It does not implement engine behavior; it only uses engine host APIs. */
public record FrozenApp(
        String appId,
        String applicationName,
        String appIconOverride,
        String engineDefaultIcon,
        Path assetsDirectory
) {
    public static final String DEFAULT_APP_ID = "frozenlands";
    public static final String DEFAULT_APP_NAME = "FrozenLands";

    public FrozenApp {
        appId = appId == null || appId.isBlank() ? DEFAULT_APP_ID : appId.trim();
        applicationName = applicationName == null || applicationName.isBlank() ? DEFAULT_APP_NAME : applicationName.trim();
        appIconOverride = appIconOverride == null ? "" : appIconOverride.trim();
        engineDefaultIcon = engineDefaultIcon == null || engineDefaultIcon.isBlank()
                ? WindowIconRequest.ENGINE_DEFAULT_ICON
                : engineDefaultIcon.trim();
        assetsDirectory = assetsDirectory == null ? Path.of("assets").toAbsolutePath().normalize() : assetsDirectory.toAbsolutePath().normalize();
    }

    public static FrozenApp frozenLands() {
        return new FrozenApp(
                DEFAULT_APP_ID,
                DEFAULT_APP_NAME,
                System.getProperty("frozenlands.app.icon", ""),
                WindowIconRequest.ENGINE_DEFAULT_ICON,
                Path.of("assets")
        );
    }

    public AppSettings requestSettings() {
        return PreLaunchSettingsDialog.request(applicationName);
    }

    public boolean applyWindowIcon(AppSettings settings) {
        return EngineWindowIcons.applyToSettings(settings, windowIconRequest());
    }

    public WindowIconRequest windowIconRequest() {
        return new WindowIconRequest(
                appId,
                applicationName,
                appIconOverride,
                engineDefaultIcon,
                assetsDirectory,
                FrozenApp.class.getClassLoader()
        );
    }
}
