package haven.cookbook;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Shared strongest-attribute calculation for recipe and ingredient presentation. */
final class CookbookRecipeStat {
    final CookbookAttribute attribute;
    final double amount;

    private CookbookRecipeStat(CookbookAttribute attribute, double amount) {
        this.attribute = attribute;
        this.amount = amount;
    }

    static CookbookRecipeStat strongest(Map<String, Double> feps) {
        EnumMap<CookbookAttribute, Double> totals = new EnumMap<>(CookbookAttribute.class);
        for(Map.Entry<String, Double> fep : feps.entrySet())
            add(totals, fep.getKey(), fep.getValue());
        return(strongest(totals));
    }

    static CookbookRecipeStat strongest(List<CookbookEntry.FepValue> feps) {
        EnumMap<CookbookAttribute, Double> totals = new EnumMap<>(CookbookAttribute.class);
        for(CookbookEntry.FepValue fep : feps)
            add(totals, fep.attribute, fep.normalizedAmount);
        return(strongest(totals));
    }

    private static void add(EnumMap<CookbookAttribute, Double> totals, String event,
                            double amount) {
        CookbookAttribute attribute = CookbookAttribute.forEvent(event);
        if(attribute != null)
            totals.merge(attribute, amount, Double::sum);
    }

    private static CookbookRecipeStat strongest(EnumMap<CookbookAttribute, Double> totals) {
        CookbookAttribute strongest = null;
        double amount = 0;
        for(CookbookAttribute attribute : CookbookAttribute.ALL) {
            if(attribute.allRecipes())
                continue;
            double candidate = totals.getOrDefault(attribute, 0d);
            if(strongest == null || candidate > amount) {
                strongest = attribute;
                amount = candidate;
            }
        }
        return(new CookbookRecipeStat(amount > 0 ? strongest : null, amount));
    }
}
