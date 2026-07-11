package org.takesome.frozenlands.engine.providers.material;

record MaterialDeclaration(String id, String directory, String variant) {
    MaterialDeclaration {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Material id must not be blank");
        }
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Material directory must not be blank");
        }
        if (variant == null || variant.isBlank()) {
            throw new IllegalArgumentException("Material variant must not be blank");
        }
    }

    static MaterialDeclaration parse(String declaration) {
        if (declaration == null) {
            throw new IllegalArgumentException("Material declaration must not be null");
        }
        String raw = declaration.trim();
        int separator = raw.indexOf('#');
        if (separator <= 0 || separator == raw.length() - 1) {
            throw new IllegalArgumentException("Invalid material declaration: " + declaration);
        }
        return new MaterialDeclaration(
                raw,
                raw.substring(0, separator),
                raw.substring(separator + 1)
        );
    }

    static MaterialDeclaration of(String directory, String variant) {
        String safeDirectory = directory == null ? "" : directory.trim();
        String safeVariant = variant == null ? "" : variant.trim();
        return new MaterialDeclaration(
                safeDirectory + '#' + safeVariant,
                safeDirectory,
                safeVariant
        );
    }
}
