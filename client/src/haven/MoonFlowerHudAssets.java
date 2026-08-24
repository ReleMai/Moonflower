package haven;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loads the project-local artwork used by the MoonFlower portrait dock. */
public final class MoonFlowerHudAssets {
    private static final String ICON_ATLAS = "/haven/hud/moonflower-hud-icons-v3-alpha.png";
    private static final String DOCK_ORNAMENT = "/haven/hud/moonflower-dock-ornament-v3-alpha.png";
    private static final int ICON_COLUMNS = 5;
    private static final int ICON_ROWS = 2;
    private static final int ICON_COUNT = ICON_COLUMNS * ICON_ROWS;

    public static final BufferedImage dockOrnament = trimTransparent(load(DOCK_ORNAMENT), 4);
    public static final BufferedImage[] buttonIcons = sliceIconAtlas(load(ICON_ATLAS));
    public static final Coord[] socketCenters = findSocketCenters(dockOrnament);

    private MoonFlowerHudAssets() {
    }

    public static boolean complete() {
        if(dockOrnament.getWidth() <= 1 || dockOrnament.getHeight() <= 1 ||
                !hasTransparency(dockOrnament) || buttonIcons.length != ICON_COUNT || socketCenters.length != 6)
            return false;
        for(BufferedImage icon : buttonIcons) {
            if(icon == null || icon.getWidth() <= 1 || icon.getHeight() <= 1 || !hasTransparency(icon))
                return false;
        }
        return true;
    }

    public static Coord scaledSocketCenter(int index, Coord targetSize) {
        Coord source = socketCenters[index];
        return Coord.of((int)Math.round(source.x * (targetSize.x / (double)dockOrnament.getWidth())),
                (int)Math.round(source.y * (targetSize.y / (double)dockOrnament.getHeight())));
    }

    private static boolean hasTransparency(BufferedImage image) {
        if(!image.getColorModel().hasAlpha())
            return false;
        for(int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 20)) {
            for(int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 20)) {
                if(((image.getRGB(x, y) >>> 24) & 0xff) < 250)
                    return true;
            }
        }
        return false;
    }

    private static BufferedImage load(String path) {
        try(InputStream input = MoonFlowerHudAssets.class.getResourceAsStream(path)) {
            if(input == null)
                throw new IOException("Missing MoonFlower HUD artwork: " + path);
            BufferedImage image = ImageIO.read(input);
            if(image == null)
                throw new IOException("Unreadable MoonFlower HUD artwork: " + path);
            return image;
        } catch(IOException error) {
            System.err.println(error.getMessage());
            return TexI.mkbuf(Coord.of(1, 1));
        }
    }

    private static BufferedImage[] sliceIconAtlas(BufferedImage atlas) {
        BufferedImage[] icons = new BufferedImage[ICON_COUNT];
        if(atlas.getWidth() <= 1 || atlas.getHeight() <= 1) {
            for(int i = 0; i < icons.length; i++)
                icons[i] = TexI.mkbuf(Coord.of(1, 1));
            return icons;
        }

        for(int i = 0; i < icons.length; i++) {
            int column = i % ICON_COLUMNS;
            int row = i / ICON_COLUMNS;
            int x0 = (column * atlas.getWidth()) / ICON_COLUMNS;
            int y0 = (row * atlas.getHeight()) / ICON_ROWS;
            int x1 = ((column + 1) * atlas.getWidth()) / ICON_COLUMNS;
            int y1 = ((row + 1) * atlas.getHeight()) / ICON_ROWS;
            icons[i] = trimToSquare(atlas.getSubimage(x0, y0, x1 - x0, y1 - y0));
        }
        return icons;
    }

    private static BufferedImage trimToSquare(BufferedImage source) {
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

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        long alphaSum = 0;
        double centroidX = 0;
        double centroidY = 0;
        for(int y = minY; y <= maxY; y++) {
            for(int x = minX; x <= maxX; x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                alphaSum += alpha;
                centroidX += x * (double)alpha;
                centroidY += y * (double)alpha;
            }
        }
        centroidX = (alphaSum == 0) ? ((minX + maxX) / 2.0) : (centroidX / alphaSum);
        centroidY = (alphaSum == 0) ? ((minY + maxY) / 2.0) : (centroidY / alphaSum);
        int padding = Math.max(2, Math.max(width, height) / 24);
        double half = Math.max(Math.max(centroidX - minX, maxX - centroidX),
                Math.max(centroidY - minY, maxY - centroidY));
        int size = Math.max(Math.max(width, height) + (padding * 2),
                ((int)Math.ceil(half) + padding) * 2);
        BufferedImage result = TexI.mkbuf(Coord.of(size, size));
        Graphics2D graphics = result.createGraphics();
        try {
            int dx = (int)Math.round((size / 2.0) - (centroidX - minX));
            int dy = (int)Math.round((size / 2.0) - (centroidY - minY));
            graphics.drawImage(source, dx, dy, dx + width, dy + height,
                    minX, minY, maxX + 1, maxY + 1, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static Coord[] findSocketCenters(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] visited = new boolean[width * height];
        List<Component> candidates = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                int start = (y * width) + x;
                if(visited[start] || !isSocketInterior(image.getRGB(x, y)))
                    continue;
                visited[start] = true;
                queue.add(start);
                Component component = new Component();
                while(!queue.isEmpty()) {
                    int point = queue.removeFirst();
                    int px = point % width;
                    int py = point / width;
                    component.add(px, py);
                    if(px > 0)
                        enqueue(image, visited, queue, point - 1);
                    if(px + 1 < width)
                        enqueue(image, visited, queue, point + 1);
                    if(py > 0)
                        enqueue(image, visited, queue, point - width);
                    if(py + 1 < height)
                        enqueue(image, visited, queue, point + width);
                }
                int cw = component.maxX - component.minX + 1;
                int ch = component.maxY - component.minY + 1;
                double aspect = cw / (double)ch;
                double fill = component.count / (double)(cw * ch);
                if(cw >= 70 && cw <= 250 && ch >= 70 && ch <= 250 &&
                        aspect >= 0.72 && aspect <= 1.38 && fill >= 0.30 && component.count >= 2500)
                    candidates.add(component);
            }
        }
        candidates.sort(Comparator.comparingDouble(Component::centerX));
        if(candidates.size() != 6)
            return new Coord[] {Coord.of(164, 353), Coord.of(367, 425), Coord.of(434, 604),
                    Coord.of(1463, 604), Coord.of(1531, 425), Coord.of(1735, 353)};
        Coord[] centers = new Coord[6];
        for(int i = 0; i < centers.length; i++)
            centers[i] = candidates.get(i).center();
        return centers;
    }

    private static void enqueue(BufferedImage image, boolean[] visited, ArrayDeque<Integer> queue, int index) {
        if(visited[index])
            return;
        visited[index] = true;
        int x = index % image.getWidth();
        int y = index / image.getWidth();
        if(isSocketInterior(image.getRGB(x, y)))
            queue.add(index);
    }

    private static boolean isSocketInterior(int pixel) {
        int alpha = (pixel >>> 24) & 0xff;
        int red = (pixel >>> 16) & 0xff;
        int green = (pixel >>> 8) & 0xff;
        int blue = pixel & 0xff;
        return alpha >= 235 && red <= 20 && green >= 12 && green <= 65 &&
                blue >= 22 && blue <= 78 && blue >= green;
    }

    private static class Component {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        long sumX;
        long sumY;
        int count;

        void add(int x, int y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            sumX += x;
            sumY += y;
            count++;
        }

        double centerX() {
            return sumX / (double)count;
        }

        Coord center() {
            return Coord.of((int)Math.round(centerX()), (int)Math.round(sumY / (double)count));
        }
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
}
