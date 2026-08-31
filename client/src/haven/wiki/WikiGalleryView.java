package haven.wiki;

import haven.CharWnd;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.PUtils;
import haven.Tex;
import haven.TexI;
import haven.UI;
import haven.Widget;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Non-blocking thumbnail strip for MediaWiki article and gallery images. */
final class WikiGalleryView extends Widget {
    private static final int MAX_IMAGES = 6;
    private final RingOfBrodgarWikiService service;
    private final List<Future<BufferedImage>> pending = new ArrayList<>();
    private final List<Tex> textures = new ArrayList<>();
    private String message = "";

    WikiGalleryView(RingOfBrodgarWikiService service, Coord size) {
        super(size);
        this.service = service;
    }

    void setImage(BufferedImage image) {
        clear();
        if(image != null)
            addTexture(image);
    }

    void setImages(List<URI> images) {
        clear();
        if(images == null || images.isEmpty())
            return;
        int count = Math.min(MAX_IMAGES, images.size());
        message = count == 1 ? "Loading article image..." : "Loading gallery...";
        for(int i = 0; i < count; i++)
            pending.add(service.image(images.get(i)));
    }

    boolean hasContent() {return(!pending.isEmpty() || !textures.isEmpty() || !message.isBlank());}

    @Override
    public void tick(double dt) {
        super.tick(dt);
        for(int i = pending.size() - 1; i >= 0; i--) {
            Future<BufferedImage> future = pending.get(i);
            if(!future.isDone())
                continue;
            try {
                BufferedImage image = future.get();
                if(image != null)
                    addTexture(image);
            } catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                message = "Image loading was interrupted.";
            } catch(ExecutionException failure) {
                if(textures.isEmpty())
                    message = "Could not load gallery images.";
            }
            pending.remove(i);
        }
        if(pending.isEmpty() && !textures.isEmpty())
            message = "";
    }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        // Textures are rebuilt at draw time so resizing never blocks the UI thread.
    }

    @Override
    public void draw(GOut g) {
        MoonFlowerHudTheme.drawSlot(g, Coord.z, sz, !textures.isEmpty(), false);
        if(textures.isEmpty()) {
            if(!message.isBlank())
                FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5, message);
            super.draw(g);
            return;
        }
        int gap = UI.scale(6);
        int cellWidth = Math.max(UI.scale(42), (sz.x - gap * (textures.size() + 1)) / textures.size());
        int cellHeight = Math.max(UI.scale(42), sz.y - gap * 2);
        for(int i = 0; i < textures.size(); i++) {
            int x = gap + i * (cellWidth + gap);
            Coord cell = Coord.of(x, gap);
            MoonFlowerHudTheme.drawSlot(g, cell, Coord.of(cellWidth, cellHeight), true, i == 0);
            g.aimage(textures.get(i), Coord.of(x + cellWidth / 2, gap + cellHeight / 2), 0.5, 0.5);
        }
        super.draw(g);
    }

    private void addTexture(BufferedImage source) {
        int available = Math.max(1, Math.min(sz.x / Math.max(1, MAX_IMAGES), sz.y) - UI.scale(12));
        double scale = Math.min(1.0, available / (double)Math.max(source.getWidth(), source.getHeight()));
        Coord target = Coord.of(Math.max(1, (int)Math.round(source.getWidth() * scale)),
                Math.max(1, (int)Math.round(source.getHeight() * scale)));
        BufferedImage displayed = target.x == source.getWidth() && target.y == source.getHeight() ?
                source : PUtils.convolvedown(source, target, CharWnd.iconfilter);
        textures.add(new TexI(displayed));
    }

    private void clear() {
        for(Future<BufferedImage> future : pending)
            future.cancel(true);
        pending.clear();
        for(Tex texture : textures)
            texture.dispose();
        textures.clear();
        message = "";
    }

    @Override
    public void dispose() {
        clear();
        super.dispose();
    }
}
