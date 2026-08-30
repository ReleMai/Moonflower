package haven.fishing;

import haven.Button;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.SListBox;
import haven.SListWidget;
import haven.UI;
import haven.Widget;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/** Ranked fish-location rail and animated Tideglass coordinate preview. */
final class FishingLocationPane extends Widget {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final Function<FishingNavigatorModel.SpotSummary, Boolean> mapAction;
    private final SpotList spotList;
    private final LocationPreview preview;

    FishingLocationPane(Coord size, Function<FishingNavigatorModel.SpotSummary, Boolean> mapAction) {
        super(size);
        this.mapAction = mapAction;
        spotList = add(new SpotList(UI.scale(290, 238)), Coord.z);
        preview = add(new LocationPreview(UI.scale(290, 238)), UI.scale(300, 0));
    }

    void setSpots(List<FishingNavigatorModel.SpotSummary> values) {
        FishingNavigatorModel.SpotSummary previous = spotList.sel;
        spotList.setSpots(values);
        FishingNavigatorModel.SpotSummary selected = match(previous, values);
        if(selected == null && values != null && !values.isEmpty())
            selected = values.get(0);
        spotList.change(selected);
    }

    private static FishingNavigatorModel.SpotSummary match(FishingNavigatorModel.SpotSummary wanted,
                                                            List<FishingNavigatorModel.SpotSummary> values) {
        if(wanted == null || values == null)
            return(null);
        for(FishingNavigatorModel.SpotSummary value : values) {
            if(value.gridId == wanted.gridId && value.tileX == wanted.tileX && value.tileY == wanted.tileY)
                return(value);
        }
        return(null);
    }

    private final class SpotList extends SListBox<FishingNavigatorModel.SpotSummary, Widget> {
        private List<FishingNavigatorModel.SpotSummary> spots = List.of();

        SpotList(Coord size) {
            super(size, UI.scale(56), UI.scale(3));
        }

        void setSpots(List<FishingNavigatorModel.SpotSummary> values) {
            spots = values == null ? List.of() : values;
            reset();
        }

        @Override
        protected List<? extends FishingNavigatorModel.SpotSummary> items() { return(spots); }

        @Override
        protected Widget makeitem(FishingNavigatorModel.SpotSummary spot, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<FishingNavigatorModel.SpotSummary>(this, size, spot);
            row.add(new SpotRow(size, spot, index), Coord.z);
            return(row);
        }

        @Override
        public void change(FishingNavigatorModel.SpotSummary spot) {
            super.change(spot);
            preview.setSpot(spot);
        }
    }

    private final class SpotRow extends Widget {
        final FishingNavigatorModel.SpotSummary spot;
        final int rank;
        final Button mapButton;
        final long createdAt = System.currentTimeMillis();

        SpotRow(Coord size, FishingNavigatorModel.SpotSummary spot, int index) {
            super(size);
            this.spot = spot;
            rank = index + 1;
            mapButton = add(new Button(UI.scale(34), "Map") {
                @Override
                public void click() { mapAction.apply(spot); }
            }, Coord.of(size.x - UI.scale(39), UI.scale(13)));
            mapButton.settip("Center this recorded location on the map");
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            mapButton.c.x = sz.x - UI.scale(39) + slideOffset();
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, spotList.sel == spot ? 226 : 194);
            int slide = slideOffset();
            if(rank == 1) {
                g.chcolor(new Color(29, 117, 122, 90));
                g.frect(Coord.z, sz);
                g.chcolor();
            }
            FastText.aprintfstroked(g, UI.scale(7, 13).add(slide, 0), 0, 0.5, "%d", rank);
            FastText.aprintfstroked(g, UI.scale(28, 13).add(slide, 0), 0, 0.5, "%s · %d%%",
                    rank == 1 ? "BEST RECORDED" : "Learned spot", spot.bestChance);
            FastText.aprintfstroked(g, UI.scale(28, 34).add(slide, 0), 0, 0.5,
                    "%d samples · %d catches · tile %d,%d",
                    spot.samples, spot.catches, spot.tileX, spot.tileY);
            super.draw(g);
        }

        private int slideOffset() {
            if(FishingNavigatorUi.reducedMotion())
                return(0);
            double delay = Math.min(rank - 1, 5) * 35.0;
            double progress = Math.max(0, Math.min(1.0,
                    (System.currentTimeMillis() - createdAt - delay) / 240.0));
            return((int)Math.round(-UI.scale(24) * (1.0 - FishingNavigatorUi.smooth(progress))));
        }
    }

    private final class LocationPreview extends Widget {
        private FishingNavigatorModel.SpotSummary spot;
        private final Button centerMap;
        private long changedAt;

        LocationPreview(Coord size) {
            super(size);
            centerMap = add(new Button(UI.scale(130), "Center map") {
                @Override
                public void click() { mapAction.apply(spot); }
            }, Coord.of((size.x - UI.scale(130)) / 2, size.y - UI.scale(31)));
            centerMap.hide();
        }

        void setSpot(FishingNavigatorModel.SpotSummary spot) {
            if(this.spot != spot)
                changedAt = System.currentTimeMillis();
            this.spot = spot;
            if(spot == null)
                centerMap.hide();
            else
                centerMap.show();
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 208);
            Coord ring = Coord.of((sz.x - FishingNavigatorAssets.locatorRing.sz().x) / 2, UI.scale(5));
            if(spot != null) {
                Coord center = ring.add(FishingNavigatorAssets.locatorRing.sz().div(2));
                drawTideglassWater(g, center, UI.scale(69));
                drawLocatorPulses(g, center);
            }
            g.image(FishingNavigatorAssets.locatorRing, ring);
            if(spot == null) {
                FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5, "Select a learned location");
            } else {
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(87)), 0.5, 0.5,
                        "%d%%", spot.bestChance);
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(174)), 0.5, 0.5,
                        "Grid %d · tile %d,%d", spot.gridId, spot.tileX, spot.tileY);
                FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(191)), 0.5, 0.5,
                        "%s · %s", FishingNavigatorUi.waterLabel(spot.waterResource),
                        CLOCK.format(Instant.ofEpochMilli(spot.latestObservedAt)));
            }
            super.draw(g);
        }

        private void drawTideglassWater(GOut g, Coord center, int radius) {
            g.chcolor(new Color(3, 17, 24, 235));
            FishingNavigatorUi.circleFill(g, center, radius);
            g.chcolor(new Color(23, 91, 101, 125));
            for(int y = -radius + UI.scale(8); y < radius; y += UI.scale(10)) {
                int span = (int)Math.sqrt(Math.max(0, radius * radius - y * y));
                g.line(center.add(-span, y), center.add(span, y), Math.max(1, UI.scale(1)));
            }
            g.chcolor();
        }

        private void drawLocatorPulses(GOut g, Coord center) {
            if(FishingNavigatorUi.reducedMotion()) {
                FishingNavigatorUi.ring(g, center, UI.scale(18), MoonFlowerHudTheme.TEAL_BRIGHT, 210);
                return;
            }
            double time = (System.currentTimeMillis() - changedAt) / 900.0;
            for(int index = 0; index < 3; index++) {
                double phase = (time + index / 3.0) % 1.0;
                int radius = UI.scale(10) + (int)Math.round(UI.scale(48) * phase);
                FishingNavigatorUi.ring(g, center, radius, MoonFlowerHudTheme.TEAL_BRIGHT,
                        (int)Math.round(210 * (1.0 - phase)));
            }
            MoonFlowerHudTheme.drawBlossom(g, center, UI.scale(4));
        }
    }
}
