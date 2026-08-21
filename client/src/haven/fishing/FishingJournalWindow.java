package haven.fishing;

import haven.Button;
import haven.CharWnd;
import haven.Coord;
import haven.Indir;
import haven.Label;
import haven.Loading;
import haven.PUtils;
import haven.Resource;
import haven.RichText;
import haven.RichTextBox;
import haven.SListBox;
import haven.SListWidget;
import haven.Text;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import static haven.MCache.tilesz;

/** Fish-group browser with date/time catches, opt-in details, and map-spot filtering. */
public final class FishingJournalWindow extends Window {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Text.Foundry ROW_TEXT =
            new Text.Foundry(Text.sans, 11, Color.WHITE).aa(true);

    private final FishingJournalService service;
    private final FishingMapMarkers mapMarkers;
    private final Label status;
    private final Label catchesHeading;
    private final Button allCatches;
    private final FishGroupList groupList;
    private final FishingCatchList catchList;
    private final RichTextBox details;
    private final List<FishingObservation> observations = new ArrayList<>();
    private final List<FishGroup> groups = new ArrayList<>();
    private Future<List<FishingObservation>> query;
    private boolean queryDirty = true;
    private long displayedGeneration = -1;
    private int displayedMarkerCount = -1;
    private int displayedUnresolvedCount = -1;
    private String displayedMarkerError;
    private Long spotGridId;
    private int spotTileX;
    private int spotTileY;

    public FishingJournalWindow(FishingJournalService service, FishingMapMarkers mapMarkers) {
        super(UI.scale(780, 520), "Fishing Journal");
        this.service = service;
        this.mapMarkers = mapMarkers;
        add(new Button(UI.scale(75), "Refresh") {
            @Override
            public void click() {
                queryDirty = true;
            }
        }, UI.scale(10, 8));
        allCatches = add(new Button(UI.scale(90), "All catches") {
            @Override
            public void click() {
                clearSpot();
            }
        }, UI.scale(95, 8));
        allCatches.hide();
        status = add(new Label("Loading recent catches..."), UI.scale(195, 12));

        add(new Label("Fish types"), UI.scale(10, 39));
        catchesHeading = add(new Label("Catches by date and time — select a fish"), UI.scale(270, 39));
        groupList = add(new FishGroupList(UI.scale(250, 170)), UI.scale(10, 58));
        catchList = add(new FishingCatchList(UI.scale(490, 170)), UI.scale(270, 58));

        add(new Label("Catch details and quality factors"), UI.scale(10, 238));
        RichText.Foundry journalText = new RichText.Foundry(RichText.IMAGESRC,
                RichText.ImageSource.res(Resource.remote()));
        details = add(new RichTextBox(UI.scale(750, 250),
                "Select a fish type, then click a dated catch to inspect it.", journalText),
                UI.scale(10, 258));
        reqclose(this::hide);
    }

    public void refresh() {
        queryDirty = true;
    }

    public void showSpot(FishingMapMarker marker) {
        if(marker == null)
            return;
        spotGridId = marker.gridId;
        spotTileX = marker.gridTileX;
        spotTileY = marker.gridTileY;
        allCatches.show();
        clearSelection();
        queryDirty = true;
        show();
        raise();
    }

    private void clearSpot() {
        spotGridId = null;
        allCatches.hide();
        clearSelection();
        queryDirty = true;
    }

    private void clearSelection() {
        groupList.change(null);
    }

    @Override
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
            query = spotGridId == null ? service.recent(100) :
                    service.spot(spotGridId, spotTileX, spotTileY);
            status.settext(spotGridId == null ? "Loading recent catches..." :
                    "Loading catches at this map spot...");
        } else if(query == null && markerStatusChanged()) {
            updateStatus();
        }
    }

    private void finishQuery() {
        String selectedGroupKey = groupList.sel == null ? null : groupList.sel.key;
        Long selectedCatchId = catchList.sel == null ? null : catchList.sel.id;
        try {
            List<FishingObservation> loaded = query.get();
            observations.clear();
            observations.addAll(loaded == null ? Collections.emptyList() : loaded);
            rebuildGroups();
            groupList.reset();
            FishGroup selectedGroup = selectedGroupKey == null ? null : groups.stream()
                    .filter(group -> group.key.equals(selectedGroupKey)).findFirst().orElse(null);
            groupList.change(selectedGroup);
            FishingObservation selectedCatch = selectedGroup == null || selectedCatchId == null ? null :
                    selectedGroup.catches.stream()
                            .filter(observation -> observation.id == selectedCatchId.longValue())
                            .findFirst().orElse(null);
            catchList.change(selectedCatch);
            updateStatus();
            displayedGeneration = service.generation();
        } catch(Exception failure) {
            status.settext(service.lastError() == null ? "Could not load fishing journal." :
                    service.lastError());
        } finally {
            query = null;
        }
    }

    private void rebuildGroups() {
        Map<String, FishGroup> grouped = new LinkedHashMap<>();
        for(FishingObservation observation : observations) {
            String key = fishKey(observation);
            FishGroup group = grouped.computeIfAbsent(key, ignored -> new FishGroup(key, observation));
            group.add(observation);
        }
        groups.clear();
        groups.addAll(grouped.values());
    }

    private boolean markerStatusChanged() {
        return(displayedMarkerCount != mapMarkers.markerCount() ||
                displayedUnresolvedCount != mapMarkers.unresolvedCount() ||
                !Objects.equals(displayedMarkerError, mapMarkers.lastError()));
    }

    private void updateStatus() {
        displayedMarkerCount = mapMarkers.markerCount();
        displayedUnresolvedCount = mapMarkers.unresolvedCount();
        displayedMarkerError = mapMarkers.lastError();
        if(!service.available() && service.lastError() != null)
            status.settext(service.lastError());
        else
            status.settext(statusText());
    }

    private String statusText() {
        StringBuilder text = new StringBuilder();
        text.append(observations.size()).append(observations.size() == 1 ? " catch" : " catches")
                .append(" in ").append(groups.size()).append(groups.size() == 1 ? " fish type" : " fish types");
        if(spotGridId != null)
            text.append(" at this spot");
        if(mapMarkers.lastError() != null)
            text.append(" | map markers unavailable");
        else {
            text.append(" | ").append(mapMarkers.markerCount())
                    .append(mapMarkers.markerCount() == 1 ? " map spot" : " map spots");
            if(mapMarkers.unresolvedCount() > 0)
                text.append(" (").append(mapMarkers.unresolvedCount()).append(" pending)");
        }
        return(text.toString());
    }

    private void showDetails(FishingObservation observation) {
        if(observation == null) {
            details.settext(groupList.sel == null ?
                    "Select a fish type, then click a dated catch to inspect it." :
                    "Click a dated catch above to inspect its details and quality factors.");
            return;
        }
        String quality = quality(observation.fishQuality);
        StringBuilder text = new StringBuilder();
        if(!observation.fishResource.isBlank())
            text.append("$img[").append(observation.fishResource).append(",h=36]\n");
        text.append("$b{").append(quote(observation.fishName)).append("} ").append(quality)
                .append(" [").append(quote(observation.confidence)).append("]\n")
                .append(quote(CLOCK.format(Instant.ofEpochMilli(observation.observedAt))))
                .append(" | Game day ").append(observation.gameDay).append(' ')
                .append(formatGameTime(observation.gameSecondOfDay))
                .append(observation.night ? " | Night" : " | Day");
        if(!observation.moonPhase.isEmpty())
            text.append(" | ").append(quote(observation.moonPhase));
        if(!observation.season.isEmpty())
            text.append(" | ").append(quote(observation.season));
        text.append("\nWater: ").append(quote(observation.waterResource))
                .append("\nPole: ").append(itemDetail(observation.poleName, observation.poleQuality,
                        observation.poleResource))
                .append("\nLine: ").append(itemDetail(observation.lineName, observation.lineQuality,
                        observation.lineResource))
                .append("\nHook: ").append(itemDetail(observation.hookName, observation.hookQuality,
                        observation.hookResource))
                .append("\n").append(quote(capitalize(observation.consumableKind))).append(": ")
                .append(itemDetail(observation.consumableName, observation.consumableQuality,
                        observation.consumableResource));
        if(observation.survival != null || observation.will != null)
            text.append("\nStats: Survival ").append(value(observation.survival))
                    .append(" | Will ").append(value(observation.will));
        text.append("\nOutcome: ").append(quote(observation.outcome));
        appendQualityFactors(text, observation);
        if(!observation.choiceRowsJson.isBlank() && !"[]".equals(observation.choiceRowsJson))
            text.append("\nFishing choices: ").append(quote(observation.choiceRowsJson));
        details.settext(text.toString());
    }

    private void appendQualityFactors(StringBuilder text, FishingObservation observation) {
        FishingQualityAnalysis.Result analysis = FishingQualityAnalysis.analyze(observation);
        text.append("\n\n$b{What affected this fish's quality}\n")
                .append("Fishing spot/node: hidden server quality at this map tile; this is the primary unknown input.");
        if(analysis.tackleAverage != null)
            text.append("\nRecorded tackle average: ")
                    .append(String.format(Locale.ROOT, "Q%.1f", analysis.tackleAverage))
                    .append(" (softcap estimate; the current server formula is not exposed).");
        else
            text.append("\nRecorded tackle average: unavailable because one or more qualities were not readable.");
        if(analysis.weakestQuality != null)
            text.append("\nWeakest tackle: ").append(quote(String.join(", ", analysis.weakestFactors)))
                    .append(String.format(Locale.ROOT, " Q%.1f", analysis.weakestQuality))
                    .append(" — the clearest gear-quality improvement.");
        for(FishingQualityAnalysis.Factor factor : analysis.factors) {
            text.append("\n  ").append(quote(factor.label)).append(": ")
                    .append(quote(factor.name.isBlank() ? "unknown" : factor.name)).append(' ')
                    .append(quality(factor.quality));
        }
        QualityHistory history = qualityHistory(observation);
        if(history.count > 0)
            text.append("\nSame fish at this exact spot: ")
                    .append(history.count).append(history.count == 1 ? " catch" : " catches")
                    .append(" ranging ")
                    .append(String.format(Locale.ROOT, "Q%.1f–Q%.1f", history.minimum, history.maximum));
        text.append("\nSelection context: tackle types, water tile, time, moon, Survival ")
                .append(value(observation.survival)).append(", and Will ").append(value(observation.will))
                .append(" affect which fish appears or its catch chance; they are shown separately because the client cannot prove a direct quality contribution.");
    }

    private QualityHistory qualityHistory(FishingObservation selected) {
        int count = 0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for(FishingObservation observation : observations) {
            if(observation.fishQuality == null || !fishKey(observation).equals(fishKey(selected)) ||
                    !sameSpot(observation, selected))
                continue;
            count++;
            minimum = Math.min(minimum, observation.fishQuality);
            maximum = Math.max(maximum, observation.fishQuality);
        }
        return(new QualityHistory(count, minimum, maximum));
    }

    private static boolean sameSpot(FishingObservation left, FishingObservation right) {
        return(left.gridId == right.gridId &&
                (int)Math.floor(left.gridOffsetX / tilesz.x) ==
                        (int)Math.floor(right.gridOffsetX / tilesz.x) &&
                (int)Math.floor(left.gridOffsetY / tilesz.y) ==
                        (int)Math.floor(right.gridOffsetY / tilesz.y));
    }

    private final class FishGroupList extends SListBox<FishGroup, Widget> {
        FishGroupList(Coord size) {
            super(size, UI.scale(42), UI.scale(2));
        }

        @Override
        protected List<? extends FishGroup> items() {
            return(groups);
        }

        @Override
        protected Widget makeitem(FishGroup group, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<FishGroup>(this, size, group);
            row.add(new FishGroupItem(size, group), Coord.z);
            return(row);
        }

        @Override
        public void change(FishGroup group) {
            super.change(group);
            catchesHeading.settext(group == null ? "Catches by date and time — select a fish" :
                    "Catches by date and time — " + group.name);
            catchList.reset();
            catchList.change(null);
        }
    }

    private final class FishingCatchList extends SListBox<FishingObservation, Widget> {
        FishingCatchList(Coord size) {
            super(size, UI.scale(38), UI.scale(2));
        }

        @Override
        protected List<? extends FishingObservation> items() {
            return(groupList.sel == null ? Collections.emptyList() : groupList.sel.catches);
        }

        @Override
        protected Widget makeitem(FishingObservation observation, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<FishingObservation>(this, size, observation);
            row.add(new FishCatchItem(size, observation), Coord.z);
            return(row);
        }

        @Override
        public void change(FishingObservation observation) {
            super.change(observation);
            showDetails(observation);
        }
    }

    private static final class FishGroupItem extends FishIconText {
        private final FishGroup group;

        FishGroupItem(Coord size, FishGroup group) {
            super(size, group.resource);
            this.group = group;
        }

        @Override
        protected String text() {
            String range = group.qualityCount == 0 ? "Q?" :
                    String.format(Locale.ROOT, "Q%.1f–%.1f", group.minimumQuality, group.maximumQuality);
            return(group.name + " (" + group.catches.size() + ") | " + range +
                    " | " + DATE.format(Instant.ofEpochMilli(group.latest.observedAt)));
        }
    }

    private static final class FishCatchItem extends FishIconText {
        private final FishingObservation observation;

        FishCatchItem(Coord size, FishingObservation observation) {
            super(size, observation.fishResource);
            this.observation = observation;
        }

        @Override
        protected String text() {
            String bait = observation.consumableName.isBlank() ? "unknown bait" : observation.consumableName;
            Instant caught = Instant.ofEpochMilli(observation.observedAt);
            return(DATE.format(caught) + "  " + TIME.format(caught) + "  |  " +
                    quality(observation.fishQuality) + "  |  " + bait);
        }
    }

    private abstract static class FishIconText extends SListWidget.IconText {
        private final Indir<Resource> resource;

        FishIconText(Coord size, String resource) {
            super(size);
            this.resource = Resource.remote().load(resource == null || resource.isBlank() ?
                    "gfx/invobjs/missing" : resource);
        }

        @Override
        protected BufferedImage img() {
            try {
                return(resource.get().flayer(Resource.imgc).img);
            } catch(Loading loading) {
                throw(loading);
            } catch(RuntimeException failure) {
                return(WItem.missing.flayer(Resource.imgc).img);
            }
        }

        @Override
        protected Text.Forge foundry() {
            return(ROW_TEXT);
        }

        @Override
        protected int margin() {
            return(UI.scale(3));
        }

        @Override
        protected PUtils.Convolution filter() {
            return(CharWnd.iconfilter);
        }
    }

    private static final class FishGroup {
        final String key;
        final String name;
        final String resource;
        final FishingObservation latest;
        final List<FishingObservation> catches = new ArrayList<>();
        int qualityCount;
        double minimumQuality = Double.POSITIVE_INFINITY;
        double maximumQuality = Double.NEGATIVE_INFINITY;

        FishGroup(String key, FishingObservation latest) {
            this.key = key;
            this.latest = latest;
            this.name = latest.fishName.isBlank() ? "Unknown fish" : latest.fishName;
            this.resource = latest.fishResource;
        }

        void add(FishingObservation observation) {
            catches.add(observation);
            if(observation.fishQuality != null) {
                qualityCount++;
                minimumQuality = Math.min(minimumQuality, observation.fishQuality);
                maximumQuality = Math.max(maximumQuality, observation.fishQuality);
            }
        }
    }

    private static final class QualityHistory {
        final int count;
        final double minimum;
        final double maximum;

        QualityHistory(int count, double minimum, double maximum) {
            this.count = count;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static String fishKey(FishingObservation observation) {
        if(observation == null)
            return("");
        return(observation.fishResource.isBlank() ?
                observation.fishName.toLowerCase(Locale.ROOT) : observation.fishResource);
    }

    private static String quality(Double value) {
        return(value == null ? "Q?" : String.format(Locale.ROOT, "Q%.1f", value));
    }

    private static String itemDetail(String name, Double quality, String resource) {
        StringBuilder text = new StringBuilder(quote(name));
        text.append(quality == null ? " Q?" : String.format(Locale.ROOT, " Q%.1f", quality));
        if(resource != null && !resource.isBlank())
            text.append(" (").append(quote(resource)).append(')');
        return(text.toString());
    }

    private static String value(Integer value) {
        return(value == null ? "?" : Integer.toString(value));
    }

    private static String capitalize(String value) {
        if(value == null || value.isBlank())
            return("Bait or lure");
        return(Character.toUpperCase(value.charAt(0)) + value.substring(1));
    }

    private static String formatGameTime(int secondOfDay) {
        int normalized = Math.floorMod(secondOfDay, 24 * 60 * 60);
        return(String.format(Locale.ROOT, "%02d:%02d", normalized / 3600,
                (normalized % 3600) / 60));
    }

    private static String quote(String value) {
        return(RichText.Parser.quote(value == null ? "" : value));
    }
}
