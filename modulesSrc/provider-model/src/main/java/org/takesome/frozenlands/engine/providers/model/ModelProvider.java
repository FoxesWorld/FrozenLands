package org.takesome.frozenlands.engine.providers.model;

import com.jme3.scene.Spatial;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.providers.EngineProvider;
import org.takesome.frozenlands.engine.providers.EngineProviders;
import org.takesome.frozenlands.engine.providers.ProviderCommand;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.takesome.frozenlands.engine.providers.ProviderArguments.bool;
import static org.takesome.frozenlands.engine.providers.ProviderArguments.reference;
import static org.takesome.frozenlands.engine.providers.ProviderArguments.string;
import static org.takesome.frozenlands.engine.providers.ProviderResponses.ok;

/** High-level model instance provider backed by the central AssetLibrary. */
public final class ModelProvider implements EngineProvider {
    private static final ModuleIndexCatalog MODULE_INDEX = ModuleIndexCatalog.defaultCatalog();
    private static final String DEFAULT_MODELS_CONFIG =
            MODULE_INDEX.configPath(EngineProviders.MODEL_PROVIDER, "models");

    private final AssetLibrary assets;
    private final ModelCatalogLoader catalogLoader;
    private final ModelInstanceRegistry instances;
    private final Map<String, ProviderCommand> commands = new LinkedHashMap<>();

    public ModelProvider(EngineContext context) {
        this.assets = context.assets();
        this.catalogLoader = new ModelCatalogLoader(context);
        this.instances = new ModelInstanceRegistry(context);
        registerCommands();
    }

    @Override
    public String id() {
        return EngineProviders.MODEL_PROVIDER;
    }

    @Override
    public void register(EngineContext context) {
        loadCatalog(DEFAULT_MODELS_CONFIG);
    }

    @Override
    public Map<String, ProviderCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    @Override
    public Map<String, Object> luaDescriptor() {
        Map<String, Object> descriptor = EngineProvider.super.luaDescriptor();
        descriptor.put("activeModels", getModelIds());
        descriptor.put("catalog", assets.catalog(AssetLibrary.Type.MODEL));
        return descriptor;
    }

    public void loadCatalog(String path) {
        catalogLoader.load(path);
    }

    public Spatial loadModels(String modelReference, String materialReference) {
        return instances.create(
                modelReference,
                materialReference,
                "",
                true,
                true
        );
    }

    public Spatial getModel(String id) {
        return instances.get(id);
    }

    public List<String> getModelIds() {
        return instances.ids();
    }

    private void registerCommands() {
        commands.put("load", ProviderCommand.of(
                "load",
                "Load and optionally attach a model",
                this::load
        ));
        commands.put("list", ProviderCommand.of(
                "list",
                "List active model instance ids",
                arguments -> ok("models", getModelIds())
        ));
        commands.put("catalog", ProviderCommand.of(
                "catalog",
                "List registered model assets",
                arguments -> ok("models", assets.catalog(AssetLibrary.Type.MODEL))
        ));
        commands.put("reloadCatalog", ProviderCommand.of(
                "reloadCatalog",
                "Reload model catalogue",
                arguments -> {
                    loadCatalog(string(arguments, "path", DEFAULT_MODELS_CONFIG));
                    return ok("models", assets.catalog(AssetLibrary.Type.MODEL));
                }
        ));
        commands.put("resolve", ProviderCommand.of(
                "resolve",
                "Resolve a model id or path",
                this::resolve
        ));
        commands.put("detach", ProviderCommand.of(
                "detach",
                "Detach and forget a model instance",
                arguments -> {
                    String id = string(arguments, "id", "");
                    return ok(Map.of("id", id, "detached", instances.detach(id)));
                }
        ));
    }

    private Map<String, Object> load(Map<String, Object> arguments) {
        String asset = reference(arguments);
        Spatial model = instances.create(
                asset,
                string(arguments, "material", ""),
                string(arguments, "id", ""),
                bool(arguments, "attach", true),
                bool(arguments, "collision", true)
        );
        return ok(Map.of(
                "id", model.getName(),
                "asset", asset,
                "source", assets.resolve(AssetLibrary.Type.MODEL, asset)
        ));
    }

    private Map<String, Object> resolve(Map<String, Object> arguments) {
        String asset = reference(arguments);
        return ok(Map.of(
                "asset", asset,
                "source", assets.resolve(AssetLibrary.Type.MODEL, asset),
                "exists", assets.exists(AssetLibrary.Type.MODEL, asset)
        ));
    }
}
