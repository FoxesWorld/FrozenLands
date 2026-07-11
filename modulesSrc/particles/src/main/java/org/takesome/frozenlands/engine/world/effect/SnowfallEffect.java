package org.takesome.frozenlands.engine.world.effect;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.takesome.frozenlands.engine.EngineContext;

public class SnowfallEffect extends ParticleEmitter {
    private final ParticleRuntimeSettings settings;

    public SnowfallEffect(EngineContext engineContext, ParticleRuntimeSettings settings) {
        super("Snow", ParticleMesh.Type.Triangle, settings.snowParticles());
        this.settings = settings;
        Material snowMat = engineContext.assets().createMaterial("Common/MatDefs/Misc/Particle.j3md");
        snowMat.setTexture("Texture", engineContext.assets().texture(settings.snowTexture()));
        setMaterial(snowMat);
        setImagesX(settings.snowImagesX());
        setImagesY(settings.snowImagesY());
        setStartColor(new ColorRGBA(1f, 1f, 1f, 0.9f));
        setEndColor(new ColorRGBA(1f, 1f, 1f, 0.15f));
        setStartSize(settings.startSize());
        setEndSize(settings.endSize());
        setGravity(0, settings.gravityY(), 0);
        setLowLife(settings.lowLife());
        setHighLife(settings.highLife());
        setParticlesPerSec(settings.snowRate());
        setShape(new EmitterBoxShape(
                new Vector3f(-settings.areaXZ(), -settings.areaHeight(), -settings.areaXZ()),
                new Vector3f(settings.areaXZ(), settings.areaHeight(), settings.areaXZ())));
        getParticleInfluencer().setInitialVelocity(new Vector3f(settings.velocityX(), settings.velocityY(), settings.velocityZ()));
        getParticleInfluencer().setVelocityVariation(settings.velocityVariation());
    }

    public void follow(Vector3f worldCenter) {
        setLocalTranslation(worldCenter.x, worldCenter.y + settings.followHeight(), worldCenter.z);
    }
}
