package org.takesome.frozenlands.engine.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takesome.frozenlands.engine.resources.ModuleIndexCatalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * External mutable runtime configuration store.
 *
 * <p>The packaged {@code core/src/main/resources/config/runtime.json} file remains
 * a clean default template. At runtime the template is exported once into the
 * application directory and all following reads go through that exported file.</p>
 */
public final class RuntimeConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfigStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private static final String CORE_MODULE_ID = "engine.core";
    private static final String RUNTIME_CONFIG_KEY = "runtime";
    private static final String RUNTIME_CONFIG_PROPERTY = "frozenlands.runtimeConfig";
    private static final String RUNTIME_CONFIG_ENV = "FROZENLANDS_RUNTIME_CONFIG";
    private static final String DEFAULT_EXTERNAL_CONFIG = "config/runtime.json";
    private static final String CLASSPATH_TEMPLATE = "config/runtime.json";

    private final ModuleIndexCatalog moduleIndexCatalog;
    private final String moduleId;
    private final String configKey;
    private final Path templatePath;
    private final Path externalPath;

    private RuntimeConfigStore(ModuleIndexCatalog moduleIndexCatalog, String moduleId, String configKey) {
        this.moduleIndexCatalog = moduleIndexCatalog;
        this.moduleId = moduleId;
        this.configKey = configKey;
        this.templatePath = resolveIndexedTemplatePath(moduleIndexCatalog, moduleId, configKey);
        this.externalPath = resolveExternalPath(moduleIndexCatalog.projectRoot());
    }

    public static RuntimeConfigStore core(ModuleIndexCatalog moduleIndexCatalog) {
        return new RuntimeConfigStore(moduleIndexCatalog, CORE_MODULE_ID, RUNTIME_CONFIG_KEY);
    }

    public Map<String, Object> readMap() {
        Path path = ensureExported();
        try {
            JsonNode node = MAPPER.readTree(path.toFile());
            return MAPPER.convertValue(node, MAP_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read external runtime config: " + path, e);
        }
    }

    public Path externalPath() {
        return externalPath;
    }

    public Path templatePath() {
        return templatePath;
    }

    public Path ensureExported() {
        if (Files.isRegularFile(externalPath)) {
            LOGGER.debug("Runtime config loaded from external file path={}", externalPath);
            return externalPath;
        }
        if (Files.exists(externalPath)) {
            throw new IllegalStateException("Runtime config path exists but is not a regular file: " + externalPath);
        }

        TemplateData template = readTemplate();
        try {
            Path parent = externalPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                    externalPath,
                    template.bytes(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            LOGGER.info(
                    "Runtime config exported module={} key={} template={} externalPath={}",
                    moduleId,
                    configKey,
                    template.source(),
                    externalPath
            );
        } catch (FileAlreadyExistsException ignored) {
            LOGGER.debug("Runtime config appeared during export path={}", externalPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export runtime config template to: " + externalPath, e);
        }
        return externalPath;
    }

    private TemplateData readTemplate() {
        if (templatePath != null && Files.isRegularFile(templatePath)) {
            try {
                byte[] bytes = Files.readAllBytes(templatePath);
                validateJsonTemplate(bytes, templatePath.toString());
                return new TemplateData(bytes, templatePath.toString());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read indexed runtime config template: " + templatePath, e);
            }
        }

        ClassLoader classLoader = RuntimeConfigStore.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(CLASSPATH_TEMPLATE)) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "Runtime config template was not found in filesystem or classpath: " + CLASSPATH_TEMPLATE
                );
            }
            byte[] bytes = inputStream.readAllBytes();
            validateJsonTemplate(bytes, "classpath:" + CLASSPATH_TEMPLATE);
            return new TemplateData(bytes, "classpath:" + CLASSPATH_TEMPLATE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath runtime config template: " + CLASSPATH_TEMPLATE, e);
        }
    }

    private void validateJsonTemplate(byte[] bytes, String source) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            MAPPER.readTree(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Runtime config template is not valid JSON: " + source, e);
        }
    }

    private Path resolveIndexedTemplatePath(ModuleIndexCatalog moduleIndexCatalog, String moduleId, String configKey) {
        try {
            String path = moduleIndexCatalog.optionalConfigPath(moduleId, configKey);
            return path == null ? null : Path.of(path).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Path resolveExternalPath(Path runtimeRoot) {
        String configuredPath = configuredExternalPath();
        Path candidate = configuredPath == null ? Path.of(DEFAULT_EXTERNAL_CONFIG) : Path.of(configuredPath);
        if (!candidate.isAbsolute()) {
            candidate = runtimeRoot.resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private String configuredExternalPath() {
        String value = System.getProperty(RUNTIME_CONFIG_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(RUNTIME_CONFIG_ENV);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record TemplateData(byte[] bytes, String source) {
    }
}
