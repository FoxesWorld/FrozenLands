package org.takesome.frozenlands.engine.providers.material;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.texture.Texture;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.assets.AssetLibrary;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.takesome.frozenlands.engine.providers.ProviderArguments.requiredString;

final class MaterialCompiler {
    private final EngineContext context;
    private final AssetLibrary assets;
    private final ModuleIndexCatalog moduleIndex;

    MaterialCompiler(EngineContext context) {
        this(context, ModuleIndexCatalog.defaultCatalog());
    }

    MaterialCompiler(EngineContext context, ModuleIndexCatalog moduleIndex) {
        this.context = Objects.requireNonNull(context, "context");
        this.assets = context.assets();
        this.moduleIndex = Objects.requireNonNull(moduleIndex, "moduleIndex");
    }

    Material compile(MaterialDeclaration declaration) {
        String baseDirectory = "textures/" + declaration.directory() + '/';
        Map<String, Object> data = moduleIndex.readAssetJsonMap(
                context.getAssetManager(),
                baseDirectory + "matOpt/" + declaration.variant() + ".json"
        );
        Material material = assets.createMaterial(requiredString(data, "matDef"));
        material.setName(declaration.id());

        Map<String, Map<String, Object>> textures = nestedMap(data.get("textures"));
        textures.forEach((parameter, config) -> applyTexture(
                material,
                declaration,
                baseDirectory,
                parameter,
                config
        ));

        Map<String, Map<String, Object>> variables = nestedMap(data.get("vars"));
        variables.forEach((parameter, config) -> applyVariable(material, parameter, config));

        context.getLogger().info(
                "Material compiled id={} textures={} variables={}",
                declaration.id(),
                textures.size(),
                variables.size()
        );
        return material;
    }

    private void applyTexture(
            Material material,
            MaterialDeclaration declaration,
            String baseDirectory,
            String parameter,
            Map<String, Object> config
    ) {
        String texturePath = baseDirectory + "textures/" + requiredString(config, "texture");
        String textureId = declaration.id() + '.' + parameter;
        assets.registerTexture(textureId, texturePath, Map.of(
                "material", declaration.id(),
                "parameter", parameter
        ));
        material.setTexture(
                parameter,
                assets.texture(textureId, parseWrap(config.get("wrap")))
        );
    }

    private void applyVariable(
            Material material,
            String parameter,
            Map<String, Object> config
    ) {
        VariableType type = VariableType.valueOf(
                requiredString(config, "type").toUpperCase(Locale.ROOT)
        );
        Object value = config.get("value");
        switch (type) {
            case FLOAT -> material.setFloat(parameter, number(value, parameter));
            case BOOLEAN -> material.setBoolean(parameter, booleanValue(value, parameter));
            case COLOR -> material.setColor(parameter, parseColor(String.valueOf(value)));
            case VECTOR -> material.setVector3(parameter, parseVector(String.valueOf(value)));
        }
    }

    private Texture.WrapMode parseWrap(Object value) {
        String raw = value == null
                ? "NONE"
                : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "REPEAT" -> Texture.WrapMode.Repeat;
            case "MIRRORED_REPEAT", "MIRROREDREPEAT" -> Texture.WrapMode.MirroredRepeat;
            case "EDGE_CLAMP", "EDGECLAMP" -> Texture.WrapMode.EdgeClamp;
            case "NONE", "" -> null;
            default -> throw new IllegalArgumentException(
                    "Unsupported texture wrap mode: " + value
            );
        };
    }

    private ColorRGBA parseColor(String value) {
        float[] components = parseComponents(value, 4, "color");
        return new ColorRGBA(
                components[0],
                components[1],
                components[2],
                components[3]
        );
    }

    private Vector3f parseVector(String value) {
        float[] components = parseComponents(value, 3, "vector");
        return new Vector3f(components[0], components[1], components[2]);
    }

    private float[] parseComponents(String value, int count, String kind) {
        String[] parts = value.split(",");
        if (parts.length != count) {
            throw new IllegalArgumentException("Invalid " + kind + " value: " + value);
        }
        float[] result = new float[count];
        for (int index = 0; index < count; index++) {
            result[index] = Float.parseFloat(parts[index].trim());
        }
        return result;
    }

    private float number(Object value, String parameter) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid numeric material parameter " + parameter + ": " + value,
                    exception
            );
        }
    }

    private boolean booleanValue(Object value, String parameter) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing boolean material parameter: " + parameter
            );
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Map<String, Object>> nestedMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected an object, got: " + value);
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(
                String.valueOf(key),
                stringObjectMap(nested, key)
        ));
        return result;
    }

    private Map<String, Object> stringObjectMap(Object value, Object key) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected nested object for key: " + key);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((nestedKey, nestedValue) ->
                result.put(String.valueOf(nestedKey), nestedValue));
        return result;
    }

    private enum VariableType {
        FLOAT,
        VECTOR,
        BOOLEAN,
        COLOR
    }
}
