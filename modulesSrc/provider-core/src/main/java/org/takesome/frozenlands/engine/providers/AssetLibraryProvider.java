package org.takesome.frozenlands.engine.providers;

import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetCatalogLoader;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.takesome.frozenlands.engine.providers.ProviderArguments.reference;
import static org.takesome.frozenlands.engine.providers.ProviderArguments.string;
import static org.takesome.frozenlands.engine.providers.ProviderResponses.ok;

/** Provider-command facade for the central FrozenLands AssetLibrary. */
public final class AssetLibraryProvider implements EngineProvider {
    private static final String PROVIDER_CORE_MODULE = "engine.providers";
    private static final ModuleIndexCatalog MODULE_INDEX = ModuleIndexCatalog.defaultCatalog();
    private static final String DEFAULT_ASSET_CATALOG =
            MODULE_INDEX.configPath(PROVIDER_CORE_MODULE, "assets");
    private static final Map<String, AssetLibrary.Type> CATALOG_SECTIONS = Map.of(
            "models", AssetLibrary.Type.MODEL,
            "textures", AssetLibrary.Type.TEXTURE
    );

    private final AssetLibrary assets;
    private final AssetCatalogLoader catalogLoader;
    private final Map<String, ProviderCommand> commands = new LinkedHashMap<>();

    public AssetLibraryProvider(EngineContext context) {
        this.assets = context.assets();
        this.catalogLoader = new AssetCatalogLoader(assets);
        registerCommands();
    }

    @Override
    public String id() {
        return EngineProviders.ASSET_PROVIDER;
    }

    @Override
    public String luaApiPath() {
        return MODULE_INDEX.luaPath(PROVIDER_CORE_MODULE, "assetsApi");
    }

    @Override
    public void register(EngineContext context) {
        catalogLoader.load(DEFAULT_ASSET_CATALOG, CATALOG_SECTIONS, Map.of("provider", id()));
    }

    @Override
    public Map<String, ProviderCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    @Override
    public Map<String, Object> luaDescriptor() {
        Map<String, Object> descriptor = EngineProvider.super.luaDescriptor();
        descriptor.put("catalog", assets.catalog());
        descriptor.put("status", assets.status());
        return descriptor;
    }

    private void registerCommands() {
        commands.put("status", ProviderCommand.of(
                "status",
                "Asset library status",
                arguments -> assets.status()
        ));
        commands.put("catalog", ProviderCommand.of(
                "catalog",
                "List registered assets",
                arguments -> catalog(arguments)
        ));
        commands.put("resolve", ProviderCommand.of(
                "resolve",
                "Resolve an asset id to its source",
                arguments -> resolve(arguments)
        ));
        commands.put("preload", ProviderCommand.of(
                "preload",
                "Load an asset into the runtime cache",
                arguments -> preload(arguments)
        ));
        commands.put("evict", ProviderCommand.of(
                "evict",
                "Evict an asset from the runtime cache",
                arguments -> evict(arguments)
        ));
        commands.put("clear", ProviderCommand.of(
                "clear",
                "Clear the runtime asset cache",
                arguments -> {
                    assets.clearRuntimeCache();
                    return ok("cleared", true);
                }
        ));
    }

    private Map<String, Object> catalog(Map<String, Object> arguments) {
        String rawType = string(arguments, "type", "");
        return ok("assets", rawType.isBlank()
                ? assets.catalog()
                : assets.catalog(parseType(rawType)));
    }

    private Map<String, Object> resolve(Map<String, Object> arguments) {
        AssetLibrary.Type type = parseType(string(arguments, "type", ""));
        String asset = reference(arguments);
        return ok(Map.of(
                "reference", asset,
                "type", type.name(),
                "source", assets.resolve(type, asset),
                "exists", assets.exists(type, asset)
        ));
    }

    private Map<String, Object> preload(Map<String, Object> arguments) {
        AssetLibrary.Type type = parseType(string(arguments, "type", ""));
        String asset = reference(arguments);
        assets.preload(type, asset);
        return ok(Map.of(
                "reference", asset,
                "type", type.name(),
                "source", assets.resolve(type, asset)
        ));
    }

    private Map<String, Object> evict(Map<String, Object> arguments) {
        AssetLibrary.Type type = parseType(string(arguments, "type", ""));
        String asset = reference(arguments);
        return ok(Map.of(
                "reference", asset,
                "type", type.name(),
                "removed", assets.evict(type, asset)
        ));
    }

    private AssetLibrary.Type parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Asset type must be model, texture, or material");
        }
        return switch (value.trim().toLowerCase()) {
            case "model", "models" -> AssetLibrary.Type.MODEL;
            case "texture", "textures" -> AssetLibrary.Type.TEXTURE;
            case "material", "materials" -> AssetLibrary.Type.MATERIAL;
            default -> throw new IllegalArgumentException("Unsupported asset type: " + value);
        };
    }
}
