package haven.fishing;

import haven.Coord;
import haven.PUtils;
import haven.Tex;
import haven.TexI;
import haven.UI;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Production Tideglass artwork used by the fishing navigator. */
final class FishingNavigatorAssets {
    private static final String MARK = "/haven/hud/moonflower-fishing-tideglass-mark-v1-alpha.png";
    private static final String THREAD = "/haven/hud/moonflower-fishing-tackle-thread-v1-alpha.png";
    private static final String LOCATOR = "/haven/hud/moonflower-fishing-locator-ring-v1-alpha.png";
    private static final PUtils.Convolution FILTER = new PUtils.Lanczos(3);

    private static final BufferedImage tackleSource = load(THREAD);
    static final Tex tideglassMark = texture(trim(load(MARK)), UI.scale(68, 68));
    static final Tex[] tackleSockets = sliceSockets(tackleSource);
    static final Tex locatorRing = texture(trim(load(LOCATOR)), UI.scale(180, 180));

    private FishingNavigatorAssets() {
    }

    static boolean complete() {
        if(tideglassMark.sz().x <= 1 || locatorRing.sz().x <= 1 || tackleSockets.length != 4)
            return(false);
        for(Tex socket : tackleSockets) {
            if(socket == null || socket.sz().x <= 1)
                return(false);
        }
        return(true);
    }

    private static Tex[] sliceSockets(BufferedImage source) {
        Tex[] sockets = new Tex[4];
        if(source.getWidth() <= 1 || source.getHeight() <= 1) {
            for(int index = 0; index < sockets.length; index++)
                sockets[index] = new TexI(TexI.mkbuf(Coord.of(1, 1)));
            return(sockets);
        }
        for(int index = 0; index < sockets.length; index++) {
            int x0 = (index * source.getWidth()) / sockets.length;
            int x1 = ((index + 1) * source.getWidth()) / sockets.length;
            BufferedImage crop = copy(source.getSubimage(x0, 0, x1 - x0, source.getHeight()));
            sockets[index] = texture(trim(crop), UI.scale(88, 88));
        }
        return(sockets);
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = TexI.mkbuf(Coord.of(source.getWidth(), source.getHeight()));
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return(result);
    }

    private static Tex texture(BufferedImage source, Coord size) {
        if(source.getWidth() == size.x && source.getHeight() == size.y)
            return(new TexI(source));
        return(new TexI(PUtils.convolvedown(source, size, FILTER)));
    }

    private static BufferedImage trim(BufferedImage source) {
        int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
        for(int y = 0; y < source.getHeight(); y++) {
            for(int x = 0; x < source.getWidth(); x++) {
                if(((source.getRGB(x, y) >>> 24) & 0xff) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if(maxX < minX || maxY < minY)
            return(TexI.mkbuf(Coord.of(1, 1)));
        int padding = Math.max(2, Math.min(source.getWidth(), source.getHeight()) / 128);
        minX = Math.max(0, minX - padding);
        minY = Math.max(0, minY - padding);
        maxX = Math.min(source.getWidth() - 1, maxX + padding);
        maxY = Math.min(source.getHeight() - 1, maxY + padding);
        BufferedImage result = TexI.mkbuf(Coord.of(maxX - minX + 1, maxY - minY + 1));
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, -minX, -minY, null);
        } finally {
            graphics.dispose();
        }
        return(result);
    }

    private static BufferedImage load(String path) {
        try(InputStream input = FishingNavigatorAssets.class.getResourceAsStream(path)) {
            if(input == null)
                throw(new IOException("Missing fishing navigator artwork: " + path));
            BufferedImage image = ImageIO.read(input);
            if(image == null)
                throw(new IOException("Unreadable fishing navigator artwork: " + path));
            return(image);
        } catch(IOException error) {
            System.err.println(error.getMessage());
            return(TexI.mkbuf(Coord.of(1, 1)));
        }
    }
}
