package haven.wiki;

import haven.Coord;
import haven.PUtils;
import haven.Tex;
import haven.TexI;
import haven.UI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Project-local painted ornaments for the MoonFlower Codex. */
final class WikiUiAssets {
    private static final String CREST = "/haven/hud/moonflower-codex-crest-v1-alpha.png";
    private static final String ARCHIVE_RAIL = "/haven/hud/moonflower-codex-archive-rail-v1-alpha.png";
    private static final PUtils.Convolution FILTER = new PUtils.Lanczos(3);

    static final Tex crest = texture(load(CREST), UI.scale(188, 106));
    static final Tex archiveRail = texture(load(ARCHIVE_RAIL), UI.scale(30, 142));

    private WikiUiAssets() {
    }

    static boolean complete() {
        return(crest.sz().x > 1 && crest.sz().y > 1 &&
                archiveRail.sz().x > 1 && archiveRail.sz().y > 1);
    }

    private static Tex texture(BufferedImage image, Coord size) {
        if(image.getWidth() == size.x && image.getHeight() == size.y)
            return(new TexI(image));
        return(new TexI(PUtils.convolvedown(image, size, FILTER)));
    }

    private static BufferedImage load(String path) {
        try(InputStream input = WikiUiAssets.class.getResourceAsStream(path)) {
            if(input == null)
                throw(new IOException("Missing Codex artwork: " + path));
            BufferedImage image = ImageIO.read(input);
            if(image == null)
                throw(new IOException("Unreadable Codex artwork: " + path));
            return(image);
        } catch(IOException error) {
            System.err.println(error.getMessage());
            return(TexI.mkbuf(Coord.of(1, 1)));
        }
    }
}
