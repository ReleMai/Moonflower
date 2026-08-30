package haven.fishing;

import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.Indir;
import haven.MoonFlowerHudTheme;
import haven.Resource;
import haven.UI;
import haven.Utils;
import haven.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Animated, mouse-wheelable fish icon ribbon. */
final class FishingFishCarousel extends Widget {
    private static final int CELL = 70;
    private final Consumer<FishingNavigatorModel.FishSummary> selection;
    private final List<FishButton> buttons = new ArrayList<>();
    private double offset;
    private double fromOffset;
    private double targetOffset;
    private long glideStartedAt;
    private String signature = "";

    FishingFishCarousel(Coord size, Consumer<FishingNavigatorModel.FishSummary> selection) {
        super(size);
        this.selection = selection;
    }

    void setFish(List<FishingNavigatorModel.FishSummary> fish, String selectedKey) {
        StringBuilder state = new StringBuilder(selectedKey == null ? "" : selectedKey);
        for(FishingNavigatorModel.FishSummary value : fish)
            state.append('|').append(value.key).append(':').append(value.catchCount)
                    .append(':').append(value.offerCount).append(':').append(value.bestChance);
        String next = state.toString();
        if(next.equals(signature)) {
            for(FishButton button : buttons)
                button.selected = button.fish.key.equals(selectedKey);
            return;
        }
        signature = next;
        for(FishButton button : new ArrayList<>(buttons))
            button.reqdestroy();
        buttons.clear();
        for(FishingNavigatorModel.FishSummary value : fish)
            buttons.add(add(new FishButton(value), Coord.z));
        int selectedIndex = 0;
        for(int index = 0; index < buttons.size(); index++) {
            FishButton button = buttons.get(index);
            button.selected = button.fish.key.equals(selectedKey);
            if(button.selected)
                selectedIndex = index;
        }
        glideTo(centerOffset(selectedIndex));
        layout();
    }

    private double centerOffset(int index) {
        int cell = UI.scale(CELL);
        return(Utils.clip((index * cell) - ((sz.x - cell) / 2), 0,
                Math.max(0, (buttons.size() * cell) - sz.x)));
    }

    private void glideTo(double wanted) {
        fromOffset = offset;
        targetOffset = wanted;
        glideStartedAt = System.currentTimeMillis();
        if(FishingNavigatorUi.reducedMotion())
            offset = targetOffset;
    }

    private void layout() {
        int cell = UI.scale(CELL);
        for(int index = 0; index < buttons.size(); index++)
            buttons.get(index).move(Coord.of((int)Math.round(index * cell - offset), 0));
    }

    @Override
    public void draw(GOut g) {
        GOut viewport = g.reclip(Coord.z, sz);
        MoonFlowerHudTheme.drawPanel(viewport, Coord.z, sz, 198);
        viewport.chcolor(MoonFlowerHudTheme.GOLD);
        viewport.line(UI.scale(8, 4), Coord.of(sz.x - UI.scale(8), UI.scale(4)), Math.max(1, UI.scale(1)));
        viewport.chcolor();
        super.draw(viewport);
        /* Explicit edge masks keep animated labels from leaking through transparent chrome. */
        viewport.chcolor(new java.awt.Color(4, 18, 24, 245));
        viewport.frect(Coord.z, Coord.of(UI.scale(3), sz.y));
        viewport.frect(Coord.of(sz.x - UI.scale(3), 0), Coord.of(UI.scale(3), sz.y));
        viewport.chcolor();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(Math.abs(offset - targetOffset) < 0.5)
            return;
        double progress = FishingNavigatorUi.reducedMotion() ? 1.0 : Math.min(1.0,
                (System.currentTimeMillis() - glideStartedAt) / 280.0);
        offset = fromOffset + ((targetOffset - fromOffset) * FishingNavigatorUi.smooth(progress));
        layout();
    }

    @Override
    public boolean mousewheel(MouseWheelEvent event) {
        int cell = UI.scale(CELL);
        glideTo(Utils.clip(targetOffset + (event.a * cell), 0,
                Math.max(0, (buttons.size() * cell) - sz.x)));
        return(true);
    }

    private final class FishButton extends Widget {
        final FishingNavigatorModel.FishSummary fish;
        final Indir<Resource> resource;
        boolean selected;
        long selectedAt = System.currentTimeMillis();

        FishButton(FishingNavigatorModel.FishSummary fish) {
            super(UI.scale(CELL - 4, 68));
            this.fish = fish;
            resource = Resource.remote().load(fish.resource.isBlank() ? "gfx/invobjs/missing" : fish.resource);
            settip(fish.name + " · " + fish.catchCount + " catches · " + fish.offerCount + " offers");
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawSlot(g, Coord.z, sz, true, selected);
            FishingNavigatorUi.drawIcon(g, resource, Coord.of(sz.x / 2, UI.scale(25)), UI.scale(34));
            if(selected) {
                double pulse = FishingNavigatorUi.reducedMotion() ? 1.0 : 0.84 + 0.16 *
                        Math.sin((System.currentTimeMillis() - selectedAt) / 180.0);
                MoonFlowerHudTheme.drawCircularSlot(g, Coord.of(sz.x / 2, UI.scale(25)),
                        (int)Math.round(UI.scale(22) * pulse), true);
                MoonFlowerHudTheme.drawBlossom(g, Coord.of(sz.x - UI.scale(7), UI.scale(7)), UI.scale(4));
            }
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(54)), 0.5, 0.5,
                    "%s", FishingNavigatorUi.shortText(fish.name, 10));
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b != 1)
                return(false);
            selectedAt = System.currentTimeMillis();
            selection.accept(fish);
            return(true);
        }
    }
}
