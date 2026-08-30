package haven.fishing;

import haven.Button;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.UI;
import haven.Widget;
import haven.automated.FishingBot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/** MoonFlower fish-first journal, location navigator, tackle evidence, and preset rack. */
final class FishingNavigatorWindow extends Widget {
    private static final int JOURNAL_LIMIT = 2000;
    private static final Coord SIZE = UI.scale(800, 560);
    private static final Color MUTED = new Color(166, 181, 185);

    private final FishingJournalService service;
    private final FishingMapMarkers mapMarkers;
    private final FishingPresetStore presetStore;
    private final FishingFishCarousel fishCarousel;
    private final FishingLocationPane locationPane;
    private final FishingTacklePane tacklePane;
    private final FishingPresetRail presetRail;
    private final Button allLocations;
    private final Button refreshButton;
    private final List<FishingObservation> observations = new ArrayList<>();
    private Future<List<FishingObservation>> query;
    private boolean queryDirty = true;
    private long displayedGeneration = -1;
    private String selectedFishKey = "";
    private FishingNavigatorModel.Snapshot snapshot = FishingNavigatorModel.build(List.of(), "", null);
    private FishingBot helper;
    private FishingTackleCatalog catalog = emptyCatalog();
    private FishingPolePreset previewPreset;
    private String catalogSignature = "";
    private long nextCatalogRefresh;
    private Long spotGridId;
    private int spotTileX;
    private int spotTileY;
    private List<Long> spotObservationIds = Collections.emptyList();
    private String status = "Loading learned fishing knowledge...";

    FishingNavigatorWindow(FishingJournalService service, FishingMapMarkers mapMarkers) {
        super(SIZE);
        this.service = service;
        this.mapMarkers = mapMarkers;
        presetStore = new FishingPresetStore(service.worldId());
        fishCarousel = add(new FishingFishCarousel(UI.scale(575, 70), this::selectFish), UI.scale(215, 4));
        locationPane = add(new FishingLocationPane(UI.scale(590, 238), this::showOnMap), UI.scale(10, 102));
        tacklePane = add(new FishingTacklePane(UI.scale(590, 200), new FishingTacklePane.Listener() {
            public void catalogChanged() { clearPresetPreviewAndRefresh(); }
            public void notifyUser(String message) { FishingNavigatorWindow.this.notifyUser(message); }
        }), UI.scale(10, 350));
        presetRail = add(new FishingPresetRail(UI.scale(180, 448), presetStore,
                new FishingPresetRail.Listener() {
                    public void catalogChanged() { clearPresetPreviewAndRefresh(); }
                    public void notifyUser(String message) { FishingNavigatorWindow.this.notifyUser(message); }
                    public void previewPreset(FishingPolePreset preset) {
                        previewPreset = preset;
                        rebuildSnapshot();
                    }
                }), UI.scale(610, 102));
        allLocations = add(new Button(UI.scale(104), "All records") {
            public void click() { clearSpotFilter(); }
        }, UI.scale(10, 73));
        allLocations.hide();
        refreshButton = add(new Button(UI.scale(72), "Refresh") {
            public void click() { refresh(); }
        }, UI.scale(122, 70));
    }

    void bindHelper(FishingBot helper) {
        this.helper = helper;
        tacklePane.bindHelper(helper);
        presetRail.bindHelper(helper);
        refreshCatalog(true);
        presetRail.setPresets(presetStore.load());
    }

    void refresh() {
        queryDirty = true;
        refreshCatalog(true);
    }

    void showSpot(FishingMapMarker marker) {
        if(marker == null)
            return;
        spotGridId = marker.gridId;
        spotTileX = marker.gridTileX;
        spotTileY = marker.gridTileY;
        spotObservationIds = marker.observationIds;
        allLocations.show();
        queryDirty = true;
    }

    private void clearSpotFilter() {
        spotGridId = null;
        spotObservationIds = Collections.emptyList();
        allLocations.hide();
        queryDirty = true;
    }

    public void tick(double dt) {
        super.tick(dt);
        if(!visible())
            return;
        if(displayedGeneration != service.generation())
            queryDirty = true;
        if(query != null && query.isDone())
            finishQuery();
        if(queryDirty && query == null) {
            queryDirty = false;
            query = spotGridId == null ? service.recent(JOURNAL_LIMIT) :
                    !spotObservationIds.isEmpty() ? service.observations(spotObservationIds) :
                            service.spot(spotGridId, spotTileX, spotTileY);
            status = spotGridId == null ? "Reading recent local observations..." :
                    "Reading the selected map cluster...";
        }
        refreshCatalog(false);
    }

    private void finishQuery() {
        try {
            List<FishingObservation> loaded = query.get();
            observations.clear();
            observations.addAll(loaded == null ? List.of() : loaded);
            rebuildSnapshot();
            displayedGeneration = service.generation();
            status = observations.isEmpty() ? "No learned fishing observations yet." :
                    observations.size() + " local observations · " + mapMarkers.markerCount() +
                            " detailed spots · " + mapMarkers.summaryCount() + " map summaries";
            if(mapMarkers.lastError() != null)
                status += " · map overlay unavailable";
        } catch(Exception failure) {
            status = service.lastError() == null ? "Could not load fishing knowledge." : service.lastError();
        } finally {
            query = null;
        }
    }

    private void refreshCatalog(boolean force) {
        if(helper == null)
            return;
        long now = System.currentTimeMillis();
        if(!force && now < nextCatalogRefresh)
            return;
        nextCatalogRefresh = now + 750;
        FishingTackleCatalog refreshed = helper.tackleCatalog();
        String signature = catalogSignature(refreshed);
        if(!force && signature.equals(catalogSignature))
            return;
        catalog = refreshed;
        catalogSignature = signature;
        tacklePane.setCatalog(refreshed);
        rebuildSnapshot();
    }

    private static String catalogSignature(FishingTackleCatalog value) {
        return(value.poles + "|" + value.lines + "|" + value.hooks + "|" + value.baits + "|" +
                value.lures + "|" + value.pole + "|" + value.line + "|" + value.hook + "|" +
                value.consumable + "|" + value.lure);
    }

    private void rebuildSnapshot() {
        FishingNavigatorModel.RigSpec displayedRig = previewPreset == null ? catalog.rig() : previewPreset.rig();
        FishingTackleCatalog displayedCatalog = previewPreset == null ? catalog : previewCatalog(previewPreset);
        snapshot = FishingNavigatorModel.build(observations, selectedFishKey, displayedRig);
        if(snapshot.selectedFish != null)
            selectedFishKey = snapshot.selectedFish.key;
        fishCarousel.setFish(snapshot.fish, selectedFishKey);
        locationPane.setSpots(snapshot.spots);
        tacklePane.setCatalog(displayedCatalog);
        tacklePane.setResults(snapshot.rigResults, displayedRig.complete(), previewPreset != null);
        presetRail.updateCurrent(catalog, snapshot.selectedFish);
    }

    private void clearPresetPreviewAndRefresh() {
        previewPreset = null;
        refreshCatalog(true);
    }

    private FishingTackleCatalog previewCatalog(FishingPolePreset preset) {
        return(new FishingTackleCatalog(catalog.poles, catalog.lines, catalog.hooks,
                catalog.baits, catalog.lures, preset.pole, preset.line, preset.hook,
                preset.consumable, "lure".equals(preset.consumableKind)));
    }

    private void selectFish(FishingNavigatorModel.FishSummary fish) {
        if(fish == null || fish.key.equals(selectedFishKey))
            return;
        selectedFishKey = fish.key;
        rebuildSnapshot();
    }

    private boolean showOnMap(FishingNavigatorModel.SpotSummary spot) {
        if(spot == null || ui == null || ui.gui == null)
            return(false);
        boolean shown = ui.gui.showFishingLocation(spot.gridId, spot.tileX, spot.tileY);
        ui.gui.msg(shown ? "Centered the map on this learned fishing location." :
                "The recorded map location is unavailable in the current map file.", Color.WHITE);
        return(shown);
    }

    public void draw(GOut g) {
        MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 228);
        g.image(FishingNavigatorAssets.tideglassMark, UI.scale(8, 3));
        FastText.aprintfstroked(g, UI.scale(78, 18), 0, 0.5, "%s",
                previewPreset == null ? "FISH GUIDE" : "PRESET OVERVIEW");
        FastText.aprintfstroked(g, UI.scale(78, 40), 0, 0.5,
                previewPreset == null ? "Navigate learned tides and refine your rig" :
                        "Reviewing " + FishingNavigatorUi.shortText(previewPreset.name, 34) +
                                " · no items moved");
        g.chcolor(MUTED);
        g.line(UI.scale(76, 56), UI.scale(205, 56), Math.max(1, UI.scale(1)));
        g.chcolor();
        FastText.aprintfstroked(g, UI.scale(120, 86), 0, 0.5, "%s",
                FishingNavigatorUi.shortText(status, 88));
        FastText.aprintfstroked(g, UI.scale(10, 96), 0, 1, "%s LOCATIONS",
                snapshot.selectedFish == null ? "FISH" :
                        FishingNavigatorUi.safe(snapshot.selectedFish.name.toUpperCase(Locale.ROOT)));
        FastText.aprintfstroked(g, UI.scale(310, 96), 0, 1, "LOCATION PREVIEW · LEARNED");
        FastText.aprintfstroked(g, UI.scale(610, 96), 0, 1, "PRESET CRESTS");
        super.draw(g);
    }

    public boolean checkhit(Coord c) { return(true); }

    public boolean mousedown(MouseDownEvent event) { return(true); }

    private void notifyUser(String message) {
        status = message;
        if(ui != null && ui.gui != null)
            ui.gui.msg(message, Color.WHITE);
    }

    private static FishingTackleCatalog emptyCatalog() {
        return(new FishingTackleCatalog(List.of(), List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", false));
    }
}
