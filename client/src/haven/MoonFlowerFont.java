package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the optional Moonflower Display outline font and scales it per UI. */
public final class MoonFlowerFont {
    private static final String FONT_RELATIVE_PATH =
            "res/customclient/fonts/MoonflowerDisplay-Regular.ttf";
    private static final Object LOCK = new Object();
    private static volatile Font base;
    private static volatile boolean fallback;

    private MoonFlowerFont() {
    }

    /** Returns a display face at a design-space size, respecting the UI scale. */
    public static Font scaled(float designSize) {
        return(baseFont().deriveFont(UI.scale(designSize)));
    }

    /** Creates an antialiased text foundry for MoonFlower headings and labels. */
    public static Text.Foundry foundry(float designSize, Color color) {
        return(new Text.Foundry(scaled(designSize), color).aa(true));
    }

    /** Used by offline checks to prove the packaged TTF was actually loadable. */
    public static boolean bundled() {
        baseFont();
        return(!fallback);
    }

    private static Font baseFont() {
        Font loaded = base;
        if(loaded != null)
            return(loaded);
        synchronized(LOCK) {
            loaded = base;
            if(loaded != null)
                return(loaded);
            try(InputStream in = Files.newInputStream(fontPath())) {
                loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch(IOException | FontFormatException | RuntimeException e) {
                fallback = true;
                loaded = Text.serif;
                System.err.println("MoonFlower display font unavailable; using the built-in serif fallback.");
            }
            base = loaded;
            return(loaded);
        }
    }

    private static Path fontPath() {
        return(ClientInstall.directory().resolve(FONT_RELATIVE_PATH));
    }
}
