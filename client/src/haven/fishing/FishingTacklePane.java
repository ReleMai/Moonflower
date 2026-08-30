package haven.fishing;

import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.UI;
import haven.Widget;
import haven.automated.FishingBot;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/** Reachable tackle selector and learned catch evidence for the selected rig. */
final class FishingTacklePane extends Widget {
    interface Listener {
        void catalogChanged();
        void notifyUser(String message);
    }

    private final Listener listener;
    private final TackleTimeline timeline;
    private final EvidencePanel evidence;
    private FishingBot helper;

    FishingTacklePane(Coord size, Listener listener) {
        super(size);
        this.listener = listener;
        timeline = add(new TackleTimeline(Coord.of(size.x, UI.scale(98))), Coord.z);
        evidence = add(new EvidencePanel(Coord.of(size.x, UI.scale(96))), UI.scale(0, 104));
    }

    void bindHelper(FishingBot helper) {
        this.helper = helper;
    }

    void setCatalog(FishingTackleCatalog catalog) {
        timeline.setCatalog(catalog);
    }

    void setResults(List<FishingNavigatorModel.RigFishResult> results, boolean complete,
                    boolean presetPreview) {
        evidence.setResults(results, complete, presetPreview);
    }

    private final class TackleTimeline extends Widget {
        private FishingTackleCatalog current = emptyCatalog();
        private final TackleStage[] stages = new TackleStage[4];
        private long threadStartedAt = System.currentTimeMillis();

        TackleTimeline(Coord size) {
            super(size);
            stages[0] = add(new TackleStage(Stage.POLE), UI.scale(8, 9));
            stages[1] = add(new TackleStage(Stage.LINE), UI.scale(158, 9));
            stages[2] = add(new TackleStage(Stage.HOOK), UI.scale(308, 9));
            stages[3] = add(new TackleStage(Stage.CONSUMABLE), UI.scale(458, 9));
        }

        void setCatalog(FishingTackleCatalog catalog) {
            current = catalog == null ? emptyCatalog() : catalog;
            long now = System.currentTimeMillis();
            for(TackleStage stage : stages)
                stage.changedAt = now;
            threadStartedAt = now;
        }

        @Override
        public void draw(GOut g) {
            for(int index = 0; index < stages.length; index++) {
                Coord center = stages[index].c.add(stages[index].sz.div(2));
                g.image(FishingNavigatorAssets.tackleSockets[index],
                        center.sub(FishingNavigatorAssets.tackleSockets[index].sz().div(2)));
            }
            double reveal = FishingNavigatorUi.reducedMotion() ? 1.0 : Math.min(1.0,
                    (System.currentTimeMillis() - threadStartedAt) / 460.0);
            g.chcolor(new Color(73, 174, 178, 220));
            int start = UI.scale(65);
            int end = UI.scale(525);
            int y = UI.scale(49);
            g.line(Coord.of(start, y), Coord.of(start + (int)Math.round((end - start) *
                    FishingNavigatorUi.smooth(reveal)), y), Math.max(1, UI.scale(2)));
            g.chcolor();
            super.draw(g);
        }

        private void cycle(Stage stage, int direction) {
            if(helper == null) {
                listener.notifyUser("The fishing helper is unavailable, so tackle cannot be changed.");
                return;
            }
            List<String> values = stage.values(current);
            if(values.isEmpty()) {
                listener.notifyUser("No reachable " + stage.label.toLowerCase(Locale.ROOT) +
                        " was found in your inventories, belt, or hands.");
                return;
            }
            String selected = stage.value(current);
            int index = Math.max(0, values.indexOf(selected));
            String next = values.get(Math.floorMod(index + direction, values.size()));
            String pole = stage == Stage.POLE ? next : current.pole;
            boolean lure = "Primitive Casting-Rod".equals(pole);
            String line = stage == Stage.LINE ? next : current.line;
            String hook = stage == Stage.HOOK ? next : current.hook;
            List<String> consumables = lure ? current.lures : current.baits;
            String consumable = stage == Stage.CONSUMABLE ? next :
                    (lure == current.lure && consumables.contains(current.consumable) ? current.consumable :
                            (consumables.isEmpty() ? "" : consumables.get(0)));
            if(helper.selectTackle(pole, line, hook, lure ? "lure" : "bait", consumable)) {
                listener.catalogChanged();
                threadStartedAt = System.currentTimeMillis();
            } else {
                listener.notifyUser("That tackle combination is not fully reachable right now.");
            }
        }

        private final class TackleStage extends Widget {
            final Stage stage;
            long changedAt;

            TackleStage(Stage stage) {
                super(UI.scale(124, 80));
                this.stage = stage;
                settip("Left-click for next; right-click for previous reachable " +
                        stage.label.toLowerCase(Locale.ROOT));
            }

            @Override
            public void draw(GOut g) {
                boolean active = !stage.value(current).isBlank();
                double bloom = FishingNavigatorUi.reducedMotion() ? 1.0 : Math.max(0, 1.0 -
                        ((System.currentTimeMillis() - changedAt) / 320.0));
                if(bloom > 0) {
                    g.chcolor(new Color(73, 174, 178, (int)Math.round(85 * bloom)));
                    FishingNavigatorUi.circleFill(g, Coord.of(sz.x / 2, sz.y / 2), UI.scale(33));
                    g.chcolor();
                }
                MoonFlowerHudTheme.drawCircularSlot(g, Coord.of(sz.x / 2, sz.y / 2), UI.scale(31), active);
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(22)), 0.5, 0.5, "%s", stage.label);
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(48)), 0.5, 0.5, "%s",
                        FishingNavigatorUi.shortText(stage.value(current).isBlank() ?
                                "Unavailable" : stage.value(current), 18));
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(64)), 0.5, 0.5, "<   >");
            }

            @Override
            public boolean mousedown(MouseDownEvent event) {
                if(event.b != 1 && event.b != 3)
                    return(false);
                changedAt = System.currentTimeMillis();
                cycle(stage, event.b == 1 ? 1 : -1);
                return(true);
            }
        }
    }

    private enum Stage {
        POLE("POLE"), LINE("LINE"), HOOK("HOOK"), CONSUMABLE("LURE / BAIT");
        final String label;

        Stage(String label) { this.label = label; }

        List<String> values(FishingTackleCatalog catalog) {
            switch(this) {
            case POLE: return(catalog.poles);
            case LINE: return(catalog.lines);
            case HOOK: return(catalog.hooks);
            case CONSUMABLE: return(catalog.consumables());
            default: return(List.of());
            }
        }

        String value(FishingTackleCatalog catalog) {
            switch(this) {
            case POLE: return(catalog.pole);
            case LINE: return(catalog.line);
            case HOOK: return(catalog.hook);
            case CONSUMABLE: return(catalog.consumable);
            default: return("");
            }
        }
    }

    private static final class EvidencePanel extends Widget {
        private List<FishingNavigatorModel.RigFishResult> results = List.of();
        private boolean complete;
        private boolean presetPreview;
        private String signature = "";
        private long changedAt;

        EvidencePanel(Coord size) {
            super(size);
        }

        void setResults(List<FishingNavigatorModel.RigFishResult> values, boolean complete,
                        boolean presetPreview) {
            String next = values + "|" + complete + "|" + presetPreview;
            if(!next.equals(signature)) {
                signature = next;
                changedAt = System.currentTimeMillis();
            }
            results = values == null ? List.of() : values;
            this.complete = complete;
            this.presetPreview = presetPreview;
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 214);
            int alpha = FishingNavigatorUi.reducedMotion() ? 255 : (int)Math.round(255 *
                    FishingNavigatorUi.smooth(Math.min(1.0,
                            (System.currentTimeMillis() - changedAt) / 240.0)));
            g.chcolor(255, 255, 255, alpha);
            FastText.aprintfstroked(g, UI.scale(9, 14), 0, 0.5, "%s",
                    presetPreview ? "POSSIBLE CATCHES · HIGHEST LEARNED %" :
                            "OBSERVED WITH THIS RIG · LEARNED");
            if(!complete) {
                FastText.aprintfstroked(g, UI.scale(9, 49), 0, 0.5,
                        "Choose one reachable pole, line, hook, and lure or bait.");
            } else if(results.isEmpty()) {
                FastText.aprintfstroked(g, UI.scale(9, 49), 0, 0.5,
                        "No observations for this rig - unknown, not zero.");
            } else {
                int shown = Math.min(6, results.size());
                int cell = (sz.x - UI.scale(14)) / shown;
                for(int index = 0; index < shown; index++) {
                    FishingNavigatorModel.RigFishResult result = results.get(index);
                    int x = UI.scale(7) + index * cell;
                    FastText.aprintfstroked(g, Coord.of(x + cell / 2, UI.scale(42)), 0.5, 0.5,
                            "%s", FishingNavigatorUi.shortText(result.name, 12));
                    FastText.aprintfstroked(g, Coord.of(x + cell / 2, UI.scale(64)), 0.5, 0.5,
                            "%s · %d obs", result.bestChance == null ? "chance ?" :
                                    result.bestChance + "%", result.observations);
                    if(result.catches > 0)
                        FastText.aprintfstroked(g, Coord.of(x + cell / 2, UI.scale(81)), 0.5, 0.5,
                                "%d caught", result.catches);
                }
            }
            g.chcolor();
        }
    }

    private static FishingTackleCatalog emptyCatalog() {
        return(new FishingTackleCatalog(List.of(), List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", false));
    }
}
