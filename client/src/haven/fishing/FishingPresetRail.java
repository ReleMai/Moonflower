package haven.fishing;

import haven.Button;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.SListBox;
import haven.SListWidget;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.automated.FishingBot;

import java.util.List;

/** Animated local rack for named exact-tackle presets. */
final class FishingPresetRail extends Widget {
    interface Listener {
        void catalogChanged();
        void notifyUser(String message);
        void previewPreset(FishingPolePreset preset);
    }

    private final FishingPresetStore store;
    private final Listener listener;
    private List<FishingPolePreset> presets = List.of();
    private FishingPolePreset selected;
    private final PresetList list;
    private final TextEntry name;
    private FishingTackleCatalog current = emptyCatalog();
    private FishingBot helper;
    private long savedAt;

    FishingPresetRail(Coord size, FishingPresetStore store, Listener listener) {
        super(size);
        this.store = store;
        this.listener = listener;
        name = add(new TextEntry(UI.scale(108), "") {{ canactivate = true; }}, UI.scale(5, 8));
        add(new Button(UI.scale(56), "Save") {
            @Override
            public void click() { saveCurrent(); }
        }, UI.scale(117, 7));
        list = add(new PresetList(UI.scale(170, 300)), UI.scale(5, 42));
        add(new Button(UI.scale(170), "Apply & prepare") {
            @Override
            public void click() { applySelected(); }
        }, UI.scale(5, 350));
        add(new Button(UI.scale(82), "Select") {
            @Override
            public void click() { selectPreset(); }
        }, UI.scale(5, 384));
        add(new Button(UI.scale(82), "Delete") {
            @Override
            public void click() { deleteSelected(); }
        }, UI.scale(93, 384));
    }

    void bindHelper(FishingBot helper) {
        this.helper = helper;
    }

    void setPresets(List<FishingPolePreset> presets) {
        this.presets = presets == null ? List.of() : presets;
        list.reset();
        if(selected != null)
            selected = this.presets.stream().filter(value ->
                    value.name.equalsIgnoreCase(selected.name)).findFirst().orElse(null);
        list.change(selected);
    }

    void updateCurrent(FishingTackleCatalog catalog, FishingNavigatorModel.FishSummary fish) {
        current = catalog == null ? emptyCatalog() : catalog;
        if(name.text().isBlank() && fish != null)
            name.settext(fish.name + " Rig");
    }

    private void saveCurrent() {
        FishingPolePreset preset = new FishingPolePreset(name.text(), current.pole, current.line,
                current.hook, current.lure ? "lure" : "bait", current.consumable);
        if(!preset.complete()) {
            listener.notifyUser("Name the preset and choose a complete reachable tackle setup.");
            return;
        }
        setPresets(store.save(presets, preset));
        selected = presets.get(0);
        list.change(selected);
        savedAt = System.currentTimeMillis();
        listener.notifyUser("Saved Tideglass preset '" + preset.name + "'.");
    }

    private void selectPreset() {
        if(selected == null) {
            listener.notifyUser("Choose a saved preset first.");
            return;
        }
        if(helper == null) {
            listener.notifyUser("The fishing helper is unavailable, so the preset cannot be selected.");
            return;
        }
        if(helper.selectTackle(selected.pole, selected.line, selected.hook,
                selected.consumableKind, selected.consumable)) {
            listener.catalogChanged();
            listener.notifyUser("Selected preset '" + selected.name + "'.");
        } else {
            listener.notifyUser("Preset components are not all reachable; nothing was moved.");
        }
    }

    private void applySelected() {
        if(selected == null) {
            listener.notifyUser("Choose a saved preset first.");
            return;
        }
        if(helper == null) {
            listener.notifyUser("The fishing helper is unavailable, so the preset cannot be prepared.");
            return;
        }
        if(!helper.selectTackle(selected.pole, selected.line, selected.hook,
                selected.consumableKind, selected.consumable)) {
            listener.notifyUser("Preset components are not all reachable; nothing was moved.");
            return;
        }
        helper.prepareSelectedTackle();
        listener.catalogChanged();
        listener.notifyUser("Preparing '" + selected.name + "' through the verified helper transaction.");
    }

    private void deleteSelected() {
        if(selected == null) {
            listener.notifyUser("Choose a saved preset first.");
            return;
        }
        String deleted = selected.name;
        setPresets(store.delete(presets, selected));
        selected = null;
        list.change(null);
        listener.notifyUser("Deleted local preset '" + deleted + "'.");
    }

    @Override
    public void draw(GOut g) {
        MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 214);
        if(savedAt > 0) {
            double phase = Math.min(1.0, (System.currentTimeMillis() - savedAt) / 600.0);
            if(phase < 1.0 && !FishingNavigatorUi.reducedMotion()) {
                FishingNavigatorUi.ring(g, Coord.of(sz.x / 2, UI.scale(27)), UI.scale(12) +
                        (int)Math.round(UI.scale(28) * phase), MoonFlowerHudTheme.TEAL_BRIGHT,
                        (int)Math.round(220 * (1.0 - phase)));
            } else if(FishingNavigatorUi.reducedMotion() && phase < 1.0) {
                MoonFlowerHudTheme.drawBlossom(g, Coord.of(sz.x - UI.scale(9), UI.scale(16)), UI.scale(4));
            }
        }
        FastText.aprintfstroked(g, Coord.of(sz.x / 2, sz.y - UI.scale(17)), 0.5, 0.5,
                "%d / %d local presets", presets.size(), FishingPresetStore.MAX_PRESETS);
        super.draw(g);
    }

    private final class PresetList extends SListBox<FishingPolePreset, Widget> {
        PresetList(Coord size) {
            super(size, UI.scale(56), UI.scale(3));
        }

        @Override
        protected List<? extends FishingPolePreset> items() { return(presets); }

        @Override
        protected Widget makeitem(FishingPolePreset preset, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<FishingPolePreset>(this, size, preset);
            row.add(new PresetCard(size, preset, index), Coord.z);
            return(row);
        }

        @Override
        public void change(FishingPolePreset preset) {
            super.change(preset);
            selected = preset;
            listener.previewPreset(preset);
        }
    }

    private final class PresetCard extends Widget {
        final FishingPolePreset preset;
        final int index;
        final long createdAt = System.currentTimeMillis();

        PresetCard(Coord size, FishingPolePreset preset, int index) {
            super(size);
            this.preset = preset;
            this.index = index;
            settip(preset.summary());
        }

        @Override
        public void draw(GOut g) {
            double progress = FishingNavigatorUi.reducedMotion() ? 1.0 : Math.min(1.0,
                    (System.currentTimeMillis() - createdAt - Math.min(index, 5) * 30.0) / 220.0);
            int slide = (int)Math.round(UI.scale(22) * (1.0 - FishingNavigatorUi.smooth(progress)));
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, selected == preset ? 224 : 190);
            MoonFlowerHudTheme.drawCircularSlot(g, UI.scale(22, 27).add(slide, 0), UI.scale(18),
                    selected == preset);
            FastText.aprintfstroked(g, UI.scale(22, 27).add(slide, 0), 0.5, 0.5, "%d", index + 1);
            FastText.aprintfstroked(g, UI.scale(47, 16).add(slide, 0), 0, 0.5, "%s",
                    FishingNavigatorUi.shortText(preset.name, 16));
            FastText.aprintfstroked(g, UI.scale(47, 36).add(slide, 0), 0, 0.5, "%s",
                    FishingNavigatorUi.shortText(preset.consumable, 15));
        }
    }

    private static FishingTackleCatalog emptyCatalog() {
        return(new FishingTackleCatalog(List.of(), List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", false));
    }
}
