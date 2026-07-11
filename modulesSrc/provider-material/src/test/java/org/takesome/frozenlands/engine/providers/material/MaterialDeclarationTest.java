package org.takesome.frozenlands.engine.providers.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialDeclarationTest {
    @Test
    void parsesDirectoryAndVariant() {
        MaterialDeclaration declaration = MaterialDeclaration.parse("terrain#mount");

        assertEquals("terrain#mount", declaration.id());
        assertEquals("terrain", declaration.directory());
        assertEquals("mount", declaration.variant());
    }

    @Test
    void createsCanonicalDeclarationFromParts() {
        MaterialDeclaration declaration = MaterialDeclaration.of(" soil ", " default ");

        assertEquals("soil#default", declaration.id());
        assertEquals("soil", declaration.directory());
        assertEquals("default", declaration.variant());
    }

    @Test
    void rejectsMalformedDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MaterialDeclaration.parse("terrain")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MaterialDeclaration.parse("#default")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MaterialDeclaration.parse("terrain#")
        );
    }
}
