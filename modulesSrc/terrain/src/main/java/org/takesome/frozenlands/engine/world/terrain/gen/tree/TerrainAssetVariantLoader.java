package org.takesome.frozenlands.engine.world.terrain.gen.tree;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.takesome.frozenlands.engine.EngineContext;
import org.takesome.frozenlands.engine.world.terrain.TerrainPlacementGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TerrainAssetVariantLoader {
    private final EngineContext context;

    TerrainAssetVariantLoader(EngineContext context) {
        this.context = context;
    }

    Map<String, List<Spatial>> loadVariants(List<TerrainPlacementGroup> groups) {
        Map<String, List<Spatial>> result = new LinkedHashMap<>();
        for (TerrainPlacementGroup group : groups) {
            List<Spatial> variants = new ArrayList<>();
            for (String modelPath : group.modelPaths()) {
                loadAssetPackVariants(group, modelPath, variants);
            }
            if (!variants.isEmpty()) {
                result.put(group.id(), List.copyOf(variants));
            }
            context.getLogger().info(
                    "Terrain asset variants loaded: group={} kind={} packs={} variants={}",
                    group.id(),
                    group.kind(),
                    group.modelPaths(),
                    variants.size()
            );
        }
        return Map.copyOf(result);
    }

    private void loadAssetPackVariants(
            TerrainPlacementGroup group,
            String modelPath,
            List<Spatial> target
    ) {
        int before = target.size();
        Spatial assetPack = context.assets().model(modelPath);
        assetPack.setShadowMode(RenderQueue.ShadowMode.Cast);
        Node variantParent = findVariantParent(assetPack);
        if (variantParent == null) {
            target.add(centeredVariant(
                    assetPack.clone(),
                    group,
                    target.size(),
                    modelPath,
                    "fallback-pack"
            ));
            context.getLogger().warn(
                    "Terrain asset variant parent not found; using whole pack: group={} path={}",
                    group.id(),
                    modelPath
            );
            return;
        }

        List<Spatial> children = List.copyOf(variantParent.getChildren());
        for (Spatial child : children) {
            if (!containsGeometry(child)) {
                continue;
            }
            Spatial variantPack = assetPack.clone();
            Node clonedParent = findNodeByName(variantPack, variantParent.getName());
            if (clonedParent == null) {
                continue;
            }
            keepOnlyChild(clonedParent, child.getName());
            target.add(centeredVariant(
                    variantPack,
                    group,
                    target.size(),
                    modelPath,
                    child.getName()
            ));
        }

        if (target.size() == before) {
            target.add(centeredVariant(
                    assetPack.clone(),
                    group,
                    target.size(),
                    modelPath,
                    "fallback-pack"
            ));
        }
        context.getLogger().info(
                "Terrain asset pack loaded as variants: group={} path={} variantsAdded={} totalVariants={}",
                group.id(),
                modelPath,
                target.size() - before,
                target.size()
        );
    }

    private Node findVariantParent(Spatial spatial) {
        if (!(spatial instanceof Node node)) {
            return null;
        }
        long renderableChildren = node.getChildren()
                .stream()
                .filter(this::containsGeometry)
                .count();
        if (renderableChildren > 1) {
            return node;
        }
        for (Spatial child : node.getChildren()) {
            Node found = findVariantParent(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean containsGeometry(Spatial spatial) {
        if (spatial instanceof Geometry) {
            return true;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                if (containsGeometry(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Node findNodeByName(Spatial spatial, String name) {
        if (spatial instanceof Node node) {
            if (name == null ? node.getName() == null : name.equals(node.getName())) {
                return node;
            }
            for (Spatial child : node.getChildren()) {
                Node found = findNodeByName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void keepOnlyChild(Node parent, String childName) {
        for (Spatial child : List.copyOf(parent.getChildren())) {
            if (childName == null
                    ? child.getName() != null
                    : !childName.equals(child.getName())) {
                child.removeFromParent();
            }
        }
    }

    private Spatial centeredVariant(
            Spatial variant,
            TerrainPlacementGroup group,
            int index,
            String modelPath,
            String sourceName
    ) {
        Node wrapper = new Node(
                "terrainAssetVariant_" + index
                        + "_" + safeName(group.id())
                        + "_" + safeName(modelPath)
                        + "_" + safeName(sourceName)
        );
        wrapper.attachChild(variant);
        wrapper.setShadowMode(RenderQueue.ShadowMode.Cast);
        refreshBounds(wrapper);

        BoundingBox bounds = boundsOf(wrapper);
        if (bounds != null) {
            com.jme3.math.Vector3f center = bounds.getCenter();
            float bottom = center.y - bounds.getYExtent();
            variant.move(-center.x, -bottom, -center.z);
            refreshBounds(wrapper);
        }
        return wrapper;
    }

    private String safeName(String name) {
        return name == null || name.isBlank()
                ? "unnamed"
                : name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private void refreshBounds(Spatial spatial) {
        spatial.updateModelBound();
        spatial.updateGeometricState();
    }

    private BoundingBox boundsOf(Spatial spatial) {
        refreshBounds(spatial);
        BoundingVolume bound = spatial.getWorldBound();
        return bound instanceof BoundingBox box ? box : null;
    }
}
