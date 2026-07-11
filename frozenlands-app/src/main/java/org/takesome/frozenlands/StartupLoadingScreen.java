package org.takesome.frozenlands;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;
import org.takesome.frozenlands.engine.ui.screen.UiScreen;
import org.takesome.frozenlands.engine.ui.screen.UiScreenModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Native JME startup overlay.
 *
 * <p>This class intentionally avoids HtmlDom/Swing frame rendering during boot.
 * Startup is a hot path: every frame must only update JME spatials, transforms,
 * colors and a few bitmap texts. Logo PNGs are loaded once, then rendered as
 * regular GUI {@link Picture} sprites.</p>
 */
final class StartupLoadingScreen implements UiScreen {
    private static final String DEFAULT_FONT = "Interface/Fonts/Default.fnt";
    private static final String LOGO_RESOURCE = "frozenlands/startup/img/helix.png";
    private static final String WORDMARK_RESOURCE = "frozenlands/startup/img/logoText.png";

    private static final float GUI_Z = 0f;
    private static final float MIN_WIDTH = 320f;
    private static final float MIN_HEIGHT = 180f;
    private static final float CARD_WIDTH = 720f;
    private static final float CARD_HEIGHT = 510f;
    private static final float LOGO_SIZE = 164f;
    private static final float WORDMARK_WIDTH = 468f;
    private static final float BAR_WIDTH = 456f;
    private static final float BAR_HEIGHT = 14f;

    private static final ColorRGBA BACKGROUND = new ColorRGBA(0.025f, 0.035f, 0.052f, 1f);
    private static final ColorRGBA AURORA = new ColorRGBA(0.17f, 0.54f, 0.82f, 0.20f);
    private static final ColorRGBA DRIFT = new ColorRGBA(0.56f, 0.86f, 1.0f, 0.35f);
    private static final ColorRGBA CARD = new ColorRGBA(0.035f, 0.082f, 0.132f, 0.88f);
    private static final ColorRGBA CARD_BORDER = new ColorRGBA(0.44f, 0.82f, 1f, 0.36f);
    private static final ColorRGBA LOGO_FRAME = new ColorRGBA(0.10f, 0.22f, 0.34f, 0.72f);
    private static final ColorRGBA LOGO_AURA = new ColorRGBA(0.38f, 0.84f, 1f, 0.22f);
    private static final ColorRGBA TRACK = new ColorRGBA(0.075f, 0.16f, 0.23f, 0.96f);
    private static final ColorRGBA FILL = new ColorRGBA(0.35f, 0.82f, 1f, 0.94f);
    private static final ColorRGBA SHINE = new ColorRGBA(0.92f, 0.985f, 1f, 0.54f);
    private static final ColorRGBA TEXT = new ColorRGBA(0.90f, 0.97f, 1f, 1f);
    private static final ColorRGBA MUTED = new ColorRGBA(0.66f, 0.86f, 0.97f, 1f);

    private final AssetManager assetManager;
    private final Node guiNode;
    private final Node root = new Node("StartupLoadingScreen.NativeRoot");
    private final Node logoAnchor = new Node("StartupLoadingScreen.LogoAnchor");
    private final Node logoSpinAnchor = new Node("StartupLoadingScreen.LogoSpinAnchor");

    private final Geometry background;
    private final Geometry auroraLeft;
    private final Geometry auroraRight;
    private final Geometry driftA;
    private final Geometry driftB;
    private final Geometry driftC;
    private final Geometry card;
    private final Geometry cardBorderTop;
    private final Geometry cardBorderBottom;
    private final Geometry logoAuraA;
    private final Geometry logoAuraB;
    private final Geometry logoFrame;
    private final Geometry progressTrack;
    private final Geometry progressFill;
    private final Geometry progressShine;

    private final Picture logoPicture = new Picture("StartupLoadingScreen.HelixLogo");
    private final Picture wordmarkPicture = new Picture("StartupLoadingScreen.Wordmark");
    private final BitmapText fallbackLogoText;
    private final BitmapText fallbackWordmarkText;
    private final BitmapText subtitleText;
    private final BitmapText stageText;
    private final BitmapText percentText;

    private final TextureResource logoTexture;
    private final TextureResource wordmarkTexture;
    private final Quaternion logoRotation = new Quaternion();
    private final Quaternion auraRotation = new Quaternion();

    private String currentStage = "Opening FrozenLands window";
    private float targetProgress;
    private float displayedProgress;
    private boolean visible;
    private long visibleSinceNanos;
    private long lastFrameNanos;
    private int lastPercent = -1;
    private int lastWidth = -1;
    private int lastHeight = -1;

    private float barX;
    private float barY;
    private float barWidth;
    private float barHeight;

    StartupLoadingScreen(AssetManager assetManager, Node guiNode) {
        this.assetManager = Objects.requireNonNull(assetManager, "assetManager");
        this.guiNode = Objects.requireNonNull(guiNode, "guiNode");

        BitmapFont font = assetManager.loadFont(DEFAULT_FONT);
        logoTexture = loadTextureResource(LOGO_RESOURCE);
        wordmarkTexture = loadTextureResource(WORDMARK_RESOURCE);

        background = rectangle("startup.background", BACKGROUND);
        auroraLeft = rectangle("startup.aurora.left", AURORA);
        auroraRight = rectangle("startup.aurora.right", AURORA);
        driftA = rectangle("startup.drift.a", DRIFT);
        driftB = rectangle("startup.drift.b", DRIFT);
        driftC = rectangle("startup.drift.c", DRIFT);
        card = rectangle("startup.card", CARD);
        cardBorderTop = rectangle("startup.card.border.top", CARD_BORDER);
        cardBorderBottom = rectangle("startup.card.border.bottom", CARD_BORDER);
        logoAuraA = rectangle("startup.logo.aura.a", LOGO_AURA);
        logoAuraB = rectangle("startup.logo.aura.b", LOGO_AURA);
        logoFrame = rectangle("startup.logo.frame", LOGO_FRAME);
        progressTrack = rectangle("startup.progress.track", TRACK);
        progressFill = rectangle("startup.progress.fill", FILL);
        progressShine = rectangle("startup.progress.shine", SHINE);

        fallbackLogoText = text(font, "✦", 64f, TEXT);
        fallbackWordmarkText = text(font, "FrozenLands", 36f, TEXT);
        subtitleText = text(font, "Runtime boot sequence", 16f, MUTED);
        stageText = text(font, currentStage, 15f, TEXT);
        percentText = text(font, "0%", 14f, TEXT);

        configurePicture(logoPicture, logoTexture);
        configurePicture(wordmarkPicture, wordmarkTexture);
        configureRoot();
    }

    @Override
    public String id() {
        return UiScreenModel.LOADING;
    }

    @Override
    public void show(UiScreenModel model, int width, int height) {
        updateModel(model);
        attachIfNeeded();
        layoutSurface(width, height);
        visible = true;
        visibleSinceNanos = System.nanoTime();
        lastFrameNanos = visibleSinceNanos;
        root.setCullHint(Spatial.CullHint.Never);
        renderFrame(width, height);
    }

    @Override
    public void update(UiScreenModel model, int width, int height) {
        updateModel(model);
        attachIfNeeded();
        layoutSurface(width, height);
        visible = true;
        root.setCullHint(Spatial.CullHint.Never);
    }

    void show(int width, int height) {
        show(UiScreenModel.loading(currentStage, targetProgress), width, height);
    }

    void update(String stage, float progress, int width, int height) {
        update(UiScreenModel.loading(stage, progress), width, height);
    }

    @Override
    public void renderFrame(int width, int height) {
        if (!visible) {
            return;
        }
        layoutSurface(width, height);
        animate(System.nanoTime());
    }

    @Override
    public void hide() {
        visible = false;
        root.setCullHint(Spatial.CullHint.Always);
        root.removeFromParent();
    }

    private void configureRoot() {
        root.setQueueBucket(RenderQueue.Bucket.Gui);
        root.setCullHint(Spatial.CullHint.Always);
        root.setLocalTranslation(0f, 0f, GUI_Z);

        root.attachChild(background);
        root.attachChild(auroraLeft);
        root.attachChild(auroraRight);
        root.attachChild(driftA);
        root.attachChild(driftB);
        root.attachChild(driftC);
        root.attachChild(card);
        root.attachChild(cardBorderTop);
        root.attachChild(cardBorderBottom);
        root.attachChild(logoAuraA);
        root.attachChild(logoAuraB);
        root.attachChild(logoFrame);

        logoSpinAnchor.attachChild(logoPicture);
        logoSpinAnchor.attachChild(fallbackLogoText);
        logoAnchor.attachChild(logoSpinAnchor);
        root.attachChild(logoAnchor);

        root.attachChild(wordmarkPicture);
        root.attachChild(fallbackWordmarkText);
        root.attachChild(subtitleText);
        root.attachChild(progressTrack);
        root.attachChild(progressFill);
        root.attachChild(progressShine);
        root.attachChild(stageText);
        root.attachChild(percentText);
    }

    private void attachIfNeeded() {
        if (root.getParent() == null) {
            guiNode.attachChild(root);
        }
    }

    private void updateModel(UiScreenModel model) {
        UiScreenModel safeModel = model == null ? UiScreenModel.loading("Loading FrozenLands runtime", 0f) : model;
        currentStage = safeModel.stage().isBlank() ? "Loading FrozenLands runtime" : safeModel.stage();
        targetProgress = Math.max(targetProgress, clamp(safeModel.progress()));
        stageText.setText(currentStage);
    }

    private void layoutSurface(int width, int height) {
        int safeWidth = Math.round(Math.max(MIN_WIDTH, width));
        int safeHeight = Math.round(Math.max(MIN_HEIGHT, height));
        if (safeWidth == lastWidth && safeHeight == lastHeight) {
            return;
        }
        lastWidth = safeWidth;
        lastHeight = safeHeight;

        float screenWidth = safeWidth;
        float screenHeight = safeHeight;
        float cardWidth = Math.min(CARD_WIDTH, screenWidth - 72f);
        float cardHeight = Math.min(CARD_HEIGHT, screenHeight - 72f);
        float cardX = (screenWidth - cardWidth) * 0.5f;
        float cardY = (screenHeight - cardHeight) * 0.5f;
        float centerX = screenWidth * 0.5f;
        float top = cardY + cardHeight;

        resize(background, screenWidth, screenHeight);
        background.setLocalTranslation(0f, 0f, 0f);

        resize(auroraLeft, 340f, 340f);
        resize(auroraRight, 300f, 300f);
        auroraLeft.setLocalTranslation(cardX - 150f, top - 160f, 1f);
        auroraRight.setLocalTranslation(cardX + cardWidth - 130f, cardY - 80f, 1f);

        layoutDrift(driftA, screenWidth, cardY + cardHeight * 0.78f, 3f);
        layoutDrift(driftB, screenWidth, cardY + cardHeight * 0.48f, 4f);
        layoutDrift(driftC, screenWidth, cardY + cardHeight * 0.26f, 5f);

        resize(card, cardWidth, cardHeight);
        card.setLocalTranslation(cardX, cardY, 10f);
        resize(cardBorderTop, cardWidth, 1.5f);
        resize(cardBorderBottom, cardWidth, 1.5f);
        cardBorderTop.setLocalTranslation(cardX, top - 1.5f, 11f);
        cardBorderBottom.setLocalTranslation(cardX, cardY, 11f);

        float logoCenterY = top - 132f;
        resize(logoAuraA, LOGO_SIZE + 64f, LOGO_SIZE + 64f);
        resize(logoAuraB, LOGO_SIZE + 34f, LOGO_SIZE + 34f);
        resize(logoFrame, LOGO_SIZE + 22f, LOGO_SIZE + 22f);
        logoAuraA.setLocalTranslation(centerX - (LOGO_SIZE + 64f) * 0.5f, logoCenterY - (LOGO_SIZE + 64f) * 0.5f, 12f);
        logoAuraB.setLocalTranslation(centerX - (LOGO_SIZE + 34f) * 0.5f, logoCenterY - (LOGO_SIZE + 34f) * 0.5f, 13f);
        logoFrame.setLocalTranslation(centerX - (LOGO_SIZE + 22f) * 0.5f, logoCenterY - (LOGO_SIZE + 22f) * 0.5f, 14f);

        logoAnchor.setLocalTranslation(centerX, logoCenterY, 20f);
        logoPicture.setWidth(LOGO_SIZE);
        logoPicture.setHeight(LOGO_SIZE);
        logoPicture.setLocalTranslation(-LOGO_SIZE * 0.5f, -LOGO_SIZE * 0.5f, 0f);
        fallbackLogoText.setLocalTranslation(-fallbackLogoText.getLineWidth() * 0.5f, fallbackLogoText.getLineHeight() * 0.5f, 1f);
        fallbackLogoText.setCullHint(logoTexture == null ? Spatial.CullHint.Never : Spatial.CullHint.Always);

        float wordmarkHeight = wordmarkHeightForWidth(WORDMARK_WIDTH);
        float wordmarkY = logoCenterY - LOGO_SIZE * 0.5f - wordmarkHeight - 20f;
        wordmarkPicture.setWidth(WORDMARK_WIDTH);
        wordmarkPicture.setHeight(wordmarkHeight);
        wordmarkPicture.setLocalTranslation(centerX - WORDMARK_WIDTH * 0.5f, wordmarkY, 20f);
        wordmarkPicture.setCullHint(wordmarkTexture == null ? Spatial.CullHint.Always : Spatial.CullHint.Never);
        fallbackWordmarkText.setLocalTranslation(centerX - fallbackWordmarkText.getLineWidth() * 0.5f, wordmarkY + 36f, 20f);
        fallbackWordmarkText.setCullHint(wordmarkTexture == null ? Spatial.CullHint.Never : Spatial.CullHint.Always);

        subtitleText.setLocalTranslation(centerX - subtitleText.getLineWidth() * 0.5f, wordmarkY - 10f, 20f);

        barWidth = Math.min(BAR_WIDTH, cardWidth - 150f);
        barHeight = BAR_HEIGHT;
        barX = centerX - barWidth * 0.5f;
        barY = wordmarkY - 56f;
        resize(progressTrack, barWidth, barHeight);
        resize(progressFill, Math.max(1f, barWidth * displayedProgress), barHeight);
        resize(progressShine, 92f, barHeight);
        progressTrack.setLocalTranslation(barX, barY, 20f);
        progressFill.setLocalTranslation(barX, barY, 21f);
        progressShine.setLocalTranslation(barX, barY, 22f);

        stageText.setLocalTranslation(cardX + 48f, barY - 36f, 20f);
        percentText.setLocalTranslation(barX + barWidth + 14f, barY + 13f, 20f);
    }

    private void layoutDrift(Geometry drift, float screenWidth, float y, float z) {
        resize(drift, Math.min(190f, screenWidth * 0.18f), 1.2f);
        drift.setLocalTranslation(-260f, y, z);
    }

    private void animate(long nowNanos) {
        if (visibleSinceNanos <= 0L) {
            visibleSinceNanos = nowNanos;
        }
        if (lastFrameNanos <= 0L) {
            lastFrameNanos = nowNanos;
        }
        float time = (nowNanos - visibleSinceNanos) / 1_000_000_000f;
        float tpf = Math.min(0.10f, Math.max(0.001f, (nowNanos - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = nowNanos;

        float fade = clamp(time / 0.42f);
        float easedFade = easeOutCubic(fade);
        applyFade(easedFade);

        displayedProgress += (targetProgress - displayedProgress) * Math.min(1f, tpf * 7.5f);
        resize(progressFill, Math.max(1f, barWidth * displayedProgress), barHeight);

        int percent = Math.round(displayedProgress * 100f);
        if (percent != lastPercent) {
            lastPercent = percent;
            percentText.setText(String.format(Locale.ROOT, "%d%%", percent));
        }

        float logoScale = 1f + (float) Math.sin(time * 2.35f) * 0.025f;
        logoAnchor.setLocalScale(logoScale, logoScale, 1f);
        logoSpinAnchor.setLocalRotation(logoRotation.fromAngleAxis(time * 0.92f, Vector3f.UNIT_Z));

        float auraScaleA = 1f + (float) Math.sin(time * 1.75f) * 0.035f;
        float auraScaleB = 1f + (float) Math.cos(time * 1.45f) * 0.026f;
        logoAuraA.setLocalScale(auraScaleA, auraScaleA, 1f);
        logoAuraB.setLocalScale(auraScaleB, auraScaleB, 1f);
        logoAuraB.setLocalRotation(auraRotation.fromAngleAxis(-time * 0.42f, Vector3f.UNIT_Z));

        float wordmarkPulse = 1f + (float) Math.sin(time * 3.1f) * 0.012f;
        wordmarkPicture.setLocalScale(wordmarkPulse, wordmarkPulse, 1f);
        fallbackWordmarkText.setLocalScale(wordmarkPulse, wordmarkPulse, 1f);

        float shineTravel = (time * 210f) % (barWidth + 120f);
        progressShine.setLocalTranslation(barX - 100f + shineTravel, barY, 22f);

        animateDrift(driftA, time, 0.0f, 5.4f);
        animateDrift(driftB, time, 1.7f, 6.1f);
        animateDrift(driftC, time, 3.1f, 5.8f);
    }

    private void animateDrift(Geometry drift, float time, float delay, float duration) {
        float width = Math.max(MIN_WIDTH, lastWidth);
        float phase = ((time + delay) % duration) / duration;
        float x = -260f + (width + 520f) * phase;
        float yPulse = (float) Math.sin((time + delay) * 1.7f) * 6f;
        Vector3f translation = drift.getLocalTranslation();
        drift.setLocalTranslation(x, translation.y + yPulse * 0.02f, translation.z);
    }

    private void applyFade(float alpha) {
        setColor(background, BACKGROUND, alpha);
        setColor(auroraLeft, AURORA, alpha);
        setColor(auroraRight, AURORA, alpha);
        setColor(driftA, DRIFT, alpha);
        setColor(driftB, DRIFT, alpha);
        setColor(driftC, DRIFT, alpha);
        setColor(card, CARD, alpha);
        setColor(cardBorderTop, CARD_BORDER, alpha);
        setColor(cardBorderBottom, CARD_BORDER, alpha);
        setColor(logoAuraA, LOGO_AURA, alpha);
        setColor(logoAuraB, LOGO_AURA, alpha);
        setColor(logoFrame, LOGO_FRAME, alpha);
        setColor(progressTrack, TRACK, alpha);
        setColor(progressFill, FILL, alpha);
        setColor(progressShine, SHINE, alpha);
        stageText.setColor(withAlpha(TEXT, alpha));
        percentText.setColor(withAlpha(TEXT, alpha));
        subtitleText.setColor(withAlpha(MUTED, alpha));
        fallbackLogoText.setColor(withAlpha(TEXT, alpha));
        fallbackWordmarkText.setColor(withAlpha(TEXT, alpha));
    }

    private Geometry rectangle(String name, ColorRGBA color) {
        Geometry geometry = new Geometry(name, new Quad(1f, 1f));
        geometry.setQueueBucket(RenderQueue.Bucket.Gui);
        geometry.setMaterial(material(color));
        return geometry;
    }

    private Material material(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(color.r, color.g, color.b, color.a));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    private void setColor(Geometry geometry, ColorRGBA base, float alpha) {
        Material material = geometry.getMaterial();
        material.setColor("Color", new ColorRGBA(base.r, base.g, base.b, base.a * alpha));
    }

    private void resize(Geometry geometry, float width, float height) {
        geometry.setMesh(new Quad(Math.max(1f, width), Math.max(1f, height)));
    }

    private BitmapText text(BitmapFont font, String value, float size, ColorRGBA color) {
        BitmapText text = new BitmapText(font);
        text.setQueueBucket(RenderQueue.Bucket.Gui);
        text.setText(value);
        text.setSize(size);
        text.setColor(color);
        return text;
    }

    private void configurePicture(Picture picture, TextureResource resource) {
        picture.setQueueBucket(RenderQueue.Bucket.Gui);
        if (resource == null) {
            picture.setCullHint(Spatial.CullHint.Always);
            return;
        }
        picture.setTexture(assetManager, resource.texture, true);
        picture.setCullHint(Spatial.CullHint.Never);
    }

    private float wordmarkHeightForWidth(float width) {
        if (wordmarkTexture == null || wordmarkTexture.width <= 0) {
            return 56f;
        }
        return width * wordmarkTexture.height / wordmarkTexture.width;
    }

    private TextureResource loadTextureResource(String resourcePath) {
        try (InputStream stream = openResource(resourcePath)) {
            if (stream == null) {
                return null;
            }
            BufferedImage bufferedImage = ImageIO.read(stream);
            if (bufferedImage == null) {
                return null;
            }
            Image image = new AWTLoader().load(bufferedImage, true);
            Texture2D texture = new Texture2D(image);
            texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            return new TextureResource(texture, bufferedImage.getWidth(), bufferedImage.getHeight());
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private InputStream openResource(String resourcePath) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = StartupLoadingScreen.class.getClassLoader();
        }
        InputStream classpathStream = loader.getResourceAsStream(resourcePath);
        if (classpathStream != null) {
            return classpathStream;
        }

        Path direct = Path.of(resourcePath);
        if (Files.isRegularFile(direct)) {
            return Files.newInputStream(direct);
        }

        Path coreResources = Path.of("core", "src", "main", "resources").resolve(resourcePath);
        if (Files.isRegularFile(coreResources)) {
            return Files.newInputStream(coreResources);
        }

        Path appResources = Path.of("frozenlands-app", "src", "main", "resources").resolve(resourcePath);
        if (Files.isRegularFile(appResources)) {
            return Files.newInputStream(appResources);
        }
        return null;
    }

    private static ColorRGBA withAlpha(ColorRGBA color, float alpha) {
        return new ColorRGBA(color.r, color.g, color.b, color.a * alpha);
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static float easeOutCubic(float value) {
        float clamped = clamp(value);
        float inverse = 1f - clamped;
        return 1f - inverse * inverse * inverse;
    }

    private static final class TextureResource {
        private final Texture2D texture;
        private final float width;
        private final float height;

        private TextureResource(Texture2D texture, float width, float height) {
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }
}
