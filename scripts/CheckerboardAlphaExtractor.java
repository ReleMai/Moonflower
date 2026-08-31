import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts ImageGen's baked neutral checkerboard into real alpha without
 * selecting similarly light, colored artwork such as linen or moonflowers.
 */
public final class CheckerboardAlphaExtractor {
    private static final int MIN_BACKGROUND = 210;
    private static final int MAX_BACKGROUND_CHROMA = 12;

    private CheckerboardAlphaExtractor() {
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 2)
            throw new IllegalArgumentException("Usage: probe|components <input> OR extract <input> <output> [minimum-component]");
        BufferedImage source = ImageIO.read(new File(args[1]));
        if(source == null)
            throw new IllegalArgumentException("Unreadable image: " + args[1]);
        switch(args[0]) {
            case "copy":
                if(args.length < 3)
                    throw new IllegalArgumentException("copy requires an output path");
                writePng(source, new File(args[2]));
                break;
            case "probe":
                probe(source);
                break;
            case "components":
                components(source).stream().sorted(Comparator.comparingInt((Component c) -> c.count).reversed())
                        .limit(40).forEach(System.out::println);
                break;
            case "extract":
                if(args.length < 3)
                    throw new IllegalArgumentException("extract requires an output path");
                int minimumComponent = (args.length > 3) ? Integer.parseInt(args[3]) : 800;
                extract(source, new File(args[2]), minimumComponent);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
    }

    private static void writePng(BufferedImage source, File output) throws Exception {
        File parent = output.getParentFile();
        if(parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IllegalStateException("Could not create output directory: " + parent);
        if(!ImageIO.write(source, "png", output))
            throw new IllegalStateException("PNG writer unavailable");
        System.out.printf("Wrote %dx%d RGBA image to %s%n", source.getWidth(), source.getHeight(), output);
    }

    private static void probe(BufferedImage source) {
        Map<Integer, Integer> counts = new HashMap<>();
        for(int y = 0; y < source.getHeight(); y++) {
            for(int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                if(isBackgroundCandidate(rgb))
                    counts.merge(rgb & 0xffffff, 1, Integer::sum);
            }
        }
        counts.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(30).forEach(entry -> System.out.printf("#%06x %d%n", entry.getKey(), entry.getValue()));
    }

    private static List<Component> components(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean[] candidate = new boolean[width * height];
        boolean[] visited = new boolean[candidate.length];
        int[] queue = new int[candidate.length];
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++)
                candidate[(y * width) + x] = isBackgroundCandidate(source.getRGB(x, y));
        }
        List<Component> components = new ArrayList<>();
        for(int start = 0; start < candidate.length; start++) {
            if(!candidate[start] || visited[start])
                continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            Component component = new Component();
            while(head < tail) {
                int point = queue[head++];
                int x = point % width;
                int y = point / width;
                component.add(x, y, point, width, height);
                if(x > 0)
                    tail = enqueue(point - 1, candidate, visited, queue, tail);
                if(x + 1 < width)
                    tail = enqueue(point + 1, candidate, visited, queue, tail);
                if(y > 0)
                    tail = enqueue(point - width, candidate, visited, queue, tail);
                if(y + 1 < height)
                    tail = enqueue(point + width, candidate, visited, queue, tail);
            }
            component.points = new int[tail];
            System.arraycopy(queue, 0, component.points, 0, tail);
            components.add(component);
        }
        return components;
    }

    private static int enqueue(int point, boolean[] candidate, boolean[] visited, int[] queue, int tail) {
        if(!visited[point]) {
            visited[point] = true;
            if(candidate[point])
                queue[tail++] = point;
        }
        return tail;
    }

    private static void extract(BufferedImage source, File output, int minimumComponent) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        boolean[] transparent = new boolean[width * height];
        int removedComponents = 0;
        for(Component component : components(source)) {
            if(!component.touchesBorder && component.count < minimumComponent)
                continue;
            removedComponents++;
            for(int point : component.points)
                transparent[point] = true;
        }

        for(int point = 0; point < transparent.length; point++) {
            if(transparent[point]) {
                int x = point % width;
                int y = point / width;
                result.setRGB(x, y, result.getRGB(x, y) & 0x00ffffff);
            }
        }

        /* Feather only pixels directly touching extracted background. This
         * suppresses the pale checkerboard fringe while leaving internal iron,
         * linen, parchment, and flower highlights fully opaque. */
        for(int pass = 0; pass < 2; pass++) {
            boolean[] next = transparent.clone();
            for(int y = 1; y + 1 < height; y++) {
                for(int x = 1; x + 1 < width; x++) {
                    int point = (y * width) + x;
                    if(transparent[point] || !touchesTransparent(transparent, point, width))
                        continue;
                    int rgb = result.getRGB(x, y);
                    int red = (rgb >>> 16) & 0xff;
                    int green = (rgb >>> 8) & 0xff;
                    int blue = rgb & 0xff;
                    int minimum = Math.min(red, Math.min(green, blue));
                    if(minimum < 150)
                        continue;
                    int alpha = Math.max(0, Math.min(255, ((230 - minimum) * 255) / 80));
                    if(alpha < 224) {
                        result.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
                        if(alpha < 24)
                            next[point] = true;
                    }
                }
            }
            transparent = next;
        }

        File parent = output.getAbsoluteFile().getParentFile();
        if(parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IllegalStateException("Could not create output directory: " + parent);
        ImageIO.write(result, "png", output);
        System.out.printf("Wrote RGBA image %s (%dx%d); removed %d checkerboard components.%n",
                output, width, height, removedComponents);
    }

    private static boolean touchesTransparent(boolean[] transparent, int point, int width) {
        return transparent[point - 1] || transparent[point + 1] ||
                transparent[point - width] || transparent[point + width];
    }

    private static boolean isBackgroundCandidate(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        return minimum >= MIN_BACKGROUND && maximum - minimum <= MAX_BACKGROUND_CHROMA;
    }

    private static final class Component {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count;
        boolean touchesBorder;
        int[] points;

        void add(int x, int y, int point, int width, int height) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            count++;
            touchesBorder |= x == 0 || y == 0 || x + 1 == width || y + 1 == height;
        }

        public String toString() {
            return String.format("count=%d bounds=%d,%d-%d,%d border=%s",
                    count, minX, minY, maxX, maxY, touchesBorder);
        }
    }
}
