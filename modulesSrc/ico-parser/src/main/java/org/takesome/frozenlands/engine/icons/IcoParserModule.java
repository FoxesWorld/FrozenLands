package org.takesome.frozenlands.engine.icons;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.icons.selection.IcoImageSelector;
import org.takesome.frozenlands.engine.modules.EngineModule;
import org.takesome.frozenlands.engine.modules.ModuleCommand;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IcoParserModule implements EngineModule {
    public static final String MODULE_ID = "engine.icoParser";
    public static final String LOADER_SERVICE_ID = "engine.icons.loader";

    private final IcoFileParser parser;
    private final EngineIconLoader loader;
    private final Map<String, ModuleCommand> commands;
    private EngineContext context;

    public IcoParserModule() {
        this(new IcoFileParser());
    }

    public IcoParserModule(IcoFileParser parser) {
        this(parser, new EngineIconLoader(parser));
    }

    public IcoParserModule(IcoFileParser parser, EngineIconLoader loader) {
        this.parser = parser == null ? new IcoFileParser() : parser;
        this.loader = loader == null ? new EngineIconLoader(this.parser) : loader;
        Map<String, ModuleCommand> map = new LinkedHashMap<>();
        map.put("status", ModuleCommand.of("status", "Return IcoParser module status.", this::status));
        map.put("inspect", ModuleCommand.of("inspect", "Decode an ICO file and return frame metadata.", this::inspect));
        map.put("best", ModuleCommand.of("best", "Decode an ICO file and return the best matching frame metadata.", this::best));
        map.put("load", ModuleCommand.of("load", "Load ICO/PNG app icons through the engine icon resolver.", this::load));
        map.put("cache.clear", ModuleCommand.of("cache.clear", "Clear the engine icon loader cache.", this::clearCache));
        this.commands = Map.copyOf(map);
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Engine-owned ICO/app icon parser, selector and loader module.";
    }

    @Override
    public Map<String, ModuleCommand> commands() {
        return commands;
    }

    @Override
    public void onRegister(EngineContext context) {
        this.context = context;
    }

    public IcoFileParser parser() {
        return parser;
    }

    public EngineIconLoader loader() {
        return loader;
    }

    private Map<String, Object> status(Map<String, Object> arguments) {
        return Map.of(
                "ok", true,
                "module", MODULE_ID,
                "formats", List.of("ico", "png-backed-ico", "dib-backed-ico", "png", "jpeg"),
                "commands", commands.keySet(),
                "cacheSize", loader.cacheSize(),
                "services", List.of(IcoFileParser.class.getName(), EngineIconLoader.class.getName(), LOADER_SERVICE_ID)
        );
    }

    private Map<String, Object> inspect(Map<String, Object> arguments) {
        try {
            IcoImageSet set = parser.parseSet(resolvePath(arguments));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("count", set.images().length);
            result.put("sizes", set.availableSizes().stream().map(IcoParserModule::dimensionMap).toList());
            result.put("frames", set.info().stream().map(IcoParserModule::infoMap).toList());
            return result;
        } catch (IOException | RuntimeException error) {
            return error(error);
        }
    }

    private Map<String, Object> best(Map<String, Object> arguments) {
        try {
            IcoImageSet set = parser.parseSet(resolvePath(arguments));
            int targetWidth = intArgument(arguments, "width", 256);
            int targetHeight = intArgument(arguments, "height", targetWidth);
            BufferedImage best = IcoImageSelector.getBestMatchingIcon(set.images(), targetWidth, targetHeight);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("target", Map.of("width", targetWidth, "height", targetHeight));
            result.put("available", set.availableSizes().stream().map(IcoParserModule::dimensionMap).toList());
            result.put("best", best == null ? null : imageMap(best));
            return result;
        } catch (IOException | RuntimeException error) {
            return error(error);
        }
    }

    private Map<String, Object> load(Map<String, Object> arguments) {
        try {
            String reference = stringArgument(arguments, "reference", stringArgument(arguments, "path", ""));
            Path assetsDirectory = assetsDirectory(arguments);
            BufferedImage[] icons = loader.loadAppIcons(reference, assetsDirectory, IcoParserModule.class.getClassLoader());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("reference", reference);
            result.put("assetsDirectory", assetsDirectory == null ? "" : assetsDirectory.toString());
            result.put("count", icons.length);
            result.put("icons", List.of(icons).stream().map(IcoParserModule::imageMap).toList());
            result.put("cacheSize", loader.cacheSize());
            return result;
        } catch (IOException | RuntimeException error) {
            return error(error);
        }
    }

    private Map<String, Object> clearCache(Map<String, Object> arguments) {
        loader.clearCache();
        return Map.of("ok", true, "cacheSize", loader.cacheSize());
    }

    private Path resolvePath(Map<String, Object> arguments) throws IOException {
        Object pathValue = arguments == null ? null : arguments.get("path");
        if (pathValue == null || String.valueOf(pathValue).isBlank()) {
            throw new IOException("Missing required argument: path");
        }
        Path path = Path.of(String.valueOf(pathValue));
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path).normalize();
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("ICO file not found: " + path);
        }
        return path;
    }

    private static Path assetsDirectory(Map<String, Object> arguments) {
        String raw = stringArgument(arguments, "assetsDirectory", "assets");
        if (raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw);
        return path.isAbsolute() ? path.normalize() : Path.of("").toAbsolutePath().resolve(path).normalize();
    }

    private static String stringArgument(Map<String, Object> arguments, String key, String fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private static int intArgument(Map<String, Object> arguments, String key, int fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Math.max(0, Integer.parseInt(String.valueOf(value)));
    }

    private static Map<String, Object> infoMap(IcoImageInfo info) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("width", info.width());
        map.put("height", info.height());
        map.put("bitDepth", info.bitDepth());
        map.put("colors", info.colors());
        map.put("format", info.format());
        map.put("dataSize", info.dataSize());
        return map;
    }

    private static Map<String, Object> dimensionMap(Dimension dimension) {
        return Map.of("width", dimension.width, "height", dimension.height);
    }

    private static Map<String, Object> imageMap(BufferedImage image) {
        return Map.of(
                "width", image.getWidth(),
                "height", image.getHeight(),
                "type", image.getType(),
                "alpha", image.getColorModel().hasAlpha()
        );
    }

    private static Map<String, Object> error(Exception error) {
        return Map.of(
                "ok", false,
                "type", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        );
    }
}
