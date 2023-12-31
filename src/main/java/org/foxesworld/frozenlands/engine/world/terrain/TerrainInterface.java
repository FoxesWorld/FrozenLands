package org.foxesworld.frozenlands.engine.world.terrain;

import com.jme3.terrain.geomipmap.TerrainQuad;

public interface TerrainInterface {
    TerrainQuad generateTerrain();
    TerrainQuad generateMountains();
    void update();
}