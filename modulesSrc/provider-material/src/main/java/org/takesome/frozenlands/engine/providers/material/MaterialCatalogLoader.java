package org.takesome.frozenlands.engine.providers.material;

import com.fasterxml.jackson.databind.JsonNode;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MaterialCatalogLoader {
    private final ModuleIndexCatalog moduleIndex;

    MaterialCatalogLoader() {
        this(ModuleIndexCatalog.defaultCatalog());
    }

    MaterialCatalogLoader(ModuleIndexCatalog moduleIndex) {
        this.moduleIndex = Objects.requireNonNull(moduleIndex, "moduleIndex");
    }

    List<MaterialDeclaration> load(String path) {
        JsonNode root = moduleIndex.readJsonNode(path);
        if (!root.isArray()) {
            throw new IllegalArgumentException("Material catalog must be a JSON array: " + path);
        }
        List<MaterialDeclaration> declarations = new ArrayList<>();
        root.forEach(node -> declarations.add(MaterialDeclaration.parse(node.asText())));
        return List.copyOf(declarations);
    }
}
