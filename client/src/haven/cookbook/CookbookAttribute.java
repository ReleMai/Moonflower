package haven.cookbook;

import haven.Indir;
import haven.Resource;
import haven.Text;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Stable attribute names, native client icons, and cookbook presentation colors. */
public enum CookbookAttribute {
    ALL_RECIPES("All recipes", "gfx/hud/chr/cooking", new Color(230, 196, 116)),
    // These are the native FoodMeter event colors after the same 50% white blend
    // used by FoodInfo and the character-sheet FEP meter.
    STRENGTH("Strength", "gfx/hud/chr/fev/str", new Color(190, 150, 147)),
    AGILITY("Agility", "gfx/hud/chr/fev/agi", new Color(152, 148, 183)),
    INTELLIGENCE("Intelligence", "gfx/hud/chr/fev/int", new Color(156, 182, 184)),
    CONSTITUTION("Constitution", "gfx/hud/chr/fev/con", new Color(193, 153, 179)),
    PERCEPTION("Perception", "gfx/hud/chr/fev/prc", new Color(227, 190, 151)),
    CHARISMA("Charisma", "gfx/hud/chr/fev/csm", new Color(154, 237, 176)),
    DEXTERITY("Dexterity", "gfx/hud/chr/fev/dex", new Color(253, 252, 203)),
    // The live resource has the native Will event artwork but, unlike the other
    // food-event resources, does not publish a FoodInfo.Event color layer.
    WILL("Will", "gfx/hud/chr/fev/wil", new Color(169, 135, 235)),
    PSYCHE("Psyche", "gfx/hud/chr/fev/psy", new Color(195, 140, 252));

    public static final List<CookbookAttribute> ALL =
            Collections.unmodifiableList(Arrays.asList(values()));

    public final String label;
    public final String resourceName;
    public final Color color;
    private final Indir<Resource> resource;
    private final Text.Foundry font;

    CookbookAttribute(String label, String resourceName, Color color) {
        this.label = label;
        this.resourceName = resourceName;
        this.color = color;
        this.resource = Resource.remote().load(resourceName);
        this.font = new Text.Foundry(Text.sans, 11, color).aa(true);
    }

    public Indir<Resource> resource() {
        return(resource);
    }

    public BufferedImage icon() {
        return(resource.get().flayer(Resource.imgc).img);
    }

    public Text.Foundry font() {
        return(font);
    }

    public boolean allRecipes() {
        return(this == ALL_RECIPES);
    }

    public boolean matches(String eventName) {
        if(allRecipes() || eventName == null)
            return(false);
        String normalized = eventName.trim().toLowerCase(Locale.ROOT);
        String base = label.toLowerCase(Locale.ROOT);
        return(normalized.equals(base) || normalized.startsWith(base + " ") ||
                normalized.startsWith(base + "+"));
    }

    public static CookbookAttribute forEvent(String eventName) {
        for(CookbookAttribute attribute : values()) {
            if(attribute.matches(eventName))
                return(attribute);
        }
        return(null);
    }
}
