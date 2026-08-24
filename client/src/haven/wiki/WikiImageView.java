package haven.wiki;

import haven.CharWnd;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.PUtils;
import haven.Tex;
import haven.TexI;
import haven.UI;
import haven.Widget;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Displays one safely downloaded wiki image without blocking the UI thread. */
final class WikiImageView extends Widget {
    private final RingOfBrodgarWikiService service;
    private Future<BufferedImage> pending;
    private BufferedImage source;
    private Tex texture;
    private String message = "";

    WikiImageView(RingOfBrodgarWikiService service, Coord size) {
        super(size);
        this.service = service;
    }

    void setImage(URI uri) {
        pending = null;
        source = null;
        disposeTexture();
        if(uri == null) {
            message = "";
            return;
        }
        message = "Loading article image...";
        pending = service.image(uri);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(pending == null || !pending.isDone())
            return;
        try {
            source = pending.get();
            message = "";
            rebuildTexture();
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            message = "Image loading was interrupted.";
        } catch(ExecutionException failure) {
            Throwable cause = failure.getCause();
            message = cause == null || cause.getMessage() == null ?
                    "Could not load this image." : cause.getMessage();
        } finally {
            pending = null;
        }
    }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        rebuildTexture();
    }

    private void rebuildTexture() {
        disposeTexture();
        if(source == null || sz.x <= 0 || sz.y <= 0)
            return;
        int availableWidth = Math.max(1, sz.x - UI.scale(12));
        int availableHeight = Math.max(1, sz.y - UI.scale(12));
        double scale = Math.min(1.0, Math.min((double)availableWidth / source.getWidth(),
                (double)availableHeight / source.getHeight()));
        Coord sourceSize = Coord.of(source.getWidth(), source.getHeight());
        Coord target = Coord.of(Math.max(1, (int)Math.round(source.getWidth() * scale)),
                Math.max(1, (int)Math.round(source.getHeight() * scale)));
        BufferedImage displayed = target.equals(sourceSize) ? source :
                PUtils.convolvedown(source, target, CharWnd.iconfilter);
        texture = new TexI(displayed);
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(new Color(9, 15, 22, 230));
        g.frect(Coord.z, sz);
        g.chcolor(new Color(92, 77, 45, 210));
        g.rect(Coord.z, sz.sub(1, 1));
        g.chcolor();
        if(texture != null)
            g.aimage(texture, sz.div(2), 0.5, 0.5);
        else if(!message.isBlank())
            FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5, message);
        super.draw(g);
    }

    private void disposeTexture() {
        if(texture != null) {
            texture.dispose();
            texture = null;
        }
    }

    @Override
    public void dispose() {
        disposeTexture();
        super.dispose();
    }
}
