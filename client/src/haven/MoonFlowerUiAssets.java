package haven;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Project-local raster artwork shared by MoonFlower windows and HUD panels. */
public final class MoonFlowerUiAssets {
    private static final String WINDOW_FRAME = "/haven/hud/moonflower-window-frame-v1-alpha.png";
    private static final String PANEL_TEXTURE = "/haven/hud/moonflower-panel-texture-v1.png";
    private static final String CHAT_SETTINGS = "/haven/hud/moonflower-chat-settings-v1-alpha.png";
    private static final int FRAME_SLICE = 400;
    private static final PUtils.Convolution ART_FILTER = new PUtils.Lanczos(3);

    private static final BufferedImage windowFrameSource = load(WINDOW_FRAME);
    private static final BufferedImage chatSettingsSource = trimTransparent(load(CHAT_SETTINGS), 8);
    public static final Tex[] windowFrame = sliceFrame(windowFrameSource);
    public static final Tex panelTexture = filtered(load(PANEL_TEXTURE), Coord.of(UI.scale(256), UI.scale(256)));
    public static final Tex chatSettings = filtered(chatSettingsSource,
            Coord.of(UI.scale(24), UI.scale(24)));

    private MoonFlowerUiAssets() {
    }

    public static boolean complete() {
        if(windowFrame.length != 9 || panelTexture.sz().x <= 1 || chatSettings.sz().x <= 1 ||
                !hasTransparency(windowFrameSource) || !hasTransparency(chatSettingsSource))
            return false;
        for(Tex slice : windowFrame) {
            if(slice == null || slice.sz().x <= 0 || slice.sz().y <= 0)
                return false;
        }
        return true;
    }

    private static boolean hasTransparency(BufferedImage image) {
        if(!image.getColorModel().hasAlpha())
            return false;
        for(int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 32)) {
            for(int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 32)) {
                if(((image.getRGB(x, y) >>> 24) & 0xff) < 240)
                    return true;
            }
        }
        return false;
    }

    private static Tex[] sliceFrame(BufferedImage source) {
        int inset = Math.min(FRAME_SLICE, Math.min(source.getWidth(), source.getHeight()) / 3);
        int middleWidth = Math.max(1, source.getWidth() - (inset * 2));
        int middleHeight = Math.max(1, source.getHeight() - (inset * 2));
        int edge = UI.scale(24);
        int repeat = UI.scale(96);
        return new Tex[] {
                texture(source, 0, 0, inset, inset, Coord.of(edge, edge)),
                texture(source, inset, 0, middleWidth, inset, Coord.of(repeat, edge)),
                texture(source, source.getWidth() - inset, 0, inset, inset, Coord.of(edge, edge)),
                texture(source, 0, inset, inset, middleHeight, Coord.of(edge, repeat)),
                texture(source, inset, inset, middleWidth, middleHeight, Coord.of(1, 1)),
                texture(source, source.getWidth() - inset, inset, inset, middleHeight, Coord.of(edge, repeat)),
                texture(source, 0, source.getHeight() - inset, inset, inset, Coord.of(edge, edge)),
                texture(source, inset, source.getHeight() - inset, middleWidth, inset, Coord.of(repeat, edge)),
                texture(source, source.getWidth() - inset, source.getHeight() - inset, inset, inset, Coord.of(edge, edge))
        };
    }

    private static Tex texture(BufferedImage source, int x, int y, int width, int height, Coord targetSize) {
        BufferedImage copy = TexI.mkbuf(Coord.of(width, height));
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        } finally {
            graphics.dispose();
        }
        return filtered(copy, targetSize);
    }

    private static Tex filtered(BufferedImage image, Coord targetSize) {
        if(image.getWidth() == targetSize.x && image.getHeight() == targetSize.y)
            return new TexI(image);
        return new TexI(PUtils.convolvedown(image, targetSize, ART_FILTER));
    }

    private static BufferedImage trimTransparent(BufferedImage source, int padding) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
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
            return TexI.mkbuf(Coord.of(1, 1));
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
        return result;
    }

    private static BufferedImage load(String path) {
        try(InputStream input = MoonFlowerUiAssets.class.getResourceAsStream(path)) {
            if(input == null)
                throw new IOException("Missing MoonFlower UI artwork: " + path);
            BufferedImage image = ImageIO.read(input);
            if(image == null)
                throw new IOException("Unreadable MoonFlower UI artwork: " + path);
            return image;
        } catch(IOException error) {
            System.err.println(error.getMessage());
            return TexI.mkbuf(Coord.of(1, 1));
        }
    }
}
