package haven.fishing;

import haven.Coord;
import haven.GOut;
import haven.Indir;
import haven.Loading;
import haven.MoonFlowerHudSettings;
import haven.Resource;
import haven.Tex;
import haven.UI;
import haven.WItem;

import java.awt.Color;
import java.util.Locale;

/** Shared drawing and motion helpers for the Tideglass presentation widgets. */
final class FishingNavigatorUi {
    private FishingNavigatorUi() {
    }

    static boolean reducedMotion() {
        return(MoonFlowerHudSettings.hudReducedMotion());
    }

    static double smooth(double value) {
        double clipped = Math.max(0.0, Math.min(1.0, value));
        return(clipped * clipped * (3.0 - 2.0 * clipped));
    }

    static String shortText(String value, int limit) {
        String cleaned = safe(value);
        return(cleaned.length() <= limit ? cleaned :
                cleaned.substring(0, Math.max(1, limit - 3)) + "...");
    }

    static String safe(String value) {
        if(value == null)
            return("");
        return(value.replace('\n', ' ').replace('\r', ' '));
    }

    static String waterLabel(String resource) {
        String normalized = resource == null ? "" : resource.toLowerCase(Locale.ROOT);
        if(normalized.contains("/owater") || normalized.contains("/odeep") || normalized.contains("ocean"))
            return("Ocean water");
        if(normalized.contains("water"))
            return("Fresh water");
        return("Water type unavailable");
    }

    static void drawIcon(GOut g, Indir<Resource> resource, Coord center, int size) {
        Tex texture;
        try {
            texture = resource.get().flayer(Resource.imgc).tex();
        } catch(Loading loading) {
            return;
        } catch(RuntimeException failure) {
            texture = WItem.missing.flayer(Resource.imgc).tex();
        }
        g.image(texture, center.sub(size / 2, size / 2), Coord.of(size, size));
    }

    static void ring(GOut g, Coord center, int radius, Color color, int alpha) {
        g.chcolor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha))));
        int segments = 40;
        for(int index = 0; index < segments; index++) {
            double first = (Math.PI * 2.0 * index) / segments;
            double second = (Math.PI * 2.0 * (index + 1)) / segments;
            g.line(center.add((int)Math.round(Math.cos(first) * radius),
                            (int)Math.round(Math.sin(first) * radius)),
                    center.add((int)Math.round(Math.cos(second) * radius),
                            (int)Math.round(Math.sin(second) * radius)), Math.max(1, UI.scale(1)));
        }
        g.chcolor();
    }

    static void circleFill(GOut g, Coord center, int radius) {
        for(int y = -radius; y <= radius; y++) {
            int span = (int)Math.sqrt(Math.max(0, radius * radius - y * y));
            g.line(center.add(-span, y), center.add(span, y), 1);
        }
    }
}
