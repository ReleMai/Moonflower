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
    private static final String ICON_ATLAS = "/haven/hud/moonflower-hud-icons-v4-alpha.png";
    private static final String MOVEMENT_ICON_ATLAS = "/haven/hud/moonflower-movement-icons-v1-alpha.png";
    private static final String DOCK_ORNAMENT = "/haven/hud/moonflower-dock-integrated-v4-alpha.png";
    private static final String COMBAT_CROWN = "/haven/hud/moonflower-combat-collar-v7-alpha.png";
    private static final int ICON_COLUMNS = 5;
    private static final int ICON_ROWS = 2;
    private static final int ICON_COUNT = ICON_COLUMNS * ICON_ROWS;

    public static final BufferedImage dockOrnament = trimTransparent(load(DOCK_ORNAMENT), 4);
    public static final BufferedImage combatCrown = trimTransparent(load(COMBAT_CROWN), 4);
    public static final BufferedImage[] buttonIcons = sliceIconAtlas(load(ICON_ATLAS));
    public static final BufferedImage[] movementIcons = sliceHorizontalAtlas(load(MOVEMENT_ICON_ATLAS), 4);
    private static final IntegratedGeometry geometry = findIntegratedGeometry(dockOrnament);
    public static final Coord portraitCenter = geometry.portraitCenter;
    public static final int portraitOpeningDiameter = geometry.portraitDiameter;
    public static final Coord[] socketCenters = geometry.mainSockets;
    public static final Coord[] buffSocketCenters = geometry.buffSockets;
    public static final Coord[] equipmentSlotCenters = geometry.equipmentSlots;
    public static final Coord[] movementSocketCenters = geometry.movementSockets;
    public static final Coord buffOverflowCenter = geometry.buffOverflowCenter;

    /* Source-space centers measured from the intentionally transparent wells
     * painted into moonflower-combat-collar-v7-alpha.png after its four-pixel
     * transparent trim. Keeping them with the asset prevents combat layout
     * code from drifting away from the artwork. */
    private static final Coord[] combatActionCenters = {
            Coord.of(308, 128), Coord.of(447, 142), Coord.of(254, 207), Coord.of(390, 215), Coord.of(228, 298),
            Coord.of(1190, 128), Coord.of(1051, 143), Coord.of(1244, 207), Coord.of(1108, 215), Coord.of(1270, 298)
    };
    private static final Coord[] combatPlayerOpeningCenters = {
            Coord.of(228, 534), Coord.of(285, 544), Coord.of(342, 553), Coord.of(399, 562)
    };
    private static final Coord[] combatOpponentOpeningCenters = {
            Coord.of(1271, 534), Coord.of(1214, 544), Coord.of(1157, 553), Coord.of(1100, 562)
    };
    private static final Coord combatPortraitCenter = Coord.of(751, 342);
    private static final Coord combatHealthOrigin = Coord.of(550, 10);
    private static final Coord combatHealthSize = Coord.of(400, 34);
    private static final Coord combatPlayerMoveCenter = Coord.of(105, 418);
    private static final Coord combatOpponentMoveCenter = Coord.of(1392, 419);
    private static final Coord combatPlayerDefenseCenter = Coord.of(355, 299);
    private static final Coord combatOpponentDefenseCenter = Coord.of(1142, 299);
    private static final Coord combatPlayerInitiativeCenter = Coord.of(355, 391);
    private static final Coord combatOpponentInitiativeCenter = Coord.of(1143, 391);
    private static final Coord combatCooldownCenter = Coord.of(751, 77);
    private static final int combatActionDiameter = 54;
    private static final int combatOpeningDiameter = 50;
    private static final int combatMoveDiameter = 76;
    private static final int combatDefenseDiameter = 56;
    private static final int combatInitiativeDiameter = 56;
    private static final int combatCooldownDiameter = 50;

    private MoonFlowerHudAssets() {
    }

    public static boolean complete() {
        if(dockOrnament.getWidth() <= 1 || dockOrnament.getHeight() <= 1 ||
                !hasTransparency(dockOrnament) || buttonIcons.length != ICON_COUNT ||
                movementIcons.length != 4 || socketCenters.length != 6 || buffSocketCenters.length != 4 ||
                equipmentSlotCenters.length != 6 || movementSocketCenters.length != 4 || portraitOpeningDiameter <= 1 ||
                combatCrown.getWidth() <= 1 || combatCrown.getHeight() <= 1 || !hasTransparency(combatCrown))
            return false;
        for(BufferedImage icon : buttonIcons) {
            if(icon == null || icon.getWidth() <= 1 || icon.getHeight() <= 1 || !hasTransparency(icon))
                return false;
        }
        for(BufferedImage icon : movementIcons) {
            if(icon == null || icon.getWidth() <= 1 || icon.getHeight() <= 1 || !hasTransparency(icon))
                return false;
        }
        return true;
    }

    public static Coord scaledSocketCenter(int index, Coord targetSize) {
        return scaledCenter(socketCenters[index], targetSize);
    }

    public static Coord scaledBuffSocketCenter(int index, Coord targetSize) {
        return scaledCenter(buffSocketCenters[index], targetSize);
    }

    public static Coord scaledEquipmentSlotCenter(int index, Coord targetSize) {
        return scaledCenter(equipmentSlotCenters[index], targetSize);
    }

    public static Coord scaledMovementSocketCenter(int index, Coord targetSize) {
        return scaledCenter(movementSocketCenters[index], targetSize);
    }

    public static Coord scaledPortraitCenter(Coord targetSize) {
        return scaledCenter(portraitCenter, targetSize);
    }

    public static Coord scaledBuffOverflowCenter(Coord targetSize) {
        return scaledCenter(buffOverflowCenter, targetSize);
    }

    public static Coord scaledCombatActionCenter(int index, Coord targetSize) {
        return scaledCombatPoint(combatActionCenters[index], targetSize);
    }

    public static Coord scaledCombatOpeningCenter(String resourceName, boolean opponent, Coord targetSize) {
        int index;
        if("paginae/atk/cornered".equals(resourceName))
            index = 0;
        else if("paginae/atk/offbalance".equals(resourceName))
            index = 1;
        else if("paginae/atk/dizzy".equals(resourceName))
            index = 2;
        else if("paginae/atk/reeling".equals(resourceName))
            index = 3;
        else
            return null;
        Coord[] centers = opponent ? combatOpponentOpeningCenters : combatPlayerOpeningCenters;
        return scaledCombatPoint(centers[index], targetSize);
    }

    public static Coord scaledCombatOpeningCenter(String resourceName, Coord targetSize) {
        return scaledCombatOpeningCenter(resourceName, true, targetSize);
    }

    public static Area scaledCombatHealthArea(Coord targetSize) {
        return Area.sized(scaledCombatPoint(combatHealthOrigin, targetSize),
                Coord.of(scaleCombatX(combatHealthSize.x, targetSize),
                        scaleCombatY(combatHealthSize.y, targetSize)));
    }

    public static Coord scaledCombatMoveCenter(boolean opponent, Coord targetSize) {
        return scaledCombatPoint(opponent ? combatOpponentMoveCenter : combatPlayerMoveCenter, targetSize);
    }

    public static Coord scaledCombatDefenseCenter(boolean opponent, Coord targetSize) {
        return scaledCombatPoint(opponent ? combatOpponentDefenseCenter : combatPlayerDefenseCenter, targetSize);
    }

    public static Coord scaledCombatInitiativeCenter(boolean opponent, Coord targetSize) {
        return scaledCombatPoint(opponent ? combatOpponentInitiativeCenter : combatPlayerInitiativeCenter, targetSize);
    }

    public static Coord scaledCombatCooldownCenter(Coord targetSize) {
        return scaledCombatPoint(combatCooldownCenter, targetSize);
    }

    public static Coord scaledCombatPortraitCenter(Coord targetSize) {
        return scaledCombatPoint(combatPortraitCenter, targetSize);
    }

    public static int scaledCombatActionDiameter(Coord targetSize) {
        return scaleCombatX(combatActionDiameter, targetSize);
    }

    public static int scaledCombatOpeningDiameter(Coord targetSize) {
        return scaleCombatX(combatOpeningDiameter, targetSize);
    }

    public static int scaledCombatMoveDiameter(Coord targetSize) {
        return scaleCombatX(combatMoveDiameter, targetSize);
    }

    public static int scaledCombatDefenseDiameter(Coord targetSize) {
        return scaleCombatX(combatDefenseDiameter, targetSize);
    }

    public static int scaledCombatInitiativeDiameter(Coord targetSize) {
        return scaleCombatX(combatInitiativeDiameter, targetSize);
    }

    public static int scaledCombatCooldownDiameter(Coord targetSize) {
        return scaleCombatX(combatCooldownDiameter, targetSize);
    }

    private static Coord scaledCenter(Coord source, Coord targetSize) {
        return Coord.of((int)Math.round(source.x * (targetSize.x / (double)dockOrnament.getWidth())),
                (int)Math.round(source.y * (targetSize.y / (double)dockOrnament.getHeight())));
    }

    private static Coord scaledCombatPoint(Coord source, Coord targetSize) {
        return Coord.of(scaleCombatX(source.x, targetSize), scaleCombatY(source.y, targetSize));
    }

    private static int scaleCombatX(int value, Coord targetSize) {
        return (int)Math.round(value * (targetSize.x / (double)combatCrown.getWidth()));
    }

    private static int scaleCombatY(int value, Coord targetSize) {
        return (int)Math.round(value * (targetSize.y / (double)combatCrown.getHeight()));
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

    private static BufferedImage[] sliceHorizontalAtlas(BufferedImage atlas, int count) {
        BufferedImage[] icons = new BufferedImage[count];
        if(atlas.getWidth() <= 1 || atlas.getHeight() <= 1) {
            for(int i = 0; i < count; i++)
                icons[i] = TexI.mkbuf(Coord.of(1, 1));
            return icons;
        }
        for(int i = 0; i < count; i++) {
            int x0 = (i * atlas.getWidth()) / count;
            int x1 = ((i + 1) * atlas.getWidth()) / count;
            icons[i] = trimToSquare(atlas.getSubimage(x0, 0, x1 - x0, atlas.getHeight()));
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

    private static IntegratedGeometry findIntegratedGeometry(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] visited = new boolean[width * height];
        List<Component> components = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                int start = (y * width) + x;
                if(visited[start] || !isTransparent(image.getRGB(x, y)))
                    continue;
                visited[start] = true;
                queue.add(start);
                Component component = new Component();
                while(!queue.isEmpty()) {
                    int point = queue.removeFirst();
                    int px = point % width;
                    int py = point / width;
                    component.add(px, py, width, height);
                    if(px > 0)
                        enqueue(image, visited, queue, point - 1);
                    if(px + 1 < width)
                        enqueue(image, visited, queue, point + 1);
                    if(py > 0)
                        enqueue(image, visited, queue, point - width);
                    if(py + 1 < height)
                        enqueue(image, visited, queue, point + width);
                }
                components.add(component);
            }
        }

        Component portrait = components.stream().filter(component -> !component.touchesBorder)
                .max(Comparator.comparingInt(component -> component.count)).orElse(null);
        List<Component> main = matching(components, component -> !component.touchesBorder &&
                component.width() >= width * 0.065 && component.width() <= width * 0.095 &&
                component.height() >= height * 0.11 && component.height() <= height * 0.17 &&
                component.center().y < height * 0.68);
        List<Component> buffs = matching(components, component -> !component.touchesBorder &&
                component.width() >= width * 0.04 && component.width() <= width * 0.065 &&
                component.height() >= height * 0.07 && component.height() <= height * 0.12 &&
                component.center().y >= height * 0.55 && component.center().y <= height * 0.76);
        List<Component> equipment = matching(components, component -> !component.touchesBorder &&
                component.center().x >= width * 0.25 && component.center().x <= width * 0.75 &&
                component.center().y >= height * 0.75 && component.width() >= width * 0.05 &&
                component.width() <= width * 0.075 && component.height() >= height * 0.09 &&
                component.height() <= height * 0.15);
        List<Component> movement = matching(components, component -> !component.touchesBorder &&
                component.center().x < width * 0.25 && component.center().y >= height * 0.72 &&
                component.width() >= width * 0.018 && component.width() <= width * 0.035 &&
                component.height() >= height * 0.03 && component.height() <= height * 0.065);

        Coord[] mainCenters = orderedMainCenters(main, portrait);
        Coord[] buffCenters = orderedCenters(buffs, 4);
        Coord[] equipmentCenters = orderedCenters(equipment, 6);
        movement.sort(Comparator.comparingInt((Component component) -> component.center().y)
                .thenComparingInt(component -> component.center().x));
        Coord[] movementCenters = (movement.size() == 4) ? centers(movement, 4) :
                new Coord[] {Coord.of(203, 744), Coord.of(272, 744), Coord.of(203, 811), Coord.of(272, 811)};
        Coord overflow = components.stream().filter(component -> !component.touchesBorder &&
                        component.center().x > width * 0.8 && component.center().y > height * 0.70 &&
                        component.width() > width * 0.06)
                .max(Comparator.comparingInt(component -> component.count))
                .map(Component::center).orElse(Coord.of((int)(width * 0.87), (int)(height * 0.81)));

        if(portrait == null)
            portrait = fallbackPortrait(width, height);
        return new IntegratedGeometry(portrait.center(), Math.min(portrait.width(), portrait.height()),
                mainCenters, buffCenters, equipmentCenters, movementCenters, overflow);
    }

    private static List<Component> matching(List<Component> components,
                                            java.util.function.Predicate<Component> predicate) {
        List<Component> matches = new ArrayList<>();
        for(Component component : components) {
            if(predicate.test(component))
                matches.add(component);
        }
        return matches;
    }

    private static Coord[] orderedMainCenters(List<Component> main, Component portrait) {
        if(main.size() != 6 || portrait == null)
            return new Coord[] {Coord.of(329, 285), Coord.of(196, 432), Coord.of(357, 538),
                    Coord.of(1320, 538), Coord.of(1483, 430), Coord.of(1352, 284)};
        List<Component> left = new ArrayList<>();
        List<Component> right = new ArrayList<>();
        for(Component component : main)
            (component.center().x < portrait.center().x ? left : right).add(component);
        if(left.size() != 3 || right.size() != 3)
            return orderedMainCenters(new ArrayList<>(), null);
        left.sort(Comparator.comparingInt(component -> component.center().y));
        right.sort(Comparator.comparingInt((Component component) -> component.center().y).reversed());
        Coord[] result = new Coord[6];
        for(int i = 0; i < 3; i++) {
            result[i] = left.get(i).center();
            result[i + 3] = right.get(i).center();
        }
        return result;
    }

    private static Coord[] orderedCenters(List<Component> components, int expected) {
        components.sort(Comparator.comparingInt(component -> component.center().x));
        return centers(components, expected);
    }

    private static Coord[] centers(List<Component> components, int expected) {
        if(components.size() != expected) {
            if(expected == 4)
                return new Coord[] {Coord.of(643, 628), Coord.of(771, 632), Coord.of(905, 632), Coord.of(1034, 628)};
            return new Coord[] {Coord.of(515, 795), Coord.of(650, 795), Coord.of(781, 795),
                    Coord.of(910, 795), Coord.of(1036, 795), Coord.of(1166, 795)};
        }
        Coord[] result = new Coord[expected];
        for(int i = 0; i < expected; i++)
            result[i] = components.get(i).center();
        return result;
    }

    private static Component fallbackPortrait(int width, int height) {
        Component portrait = new Component();
        portrait.minX = (int)(width * 0.392);
        portrait.maxX = (int)(width * 0.606);
        portrait.minY = (int)(height * 0.183);
        portrait.maxY = (int)(height * 0.575);
        portrait.count = 1;
        portrait.sumX = (portrait.minX + portrait.maxX) / 2;
        portrait.sumY = (portrait.minY + portrait.maxY) / 2;
        return portrait;
    }

    private static void enqueue(BufferedImage image, boolean[] visited, ArrayDeque<Integer> queue, int index) {
        if(visited[index])
            return;
        visited[index] = true;
        int x = index % image.getWidth();
        int y = index / image.getWidth();
        if(isTransparent(image.getRGB(x, y)))
            queue.add(index);
    }

    private static boolean isTransparent(int pixel) {
        return ((pixel >>> 24) & 0xff) <= 12;
    }

    private static class Component {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        long sumX;
        long sumY;
        int count;
        boolean touchesBorder;

        void add(int x, int y, int width, int height) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            sumX += x;
            sumY += y;
            count++;
            touchesBorder |= x == 0 || y == 0 || x + 1 == width || y + 1 == height;
        }

        double centerX() {
            return sumX / (double)count;
        }

        Coord center() {
            return Coord.of((int)Math.round(centerX()), (int)Math.round(sumY / (double)count));
        }

        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }

    private static class IntegratedGeometry {
        final Coord portraitCenter;
        final int portraitDiameter;
        final Coord[] mainSockets;
        final Coord[] buffSockets;
        final Coord[] equipmentSlots;
        final Coord[] movementSockets;
        final Coord buffOverflowCenter;

        IntegratedGeometry(Coord portraitCenter, int portraitDiameter, Coord[] mainSockets,
                           Coord[] buffSockets, Coord[] equipmentSlots, Coord[] movementSockets,
                           Coord buffOverflowCenter) {
            this.portraitCenter = portraitCenter;
            this.portraitDiameter = portraitDiameter;
            this.mainSockets = mainSockets;
            this.buffSockets = buffSockets;
            this.equipmentSlots = equipmentSlots;
            this.movementSockets = movementSockets;
            this.buffOverflowCenter = buffOverflowCenter;
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
