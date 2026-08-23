package haven.cookbook;

import haven.Button;
import haven.CharWnd;
import haven.CheckBox;
import haven.Coord;
import haven.GameUI;
import haven.GItem;
import haven.Indir;
import haven.Label;
import haven.Loading;
import haven.MenuGrid;
import haven.PUtils;
import haven.Resource;
import haven.RichText;
import haven.RichTextBox;
import haven.SDropBox;
import haven.SListBox;
import haven.SListWidget;
import haven.Tabs;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Utils;
import haven.WItem;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

/** Searchable recipes and an evidence-backed ingredient planning view. */
public final class CookbookWindow extends Window {
    private static final double INVENTORY_SCAN_INTERVAL = 1.5;
    private static final Text.Foundry ROW_TEXT =
            new Text.Foundry(Text.sans, 11, Color.WHITE).aa(true);
    private static final Text.Foundry ROW_MUTED =
            new Text.Foundry(Text.sans, 11, new Color(190, 190, 190)).aa(true);
    private static final Text.Foundry SPICE_TEXT =
            new Text.Foundry(Text.sans, 11, new Color(213, 151, 235)).aa(true);
    private static final Color QUALITY_COLOR = new Color(230, 196, 116);
    private static final Color NOTE_COLOR = new Color(170, 190, 205);

    private final GameUI gui;
    private final CookbookService service;
    private final Tabs tabs;
    private final Tabs.Tab recipesTab;
    private final Tabs.Tab ingredientsTab;

    private final AttributeDropBox attributeDropBox;
    private final RecipeSortDropBox recipeSortDropBox;
    private final TextEntry recipeSearchEntry;
    private final Label recipeStatus;
    private final Label targetHeader;
    private final FoodList foodList;
    private final RecipeVariantDropBox recipeVariantDropBox;
    private final CheckBox showUnmodifiedStats;
    private final RichTextBox recipeDetails;
    private final Button openRecipeButton;

    private final IngredientCategoryDropBox ingredientCategoryDropBox;
    private final TextEntry ingredientSearchEntry;
    private final Label ingredientStatus;
    private final IngredientList ingredientList;
    private final RichTextBox ingredientDetails;
    private final IngredientRecipeList ingredientRecipeList;

    private List<CookbookRecipeGroup> recipeGroups = java.util.Collections.emptyList();
    private List<CookbookIngredientEntry> ingredientEntries = java.util.Collections.emptyList();
    private Future<List<CookbookEntry>> recipeQuery;
    private Future<List<CookbookIngredientEntry>> ingredientQuery;
    private boolean recipeQueryDirty = true;
    private boolean ingredientQueryDirty = true;
    private boolean captureStatusShown;
    private final Map<GItem, Integer> scannedInventoryItems = new IdentityHashMap<>();
    private double inventoryScanElapsed;
    private boolean inventoryScanVisible;
    private long observedGeneration = -1;
    private CookbookRecipeGroup selectedRecipeGroup;
    private CookbookEntry selectedRecipe;

    public CookbookWindow(GameUI gui, CookbookService service) {
        super(UI.scale(860, 565), "Cookbook");
        this.gui = gui;
        this.service = service;

        tabs = new Tabs(UI.scale(0, 31), UI.scale(860, 534), this);
        recipesTab = tabs.add();
        ingredientsTab = tabs.add();
        add(tabs.new TabButton(UI.scale(120), "Recipes", recipesTab), UI.scale(10, 2));
        add(tabs.new TabButton(UI.scale(120), "Ingredients", ingredientsTab), UI.scale(135, 2));

        recipesTab.add(new Label("View:"), UI.scale(10, 12));
        attributeDropBox = recipesTab.add(new AttributeDropBox(), UI.scale(65, 5));
        recipesTab.add(new Label("Sort:"), UI.scale(235, 12));
        recipeSortDropBox = recipesTab.add(new RecipeSortDropBox(), UI.scale(270, 5));
        recipesTab.add(new Label("Search:"), UI.scale(440, 12));
        recipeSearchEntry = recipesTab.add(new TextEntry(UI.scale(150), "") {
            @Override
            protected void changed() {
                scheduleRecipeRefresh();
                super.changed();
            }
        }, UI.scale(490, 6));
        recipesTab.add(new Button(UI.scale(75), "Refresh") {
            @Override
            public void click() {
                scheduleRecipeRefresh();
            }
        }, UI.scale(650, 6));
        recipeStatus = recipesTab.add(new Label("Loading..."), UI.scale(735, 12));

        recipesTab.add(new Label("Food"), UI.scale(12, 45));
        recipesTab.add(new Label("Best known recipe"), UI.scale(255, 45));
        targetHeader = recipesTab.add(new Label("Total (Q10)"), UI.scale(500, 45));
        recipesTab.add(new Label("FEP / hunger"), UI.scale(585, 45));
        recipesTab.add(new Label("Hunger (Q10)"), UI.scale(690, 45));
        recipesTab.add(new Label("Latest Q"), UI.scale(780, 45));

        foodList = recipesTab.add(new FoodList(UI.scale(840, 258)), UI.scale(10, 66));
        recipesTab.add(new Label("Known recipes:"), UI.scale(12, 335));
        recipeVariantDropBox = recipesTab.add(new RecipeVariantDropBox(), UI.scale(105, 327));
        showUnmodifiedStats = recipesTab.add(new CheckBox("Show unmodified stats") {
            {a = Utils.getprefb("cookbook-show-unmodified-stats", false);}

            @Override
            public void changed(boolean value) {
                Utils.setprefb("cookbook-show-unmodified-stats", value);
                showRecipeDetails(selectedRecipe);
            }
        }, UI.scale(565, 333));
        openRecipeButton = recipesTab.add(new Button(UI.scale(135), "Open recipe") {
            @Override
            public void click() {
                openSelectedRecipe();
            }
        }, UI.scale(715, 327));
        openRecipeButton.disable(true);
        recipeDetails = recipesTab.add(new RichTextBox(UI.scale(840, 166),
                "Select a food to see its Q10 baseline and observed outcomes."), UI.scale(10, 357));

        ingredientsTab.add(new Label("Category:"), UI.scale(10, 12));
        ingredientCategoryDropBox = ingredientsTab.add(new IngredientCategoryDropBox(), UI.scale(75, 5));
        ingredientsTab.add(new Label("Search:"), UI.scale(250, 12));
        ingredientSearchEntry = ingredientsTab.add(new TextEntry(UI.scale(300), "") {
            @Override
            protected void changed() {
                scheduleIngredientRefresh();
                super.changed();
            }
        }, UI.scale(300, 6));
        ingredientsTab.add(new Button(UI.scale(75), "Refresh") {
            @Override
            public void click() {
                scheduleIngredientRefresh();
            }
        }, UI.scale(610, 6));
        ingredientStatus = ingredientsTab.add(new Label("Loading ingredients..."), UI.scale(695, 12));

        ingredientsTab.add(new Label("Ingredient"), UI.scale(12, 45));
        ingredientsTab.add(new Label("Category"), UI.scale(265, 45));
        ingredientsTab.add(new Label("Main attributes (Q10 recipe avg)"), UI.scale(365, 45));
        ingredientsTab.add(new Label("Measured spice boost"), UI.scale(625, 45));
        ingredientsTab.add(new Label("Recipes"), UI.scale(805, 45));
        ingredientList = ingredientsTab.add(new IngredientList(UI.scale(840, 218)), UI.scale(10, 66));
        ingredientsTab.add(new Label("Ingredient planning details"), UI.scale(12, 296));
        ingredientsTab.add(new Label("Best crafted foods by strongest Q10 FEP"), UI.scale(440, 296));
        ingredientDetails = ingredientsTab.add(new RichTextBox(UI.scale(415, 204),
                "Select an ingredient to see its observed Q10 recipe profile."), UI.scale(10, 319));
        ingredientRecipeList = ingredientsTab.add(
                new IngredientRecipeList(UI.scale(410, 204)), UI.scale(440, 319));

        reqclose(this::hide);
    }

    public void refresh() {
        scheduleRecipeRefresh();
        scheduleIngredientRefresh();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(!visible()) {
            inventoryScanVisible = false;
            return;
        }
        if(!inventoryScanVisible) {
            inventoryScanVisible = true;
            scannedInventoryItems.clear();
            inventoryScanElapsed = INVENTORY_SCAN_INTERVAL;
        }
        inventoryScanElapsed += dt;
        if(inventoryScanElapsed >= INVENTORY_SCAN_INTERVAL) {
            inventoryScanElapsed = 0;
            scanMainInventory();
        }
        long generation = service.generation();
        if(observedGeneration != generation) {
            observedGeneration = generation;
            recipeQueryDirty = true;
            ingredientQueryDirty = true;
        }
        if(recipeQuery != null && recipeQuery.isDone())
            finishRecipeQuery();
        if(ingredientQuery != null && ingredientQuery.isDone())
            finishIngredientQuery();
        if(recipeQueryDirty && recipeQuery == null) {
            recipeQueryDirty = false;
            // Load complete food families; search decides which families are visible but the
            // selected family's dropdown must retain every known ingredient recipe.
            recipeQuery = service.list(selectedAttribute().label, "");
            recipeStatus.settext("Loading...");
        }
        if(ingredientQueryDirty && ingredientQuery == null) {
            ingredientQueryDirty = false;
            ingredientQuery = service.listIngredients(selectedIngredientCategory(),
                    ingredientSearchEntry.buf.line());
            ingredientStatus.settext("Loading...");
        }
        int pendingCaptures = service.pendingCaptures();
        if(pendingCaptures > 0) {
            captureStatusShown = true;
            recipeStatus.settext("Capturing " + pendingCaptures +
                    (pendingCaptures == 1 ? " food..." : " foods..."));
        } else if(captureStatusShown) {
            captureStatusShown = false;
            scheduleRecipeRefresh();
        }
    }

    private void scanMainInventory() {
        if(gui.maininv == null)
            return;
        Map<GItem, Integer> present = new IdentityHashMap<>();
        for(WItem widget : gui.maininv.getAllItems()) {
            if(widget == null || widget.item == null)
                continue;
            GItem item = widget.item;
            int sequence = item.cookbookInfoSequence();
            present.put(item, sequence);
            Integer scannedSequence = scannedInventoryItems.get(item);
            if((scannedSequence == null || scannedSequence != sequence) &&
                    item.scanForCookbook())
                scannedInventoryItems.put(item, sequence);
        }
        scannedInventoryItems.keySet().retainAll(present.keySet());
    }

    private CookbookAttribute selectedAttribute() {
        CookbookAttribute selected = attributeDropBox.sel;
        return(selected == null ? CookbookAttribute.ALL_RECIPES : selected);
    }

    private CookbookIngredientCategory selectedIngredientCategory() {
        CookbookIngredientCategory selected = ingredientCategoryDropBox.sel;
        return(selected == null ? CookbookIngredientCategory.ALL : selected);
    }

    private void scheduleRecipeRefresh() {
        recipeQueryDirty = true;
    }

    private void scheduleIngredientRefresh() {
        ingredientQueryDirty = true;
    }

    private void finishRecipeQuery() {
        try {
            String selectedKey = foodList.sel == null ? "" : foodList.sel.key;
            List<CookbookEntry> recipes = new ArrayList<>(recipeQuery.get());
            recipeGroups = CookbookRecipeGroup.group(recipes);
            String search = CookbookIngredientCatalog.normalize(recipeSearchEntry.buf.line());
            if(!search.isEmpty())
                recipeGroups.removeIf(group -> !recipeGroupMatches(group, search));
            sortRecipeGroups();
            foodList.reset();
            CookbookRecipeGroup selected = findRecipeGroup(selectedKey);
            foodList.change(selected);
            int visibleRecipes = recipeGroups.stream().mapToInt(group -> group.recipes.size()).sum();
            recipeStatus.settext(recipeGroups.size() + " foods / " + visibleRecipes + " recipes");
            if(!service.available() && service.lastError() != null)
                recipeStatus.settext(service.lastError());
        } catch(Exception e) {
            String error = service.lastError();
            recipeStatus.settext((error == null) ? "Could not load recipes." : error);
        } finally {
            recipeQuery = null;
        }
    }

    private void finishIngredientQuery() {
        try {
            String selectedName = ingredientList.sel == null ? "" : ingredientList.sel.name;
            ingredientEntries = new ArrayList<>(ingredientQuery.get());
            ingredientList.reset();
            CookbookIngredientEntry selected = findIngredientEntry(selectedName);
            ingredientList.change(selected);
            ingredientStatus.settext(ingredientEntries.size() + (ingredientEntries.size() == 1 ?
                    " ingredient" : " ingredients"));
        } catch(Exception e) {
            String error = service.lastError();
            ingredientStatus.settext((error == null) ? "Could not load ingredients." : error);
        } finally {
            ingredientQuery = null;
        }
    }

    private CookbookRecipeGroup findRecipeGroup(String key) {
        if(key == null || key.isEmpty())
            return(null);
        for(CookbookRecipeGroup group : recipeGroups) {
            if(group.key.equals(key))
                return(group);
        }
        return(null);
    }

    private void sortRecipeGroups() {
        CookbookRecipeSort sort = selectedRecipeSort();
        boolean allRecipes = selectedAttribute().allRecipes();
        Comparator<CookbookRecipeGroup> comparator;
        if(sort == CookbookRecipeSort.NAME) {
            comparator = Comparator.comparing(group -> group.itemName,
                    String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparingDouble(
                            (CookbookRecipeGroup group) -> group.sortValue(sort, allRecipes))
                    .reversed()
                    .thenComparing(group -> group.itemName, String.CASE_INSENSITIVE_ORDER);
        }
        recipeGroups.sort(comparator);
    }

    private static boolean recipeGroupMatches(CookbookRecipeGroup group, String search) {
        if(CookbookIngredientCatalog.normalize(group.itemName).contains(search))
            return(true);
        for(CookbookEntry recipe : group.recipes) {
            if(CookbookIngredientCatalog.normalize(recipe.ingredients).contains(search) ||
                    CookbookIngredientCatalog.normalize(recipe.modifiers).contains(search))
                return(true);
        }
        return(false);
    }

    private CookbookRecipeSort selectedRecipeSort() {
        CookbookRecipeSort selected = recipeSortDropBox.sel;
        return(selected == null ? CookbookRecipeSort.SELECTED_STAT : selected);
    }

    private CookbookIngredientEntry findIngredientEntry(String name) {
        String wanted = CookbookIngredientCatalog.normalize(name);
        for(CookbookIngredientEntry entry : ingredientEntries) {
            if(CookbookIngredientCatalog.normalize(entry.name).equals(wanted))
                return(entry);
        }
        return(null);
    }

    private void showRecipeDetails(CookbookEntry entry) {
        openRecipeButton.disable(entry == null);
        if(entry == null) {
            recipeDetails.settext("Select a food to see its Q10 baseline and observed outcomes.");
            return;
        }

        String ingredients = entry.ingredients.isEmpty() ?
                "No ingredient details supplied" : entry.ingredients;
        String modifiers = entry.modifiers.isEmpty() ? "None" : entry.modifiers;
        int recipeNumber = selectedRecipeGroup == null ? 1 :
                selectedRecipeGroup.recipes.indexOf(entry) + 1;
        int recipeCount = selectedRecipeGroup == null ? 1 : selectedRecipeGroup.recipes.size();
        StringBuilder text = new StringBuilder();
        text.append(color(selectedAttribute().color, entry.itemName)).append("  ")
                .append(color(NOTE_COLOR, "Recipe " + recipeNumber + " of " + recipeCount))
                .append("\n")
                .append("$b{Ingredients:} ").append(quote(ingredients)).append("\n")
                .append("$b{Modifiers:} ").append(quote(modifiers)).append("\n");
        if(showUnmodifiedStats.state()) {
            text.append("$b{Q10 baseline (unmodified):} Hunger ")
                    .append(String.format(Locale.ROOT, "%.2f‰  Energy %.2f%%",
                            entry.hungerPermille, entry.energyPercent)).append("\n")
                    .append("$b{Unmodified FEPs:} ")
                    .append(fepSummary(entry.feps, true)).append("\n");
        }
        text.append(showUnmodifiedStats.state() ? "$b{Observed crafted outcomes:}" :
                "$b{Observed character-adjusted outcomes:}");

        for(CookbookEntry.Observation observation : entry.observations) {
            text.append("\n")
                    .append(color(QUALITY_COLOR,
                            String.format(Locale.ROOT, "Q%.1f", observation.quality)));
            if(observation.seenCount > 1)
                text.append("  (seen ").append(observation.seenCount).append(" times)");
            if(showUnmodifiedStats.state()) {
                text.append(String.format(Locale.ROOT,
                                " unmodified — Hunger %.2f‰  Energy %.2f%%",
                                observation.hungerPermille, observation.energyPercent))
                        .append("\n  ").append(fepSummary(observation.feps, false));
            }
            if(Double.isFinite(observation.capturedFepEfficiencyPercent) &&
                    Double.isFinite(observation.capturedHungerEfficiencyPercent)) {
                double capturedHunger = observation.hungerPermille *
                        (observation.capturedHungerEfficiencyPercent / 100d);
                text.append("\n  $b{Last captured character modifiers:} ")
                        .append(String.format(Locale.ROOT,
                                "FEP %.2f%%  Hunger %.2f%% — Hunger %.2f‰",
                                observation.capturedFepEfficiencyPercent,
                                observation.capturedHungerEfficiencyPercent, capturedHunger))
                        .append("\n  ").append(fepSummary(observation.feps, false,
                                observation.capturedFepEfficiencyPercent / 100d));
            } else if(!showUnmodifiedStats.state()) {
                text.append(" — Character-adjusted stats were not captured");
            }
        }
        recipeDetails.settext(text.toString());
    }

    private void showIngredientDetails(CookbookIngredientEntry entry) {
        ingredientRecipeList.reset();
        if(entry == null) {
            ingredientDetails.settext("Select an ingredient to see its observed Q10 recipe profile.");
            return;
        }
        CookbookAttribute main = entry.mainAttribute();
        Color nameColor = entry.spice() ? SPICE_TEXT.defcol :
                (main == null ? Color.WHITE : main.color);
        StringBuilder text = new StringBuilder();
        text.append(color(nameColor, entry.name)).append("  •  ")
                .append(quote(entry.category.label)).append("\n")
                .append("$b{Observed in:} ").append(entry.recipeCount)
                .append(entry.recipeCount == 1 ? " local recipe" : " local recipes")
                .append("\n")
                .append("$b{Q10 recipe averages:} ")
                .append(ingredientFepSummary(entry.averageFeps)).append("\n");
        if(entry.spice()) {
            text.append("$b{Measured spice boost:} ");
            if(entry.matchedSpiceComparisons == 0) {
                text.append("No matching unspiced recipe has been observed yet.");
            } else if(entry.spiceBoosts.isEmpty()) {
                text.append("No FEP change in ").append(entry.matchedSpiceComparisons)
                        .append(" matched comparison");
            } else {
                text.append(spiceBoostSummary(entry.spiceBoosts)).append("  (")
                        .append(entry.matchedSpiceComparisons).append(" matched comparison")
                        .append(entry.matchedSpiceComparisons == 1 ? ")" : "s)");
            }
            text.append("\n");
        }
        text.append(color(NOTE_COLOR, "Planner note: averages describe recipes containing this " +
                "ingredient; spice boosts compare identical outputs and non-spice ingredients."));
        ingredientDetails.settext(text.toString());
    }

    private String fepSummary(List<CookbookEntry.FepValue> feps, boolean normalized) {
        return(fepSummary(feps, normalized, 1d));
    }

    private String fepSummary(List<CookbookEntry.FepValue> feps, boolean normalized,
                              double multiplier) {
        if(feps.isEmpty())
            return("No FEP events");
        List<CookbookEntry.FepValue> ordered = new ArrayList<>(feps);
        CookbookAttribute target = selectedAttribute();
        ordered.sort(Comparator.comparingInt((CookbookEntry.FepValue value) ->
                        target.matches(value.attribute) ? 0 : 1)
                .thenComparing(value -> value.attribute, String.CASE_INSENSITIVE_ORDER));
        List<String> parts = new ArrayList<>();
        for(CookbookEntry.FepValue fep : ordered) {
            CookbookAttribute attribute = CookbookAttribute.forEvent(fep.attribute);
            Color valueColor = (attribute == null) ? Color.WHITE : attribute.color;
            double value = (normalized ? fep.normalizedAmount : fep.amount) * multiplier;
            parts.add(color(valueColor,
                    String.format(Locale.ROOT, "%s %.2f", fep.attribute, value)));
        }
        return(String.join("  •  ", parts));
    }

    private static String ingredientFepSummary(List<CookbookIngredientEntry.AttributeValue> values) {
        if(values.isEmpty())
            return("No FEP observations");
        List<String> parts = new ArrayList<>();
        for(CookbookIngredientEntry.AttributeValue value : values) {
            CookbookAttribute attribute = CookbookAttribute.forEvent(value.attribute);
            Color valueColor = attribute == null ? Color.WHITE : attribute.color;
            parts.add(color(valueColor,
                    String.format(Locale.ROOT, "%s %.2f", value.attribute, value.amount)));
        }
        return(String.join("  •  ", parts));
    }

    private static String spiceBoostSummary(List<CookbookIngredientEntry.SpiceBoost> values) {
        List<String> parts = new ArrayList<>();
        for(CookbookIngredientEntry.SpiceBoost value : values) {
            CookbookAttribute attribute = CookbookAttribute.forEvent(value.attribute);
            Color valueColor = attribute == null ? Color.WHITE : attribute.color;
            String amount = String.format(Locale.ROOT, "%s %+.2f", value.attribute, value.amount);
            if(Double.isFinite(value.percent))
                amount += String.format(Locale.ROOT, " (%+.1f%%)", value.percent);
            parts.add(color(valueColor, amount));
        }
        return(String.join("  •  ", parts));
    }

    private static String compactAttributes(List<CookbookIngredientEntry.AttributeValue> values) {
        if(values.isEmpty())
            return("No FEP data");
        List<String> parts = new ArrayList<>();
        for(int i = 0; i < Math.min(3, values.size()); i++) {
            CookbookIngredientEntry.AttributeValue value = values.get(i);
            parts.add(shortAttribute(value.attribute) + " " +
                    String.format(Locale.ROOT, "%.2f", value.amount));
        }
        return(String.join("  •  ", parts));
    }

    private static String compactBoosts(CookbookIngredientEntry entry) {
        if(!entry.spice())
            return("—");
        if(entry.matchedSpiceComparisons == 0)
            return("Needs baseline");
        if(entry.spiceBoosts.isEmpty())
            return("No change");
        List<String> parts = new ArrayList<>();
        for(int i = 0; i < Math.min(2, entry.spiceBoosts.size()); i++) {
            CookbookIngredientEntry.SpiceBoost boost = entry.spiceBoosts.get(i);
            parts.add(shortAttribute(boost.attribute) + " " +
                    String.format(Locale.ROOT, "%+.2f", boost.amount));
        }
        return(String.join("  ", parts));
    }

    private static String shortAttribute(String eventName) {
        CookbookAttribute attribute = CookbookAttribute.forEvent(eventName);
        if(attribute == null)
            return(eventName.length() <= 3 ? eventName : eventName.substring(0, 3));
        return(attribute.label.substring(0, Math.min(3, attribute.label.length())));
    }

    private void openSelectedRecipe() {
        CookbookEntry selected = selectedRecipe;
        if(selected == null)
            return;
        MenuGrid.Pagina recipe = findCraftingRecipe(selected.itemName);
        if(recipe == null) {
            gui.error("No known crafting recipe for " + selected.itemName + ".");
            return;
        }
        try {
            gui.menu.use(recipe.button(), new MenuGrid.Interaction(), false);
        } catch(Loading loading) {
            gui.error("The crafting recipe is still loading. Try again in a moment.");
        }
    }

    private MenuGrid.Pagina findCraftingRecipe(String itemName) {
        if(gui.menu == null)
            return(null);
        String wanted = normalizeName(itemName);
        List<MenuGrid.Pagina> pages;
        synchronized(gui.menu.paginae) {
            pages = new ArrayList<>(gui.menu.paginae);
        }
        pages.sort(Comparator.comparing(page -> {
            try {
                return(page.res().name);
            } catch(Loading loading) {
                return("");
            }
        }));
        for(MenuGrid.Pagina page : pages) {
            try {
                MenuGrid.PagButton button = page.button();
                String[] action = button.act().ad;
                boolean craftingAction = action.length > 0 &&
                        (action[0].equals("craft") || action[0].equals("bp"));
                if(craftingAction && normalizeName(button.name()).equals(wanted))
                    return(page);
            } catch(RuntimeException ignored) {
                // A partially loaded menu page cannot be used yet; another click can retry it.
            }
        }
        return(null);
    }

    private static String normalizeName(String value) {
        return(value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT));
    }

    private static String color(Color color, String text) {
        return(String.format(Locale.ROOT, "$col[%d,%d,%d,%d]{%s}", color.getRed(),
                color.getGreen(), color.getBlue(), color.getAlpha(), quote(text)));
    }

    private static String quote(String text) {
        return(RichText.Parser.quote(text));
    }

    private final class AttributeDropBox extends SDropBox<CookbookAttribute, Widget> {
        private AttributeDropBox() {
            super(UI.scale(160), UI.scale(260), UI.scale(26));
            super.change(CookbookAttribute.ALL_RECIPES);
        }

        @Override
        protected List<CookbookAttribute> items() {
            return(CookbookAttribute.ALL);
        }

        @Override
        protected Widget makeitem(CookbookAttribute attribute, int index, Coord size) {
            return(new AttributeItem(size, attribute));
        }

        @Override
        public void change(CookbookAttribute attribute) {
            super.change(attribute);
            targetHeader.settext(attribute.allRecipes() ? "Total (Q10)" : "Target (Q10)");
            scheduleRecipeRefresh();
            if(foodList != null)
                showRecipeDetails(selectedRecipe);
        }
    }

    private final class RecipeSortDropBox extends SDropBox<CookbookRecipeSort, Widget> {
        private RecipeSortDropBox() {
            super(UI.scale(160), UI.scale(180), UI.scale(26));
            super.change(CookbookRecipeSort.SELECTED_STAT);
        }

        @Override
        protected List<CookbookRecipeSort> items() {
            return(CookbookRecipeSort.ALL);
        }

        @Override
        protected Widget makeitem(CookbookRecipeSort sort, int index, Coord size) {
            return(SListWidget.TextItem.of(size, ROW_TEXT, () -> sort.label));
        }

        @Override
        public void change(CookbookRecipeSort sort) {
            super.change(sort);
            scheduleRecipeRefresh();
        }
    }

    private final class RecipeVariantDropBox extends SDropBox<CookbookEntry, Widget> {
        private RecipeVariantDropBox() {
            super(UI.scale(450), UI.scale(280), UI.scale(28));
        }

        @Override
        protected List<CookbookEntry> items() {
            return(selectedRecipeGroup == null ? java.util.Collections.emptyList() :
                    selectedRecipeGroup.recipes);
        }

        @Override
        protected Widget makeitem(CookbookEntry recipe, int index, Coord size) {
            if(recipe == null)
                return(SListWidget.TextItem.of(size, ROW_MUTED,
                        () -> "No recipe selected"));
            RecipeStat stat = recipeVariantStat(recipe);
            String ingredients = recipe.ingredients.isEmpty() ? "Ingredients not captured" :
                    recipe.ingredients;
            String modifiers = recipe.modifiers.isEmpty() ? "" :
                    "  [" + recipe.modifiers + "]";
            Widget row = new Widget(size);
            row.add(SListWidget.TextItem.of(UI.scale(150, 28), stat.font(), stat::label),
                    Coord.z);
            row.add(SListWidget.TextItem.of(
                    Coord.of(Math.max(1, size.x - UI.scale(155)), size.y), ROW_TEXT,
                    () -> ingredients + modifiers), UI.scale(155, 0));
            return(row);
        }

        @Override
        public void change(CookbookEntry recipe) {
            super.change(recipe);
            selectedRecipe = recipe;
            showRecipeDetails(recipe);
        }
    }

    private RecipeStat recipeVariantStat(CookbookEntry recipe) {
        CookbookAttribute selected = selectedAttribute();
        if(!selected.allRecipes())
            return(new RecipeStat(selected, recipe.targetFep));
        CookbookRecipeStat strongest = CookbookRecipeStat.strongest(recipe.feps);
        return(new RecipeStat(strongest.attribute, strongest.amount));
    }

    private static final class RecipeStat {
        final CookbookAttribute attribute;
        final double amount;

        RecipeStat(CookbookAttribute attribute, double amount) {
            this.attribute = attribute;
            this.amount = amount;
        }

        Text.Forge font() {
            return(attribute == null ? ROW_MUTED : attribute.font());
        }

        String label() {
            return(attribute == null ? "No FEP" : String.format(Locale.ROOT, "%.2f %s",
                    amount, attribute.label));
        }
    }

    private final class IngredientCategoryDropBox extends SDropBox<CookbookIngredientCategory, Widget> {
        private IngredientCategoryDropBox() {
            super(UI.scale(160), UI.scale(250), UI.scale(26));
            super.change(CookbookIngredientCategory.ALL);
        }

        @Override
        protected List<CookbookIngredientCategory> items() {
            return(CookbookIngredientCategory.ALL_VALUES);
        }

        @Override
        protected Widget makeitem(CookbookIngredientCategory category, int index, Coord size) {
            return(SListWidget.TextItem.of(size, ROW_TEXT, () -> category.label));
        }

        @Override
        public void change(CookbookIngredientCategory category) {
            super.change(category);
            scheduleIngredientRefresh();
        }
    }

    private static final class AttributeItem extends SListWidget.IconText {
        private final CookbookAttribute attribute;

        private AttributeItem(Coord size, CookbookAttribute attribute) {
            super(size);
            this.attribute = attribute;
        }

        @Override
        protected BufferedImage img() {
            return(attribute.icon());
        }

        @Override
        protected String text() {
            return(attribute.label);
        }

        @Override
        protected int margin() {
            return(UI.scale(3));
        }

        @Override
        protected Text.Forge foundry() {
            return(attribute.font());
        }

        @Override
        protected PUtils.Convolution filter() {
            return(CharWnd.iconfilter);
        }
    }

    private static final class ResourceIconText extends SListWidget.IconText {
        private final Indir<Resource> resource;
        private final Indir<Resource> overlay;
        private final String label;
        private final Text.Forge font;

        private ResourceIconText(Coord size, String resourceName, String label, Text.Forge font) {
            this(size, resourceName, null, label, font);
        }

        private ResourceIconText(Coord size, String resourceName, String overlayResourceName,
                                 String label, Text.Forge font) {
            super(size);
            this.resource = Resource.remote().load(resourceName == null || resourceName.isBlank() ?
                    "gfx/invobjs/missing" : resourceName);
            this.overlay = overlayResourceName == null || overlayResourceName.isBlank() ? null :
                    Resource.remote().load(overlayResourceName);
            this.label = label;
            this.font = font;
        }

        @Override
        protected BufferedImage img() {
            BufferedImage base;
            try {
                base = resource.get().flayer(Resource.imgc).img;
            } catch(Loading loading) {
                throw(loading);
            } catch(RuntimeException failure) {
                return(WItem.missing.flayer(Resource.imgc).img);
            }
            if(overlay == null)
                return(base);
            try {
                BufferedImage combined = PUtils.copy(base);
                PUtils.alphablit(combined.getRaster(),
                        overlay.get().flayer(Resource.imgc).img.getRaster(), Coord.z);
                return(combined);
            } catch(Loading loading) {
                throw(loading);
            } catch(RuntimeException failure) {
                return(base);
            }
        }

        @Override
        protected String text() {
            return(label);
        }

        @Override
        protected Text.Forge foundry() {
            return(font);
        }

        @Override
        protected int margin() {
            return(UI.scale(2));
        }

        @Override
        protected PUtils.Convolution filter() {
            return(CharWnd.iconfilter);
        }
    }

    private final class FoodList extends SListBox<CookbookRecipeGroup, Widget> {
        private FoodList(Coord size) {
            super(size, UI.scale(30), UI.scale(2));
        }

        @Override
        protected List<? extends CookbookRecipeGroup> items() {
            return(recipeGroups);
        }

        @Override
        protected Widget makeitem(CookbookRecipeGroup group, int index, Coord size) {
            CookbookAttribute target = selectedAttribute();
            boolean allRecipes = target.allRecipes();
            CookbookEntry entry = group.representative(selectedRecipeSort(), allRecipes);
            double displayedFep = allRecipes ? entry.totalFep : entry.targetFep;
            double displayedEfficiency = (allRecipes && entry.hungerPermille > 0) ?
                    entry.totalFep / entry.hungerPermille : entry.targetPerHunger;
            Text.Forge nameFont = (allRecipes || entry.targetFep > 0) ? target.font() : ROW_MUTED;
            String secondary = entry.modifiers.isEmpty() ? entry.ingredients :
                    "[" + entry.modifiers + "] " + entry.ingredients;
            String foodName = group.itemName + (group.recipes.size() > 1 ?
                    "  (" + group.recipes.size() + " recipes)" : "");
            Widget row = new SListWidget.ItemWidget<CookbookRecipeGroup>(this, size, group);
            row.add(new ResourceIconText(UI.scale(236, 30), entry.resourceName,
                    CookbookIngredientCatalog.meatIconOverlay(entry.itemName, entry.resourceName),
                    foodName, nameFont), UI.scale(3, 0));
            row.add(SListWidget.TextItem.of(UI.scale(240, 30), ROW_MUTED,
                    () -> secondary), UI.scale(245, 0));
            row.add(SListWidget.TextItem.of(UI.scale(75, 30), target.font(),
                    () -> String.format(Locale.ROOT, "%.2f", displayedFep)), UI.scale(500, 0));
            row.add(SListWidget.TextItem.of(UI.scale(95, 30), ROW_TEXT,
                    () -> String.format(Locale.ROOT, "%.3f", displayedEfficiency)), UI.scale(585, 0));
            row.add(SListWidget.TextItem.of(UI.scale(72, 30), ROW_TEXT,
                    () -> String.format(Locale.ROOT, "%.2f‰", entry.hungerPermille)), UI.scale(690, 0));
            row.add(SListWidget.TextItem.of(UI.scale(55, 30), ROW_TEXT,
                    () -> String.format(Locale.ROOT, "Q%.1f", entry.quality)), UI.scale(780, 0));
            return(row);
        }

        @Override
        public void change(CookbookRecipeGroup group) {
            super.change(group);
            selectedRecipeGroup = group;
            CookbookEntry recipe = group == null ? null :
                    group.representative(selectedRecipeSort(), selectedAttribute().allRecipes());
            recipeVariantDropBox.change(recipe);
        }
    }

    private final class IngredientList extends SListBox<CookbookIngredientEntry, Widget> {
        private IngredientList(Coord size) {
            super(size, UI.scale(30), UI.scale(2));
        }

        @Override
        protected List<? extends CookbookIngredientEntry> items() {
            return(ingredientEntries);
        }

        @Override
        protected Widget makeitem(CookbookIngredientEntry entry, int index, Coord size) {
            CookbookAttribute main = entry.mainAttribute();
            Text.Forge nameFont = entry.spice() ? SPICE_TEXT :
                    (main == null ? ROW_TEXT : main.font());
            Text.Forge boostFont = entry.spice() ? SPICE_TEXT : ROW_MUTED;
            Widget row = new SListWidget.ItemWidget<CookbookIngredientEntry>(this, size, entry);
            row.add(new ResourceIconText(UI.scale(240, 30), entry.resourceName,
                    CookbookIngredientCatalog.meatIconOverlay(entry.name, entry.resourceName),
                    entry.name, nameFont), UI.scale(3, 0));
            row.add(SListWidget.TextItem.of(UI.scale(90, 30), ROW_MUTED,
                    () -> entry.category.label), UI.scale(250, 0));
            row.add(SListWidget.TextItem.of(UI.scale(245, 30), ROW_TEXT,
                    () -> compactAttributes(entry.averageFeps)), UI.scale(350, 0));
            row.add(SListWidget.TextItem.of(UI.scale(160, 30), boostFont,
                    () -> compactBoosts(entry)), UI.scale(610, 0));
            row.add(SListWidget.TextItem.of(UI.scale(55, 30), ROW_TEXT,
                    () -> Integer.toString(entry.recipeCount)), UI.scale(795, 0));
            return(row);
        }

        @Override
        public void change(CookbookIngredientEntry entry) {
            super.change(entry);
            showIngredientDetails(entry);
        }
    }

    private final class IngredientRecipeList extends
            SListBox<CookbookIngredientEntry.RecipeHighlight, Widget> {
        private IngredientRecipeList(Coord size) {
            super(size, UI.scale(48), UI.scale(2));
        }

        @Override
        protected List<? extends CookbookIngredientEntry.RecipeHighlight> items() {
            CookbookIngredientEntry selected = ingredientList.sel;
            return(selected == null ? java.util.Collections.emptyList() :
                    selected.recipeHighlights);
        }

        @Override
        protected Widget makeitem(CookbookIngredientEntry.RecipeHighlight recipe, int index,
                                  Coord size) {
            CookbookAttribute attribute = CookbookAttribute.forEvent(recipe.attribute);
            Text.Forge valueFont = attribute == null ? ROW_TEXT : attribute.font();
            String valueLabel = String.format(Locale.ROOT, "%s %.2f (Q10)",
                    attribute == null ? recipe.attribute : attribute.label, recipe.amount);
            Widget row = new SListWidget.ItemWidget<CookbookIngredientEntry.RecipeHighlight>(
                    this, size, recipe);
            row.add(new ResourceIconText(Coord.of(size.x - UI.scale(5), UI.scale(25)),
                    recipe.resourceName,
                    CookbookIngredientCatalog.meatIconOverlay(recipe.foodName,
                            recipe.resourceName),
                    recipe.foodName, ROW_TEXT), UI.scale(3, 0));
            row.add(SListWidget.TextItem.of(
                    Coord.of(size.x - UI.scale(35), UI.scale(21)), valueFont,
                    () -> valueLabel), UI.scale(32, 25));
            return(row);
        }
    }
}
