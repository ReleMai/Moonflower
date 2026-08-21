package haven.botcontrol;

import haven.Coord;
import haven.Loading;
import haven.PUtils;
import haven.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IconPackExporter {
    private static final Coord OUTPUT_SIZE = Coord.of(32, 32);
    private static final PUtils.Convolution ICON_FILTER = new PUtils.Lanczos(3);
    private static final Map<String, String> ICON_RESOURCES = new LinkedHashMap<>();

    static {
        ICON_RESOURCES.put("str", "gfx/hud/chr/str");
        ICON_RESOURCES.put("agi", "gfx/hud/chr/agi");
        ICON_RESOURCES.put("int", "gfx/hud/chr/int");
        ICON_RESOURCES.put("con", "gfx/hud/chr/con");
        ICON_RESOURCES.put("prc", "gfx/hud/chr/prc");
        ICON_RESOURCES.put("csm", "gfx/hud/chr/csm");
        ICON_RESOURCES.put("dex", "gfx/hud/chr/dex");
        ICON_RESOURCES.put("wil", "gfx/hud/chr/wil");
        ICON_RESOURCES.put("psy", "gfx/hud/chr/psy");
        ICON_RESOURCES.put("unarmed", "gfx/hud/chr/unarmed");
        ICON_RESOURCES.put("melee", "gfx/hud/chr/melee");
        ICON_RESOURCES.put("ranged", "gfx/hud/chr/ranged");
        ICON_RESOURCES.put("explore", "gfx/hud/chr/explore");
        ICON_RESOURCES.put("stealth", "gfx/hud/chr/stealth");
        ICON_RESOURCES.put("sewing", "gfx/hud/chr/sewing");
        ICON_RESOURCES.put("smithing", "gfx/hud/chr/smithing");
        ICON_RESOURCES.put("masonry", "gfx/hud/chr/masonry");
        ICON_RESOURCES.put("carpentry", "gfx/hud/chr/carpentry");
        ICON_RESOURCES.put("cooking", "gfx/hud/chr/cooking");
        ICON_RESOURCES.put("farming", "gfx/hud/chr/farming");
        ICON_RESOURCES.put("survive", "gfx/hud/chr/survive");
        ICON_RESOURCES.put("lore", "gfx/hud/chr/lore");
        ICON_RESOURCES.put("swim", "gfx/hud/chr/swim");
        ICON_RESOURCES.put("mining", "gfx/hud/chr/mining");
        ICON_RESOURCES.put("hp", "gfx/hud/meter/hp");
        ICON_RESOURCES.put("stam", "gfx/hud/meter/stam");
        ICON_RESOURCES.put("nrj", "gfx/hud/meter/nrj");
        ICON_RESOURCES.put("character-sheet", "gfx/hud/rbtn-chr");
    }

    private IconPackExporter() {
    }

    public static void main(String[] args) throws Exception {
        Path outDir = args.length > 0 ? Path.of(args[0]) : Path.of("..", "web", "public", "game-icons", "character");
        System.setProperty("haven.gamedir", Path.of("").toAbsolutePath().normalize() + File.separator);
        Files.createDirectories(outDir);
        for (Map.Entry<String, String> entry : ICON_RESOURCES.entrySet()) {
            try {
                export(entry.getKey(), entry.getValue(), outDir.resolve(entry.getKey() + ".png"));
            } catch (RuntimeException | IOException ex) {
                System.err.println("Skipped " + entry.getKey() + " (" + entry.getValue() + "): " + ex.getMessage());
            }
        }
    }

    private static void export(String key, String resourceName, Path destination) throws IOException {
        Resource resource = Loading.waitfor(Resource.local().load(resourceName));
        Resource.Image image = resource.flayer(Resource.imgc);
        if (image == null || image.img == null) {
            throw new IOException("Missing image layer for " + resourceName);
        }
        BufferedImage scaled = PUtils.convolvedown(image.img, OUTPUT_SIZE, ICON_FILTER);
        ImageIO.write(scaled, "png", destination.toFile());
        System.out.println("Exported " + key + " -> " + destination);
    }
}
