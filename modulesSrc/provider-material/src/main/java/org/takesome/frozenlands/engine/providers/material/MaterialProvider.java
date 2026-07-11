package org.takesome.frozenlands.engine.providers.material;

import com.jme3.material.Material;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.providers.EngineProvider;
import org.takesome.frozenlands.engine.providers.EngineProviders;
import org.takesome.frozenlands.engine.providers.ProviderCommand;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.takesome.frozenlands.engine.providers.ProviderArguments.string;
import static org.takesome.frozenlands.engine.providers.ProviderResponses.ok;

/** High-level material catalogue backed by the central FrozenLands AssetLibrary. */
public final class MaterialProvider implements EngineProvider {
    private static final ModuleIndexCatalog MODULE_INDEX = ModuleIndexCatalog.defaultCatalog();
    private static final String DEFAULT_MATERIALS_CONFIG =
            MODULE_INDEX.configPath(EngineProviders.MATERIAL_PROVIDER, "materials");

    private final EngineContext context;
    private final AssetLibrary assets;
    private final MaterialCatalogLoader catalogLoader;
    private final MaterialCompiler compiler;
    private final Set<String> materialIds = new LinkedHashSet<>();
    private final Map<String, ProviderCommand> commands = new LinkedHashMap<>();

    public MaterialProvider(EngineContext context) {
        this.context = context;
        this.assets = context.assets();
        this.catalogLoader = new MaterialCatalogLoader();
        this.compiler = new MaterialCompiler(context);
        registerCommands();
    }

    @Override
    public String id() {
        return EngineProviders.MATERIAL_PROVIDER;
    }

    @Override
    public void register(EngineContext context) {
        loadMaterials(DEFAULT_MATERIALS_CONFIG);
    }

    @Override
    public Map<String, ProviderCommand> commands() {
        return Collections.unmodifiableMap(commands);
    }

    @Override
    public Map<String, Object> luaDescriptor() {
        Map<String, Object> descriptor = EngineProvider.super.luaDescriptor();
        descriptor.put("materials", getMaterialIds());
        return descriptor;
    }

    public void loadMaterials(String path) {
        context.getLogger().info("Loading FrozenLands material catalogue path={}", path);
        materialIds.clear();
        catalogLoader.load(path).forEach(this::registerDeclaration);

        context.getProviderRegistry().publishEvent(
                "provider.material.loaded",
                ok(Map.of("count", materialIds.size(), "path", path))
        );
        context.getLogger().info("Material catalogue ready count={}", materialIds.size());
    }

    public Material createMat(String directory, String variant) {
        MaterialDeclaration declaration = MaterialDeclaration.of(directory, variant);
        registerDeclaration(declaration);
        return assets.material(declaration.id());
    }

    public Material getMaterial(String materialId) {
        if (!assets.registered(AssetLibrary.Type.MATERIAL, materialId)) {
            throw new IllegalArgumentException("Material is not registered: " + materialId);
        }
        return assets.material(materialId);
    }

    public List<String> getMaterialIds() {
        return List.copyOf(materialIds);
    }

    private void registerDeclaration(MaterialDeclaration declaration) {
        Material prototype = compiler.compile(declaration);
        assets.registerMaterial(declaration.id(), prototype, Map.of(
                "directory", declaration.directory(),
                "variant", declaration.variant(),
                "provider", id()
        ));
        materialIds.add(declaration.id());
    }

    private void registerCommands() {
        commands.put("load", ProviderCommand.of(
                "load",
                "Load material declarations from a resource path",
                arguments -> {
                    loadMaterials(string(arguments, "path", DEFAULT_MATERIALS_CONFIG));
                    return ok("count", materialIds.size());
                }
        ));
        commands.put("list", ProviderCommand.of(
                "list",
                "List registered material ids",
                arguments -> ok("materials", getMaterialIds())
        ));
        commands.put("get", ProviderCommand.of(
                "get",
                "Resolve a material prototype",
                arguments -> {
                    String materialId = string(arguments, "id", "");
                    return ok(Map.of(
                            "id", materialId,
                            "exists", assets.registered(
                                    AssetLibrary.Type.MATERIAL,
                                    materialId
                            )
                    ));
                }
        ));
    }
}
