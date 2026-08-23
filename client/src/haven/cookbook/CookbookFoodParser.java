package haven.cookbook;

import haven.ItemInfo;
import haven.Resource;
import haven.res.ui.tt.q.qbuff.QBuff;
import haven.resutil.FoodInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Converts resource-backed item tooltip objects into cookbook-owned data. */
final class CookbookFoodParser {
    private CookbookFoodParser() {
    }

    static CookbookItem parseItem(List<ItemInfo> info, Resource resource, String worldId,
                                  long observedAt) {
        if(resource == null)
            return(null);
        String itemName = itemName(info, resource);
        if(itemName.isBlank() || resource.name == null || resource.name.isBlank())
            return(null);
        return(new CookbookItem(worldId, itemName, resource.name, observedAt));
    }

    static CookbookFood parse(List<ItemInfo> info, Resource resource, String worldId,
                              String characterId, long observedAt) {
        FoodInfo foodInfo = ItemInfo.find(FoodInfo.class, info);
        if(foodInfo == null)
            return(null);

        String itemName = itemName(info, resource);
        QBuff qualityInfo = ItemInfo.find(QBuff.class, info);
        double quality = (qualityInfo == null || qualityInfo.q <= 0) ? 10d : qualityInfo.q;
        double fepQualityFactor = Math.sqrt(quality / 10d);
        double hungerQualityFactor = Math.sqrt(fepQualityFactor);

        List<CookbookFood.Ingredient> ingredients = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        for(ItemInfo itemInfo : info) {
            CookbookFood.Ingredient ingredient = parseIngredient(itemInfo);
            if(ingredient != null)
                ingredients.add(ingredient);
            if(itemInfo instanceof ItemInfo.AdHoc) {
                String text = ((ItemInfo.AdHoc)itemInfo).str.text;
                if(isFoodModifier(text))
                    modifiers.add(text.trim());
            }
        }
        // The server-provided tooltip order carries recipe meaning: the primary ingredient is
        // normally first. Keep that order instead of alphabetizing it away.
        modifiers.sort(String.CASE_INSENSITIVE_ORDER);

        List<CookbookFood.Fep> feps = new ArrayList<>();
        for(FoodInfo.Event event : foodInfo.evs) {
            feps.add(new CookbookFood.Fep(event.ev.nm, round(event.a),
                    round(event.a / fepQualityFactor)));
        }
        feps.sort(Comparator.comparing(value -> value.attribute, String.CASE_INSENSITIVE_ORDER));

        double hunger = round(foodInfo.glut * 1000d);
        boolean salted = modifiers.stream().anyMatch(value -> value.equalsIgnoreCase("Salted"));
        FoodInfo.Efficiency efficiency = foodInfo.currentEfficiency(salted);
        double capturedFepEfficiency = efficiency.available ? round(efficiency.fepPercent) : Double.NaN;
        double capturedHungerEfficiency = efficiency.available ?
                round(efficiency.hungerPercent) : Double.NaN;
        return(new CookbookFood(worldId, characterId, itemName, resource.name, quality,
                round(foodInfo.end * 100d), hunger, round(hunger / hungerQualityFactor),
                capturedFepEfficiency, capturedHungerEfficiency, ingredients, modifiers, feps,
                observedAt));
    }

    private static String itemName(List<ItemInfo> info, Resource resource) {
        ItemInfo.Name itemNameInfo = ItemInfo.find(ItemInfo.Name.class, info);
        return((itemNameInfo == null) ? resource.basename() : itemNameInfo.str.text);
    }

    private static CookbookFood.Ingredient parseIngredient(ItemInfo info) {
        String simpleName = info.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String kind;
        if(simpleName.contains("ingredient"))
            kind = "ingredient";
        else if(simpleName.contains("smoke"))
            kind = "smoke";
        else
            return(null);
        try {
            Field nameField = info.getClass().getField("name");
            Field valueField = info.getClass().getField("val");
            Object name = nameField.get(info);
            Object value = valueField.get(info);
            if((name instanceof String) && (value instanceof Number)) {
                return(new CookbookFood.Ingredient(kind, (String)name,
                        round(((Number)value).doubleValue() * 100d)));
            }
        } catch(ReflectiveOperationException ignored) {
            // Ingredient tooltip classes are delivered by game resources and have no stable Java type.
        }
        return(null);
    }

    private static boolean isFoodModifier(String text) {
        if(text == null)
            return(false);
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return(normalized.equals("salted") || normalized.equals("peppered") ||
                normalized.equals("white-truffled") || normalized.equals("black-truffled"));
    }

    private static double round(double value) {
        return(Math.round(value * 100d) / 100d);
    }
}
