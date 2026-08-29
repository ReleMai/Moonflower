package haven.fishing;

import haven.Button;
import haven.CharWnd;
import haven.Coord;
import haven.GOut;
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
import haven.Utils;
import haven.WItem;
import haven.Widget;
import org.json.JSONArray;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;

import static haven.MCache.tilesz;

/** Fish-group browser with date/time catches, opt-in details, and map-spot filtering. */
public final class FishingJournalWindow extends Widget {
    private static final int JOURNAL_LIMIT = 2000;
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
    private final Label fishTypesHeading;
    private final Label catchesHeading;
    private final Label detailsHeading;
    private final Button allCatches;
    private final Button bookmarkButton;
    private final Map<View, JournalTabButton> viewButtons = new EnumMap<>(View.class);
    private final FishGroupList groupList;
    private final FishingCatchList catchList;
    private final RichTextBox details;
    private final RichTextBox analysisInfo;
    private final LocationEntryList locationList;
    private final RichTextBox locationDetails;
    private final Button mapLinkButton;
    private final List<FishingObservation> observations = new ArrayList<>();
    private final List<FishGroup> groups = new ArrayList<>();
    private final List<LocationEntry> locationEntries = new ArrayList<>();
    private Future<List<FishingObservation>> query;
    private boolean queryDirty = true;
    private long displayedGeneration = -1;
    private int displayedMarkerCount = -1;
    private int displayedSummaryCount = -1;
    private int displayedUnresolvedCount = -1;
    private String displayedMarkerError;
    private Long spotGridId;
    private int spotTileX;
    private int spotTileY;
    private List<Long> spotObservationIds = Collections.emptyList();
    private final Set<String> bookmarkedFish = new LinkedHashSet<>();
    private View view = View.FISH;

    public FishingJournalWindow(FishingJournalService service, FishingMapMarkers mapMarkers) {
        super(UI.scale(800, 560));
        this.service = service;
        this.mapMarkers = mapMarkers;
        loadBookmarks();
        addViewButton(View.FISH, 10, 75);
        addViewButton(View.WATER, 90, 75);
        addViewButton(View.TACKLE, 170, 100);
        addViewButton(View.SPOTS, 275, 90);
        addViewButton(View.TIMES, 370, 75);
        addViewButton(View.BOOKMARKS, 450, 100);
        add(new Button(UI.scale(75), "Refresh") {
            @Override
            public void click() {
                queryDirty = true;
            }
        }, UI.scale(10, 38));
        allCatches = add(new Button(UI.scale(90), "All catches") {
            @Override
            public void click() {
                clearSpot();
            }
        }, UI.scale(95, 38));
        allCatches.hide();
        bookmarkButton = add(new Button(UI.scale(110), "Bookmark fish") {
            @Override
            public void click() {
                toggleBookmark();
            }
        }, UI.scale(195, 38));
        status = add(new Label("Loading fishing knowledge..."), UI.scale(315, 42));

        fishTypesHeading = add(new Label("Fish"), UI.scale(10, 69));
        catchesHeading = add(new Label("Recorded catches — select a fish"), UI.scale(270, 69));
        groupList = add(new FishGroupList(UI.scale(250, 170)), UI.scale(10, 88));
        catchList = add(new FishingCatchList(UI.scale(510, 170)), UI.scale(270, 88));

        detailsHeading = add(new Label("Catch details"), UI.scale(10, 268));
        RichText.Foundry journalText = new RichText.Foundry(RichText.IMAGESRC,
                RichText.ImageSource.res(Resource.remote()));
        details = add(new RichTextBox(UI.scale(770, 260),
                "Select a fish type, then click a dated catch to inspect it.", journalText),
                UI.scale(10, 288));
        analysisInfo = add(new RichTextBox(UI.scale(770, 460), "", journalText), UI.scale(10, 88));
        analysisInfo.hide();
        locationList = add(new LocationEntryList(UI.scale(300, 420)), UI.scale(10, 88));
        locationDetails = add(new RichTextBox(UI.scale(455, 365),
                "Select a recorded location.", journalText), UI.scale(320, 88));
        mapLinkButton = add(new Button(UI.scale(220), "Show location on map  →") {
            @Override
            public void click() {
                showSelectedLocation();
            }
        }, UI.scale(320, 463));
        locationList.hide();
        locationDetails.hide();
        mapLinkButton.hide();
        setView(View.FISH);
    }

    public void refresh() {
        queryDirty = true;
    }

    private void addViewButton(View target, int x, int width) {
        JournalTabButton button = add(new JournalTabButton(UI.scale(width), target), UI.scale(x, 2));
        viewButtons.put(target, button);
    }

    public void showView(View target) {
        setView(target == null ? View.FISH : target);
    }

    public void showSpot(FishingMapMarker marker) {
        if(marker == null)
            return;
        spotGridId = marker.gridId;
        spotTileX = marker.gridTileX;
        spotTileY = marker.gridTileY;
        spotObservationIds = marker.observationIds;
        allCatches.show();
        clearSelection();
        setView(View.FISH);
        queryDirty = true;
        show();
        raise();
    }

    private void clearSpot() {
        spotGridId = null;
        spotObservationIds = Collections.emptyList();
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
            query = spotGridId == null ? service.recent(JOURNAL_LIMIT) : !spotObservationIds.isEmpty() ?
                    service.observations(spotObservationIds) :
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
            updateAnalysisInfo();
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
            if(!FishingAnalytics.isCatch(observation))
                continue;
            String key = fishKey(observation);
            FishGroup group = grouped.computeIfAbsent(key, ignored -> new FishGroup(key, observation));
            group.add(observation);
        }
        groups.clear();
        groups.addAll(grouped.values());
        groups.sort(Comparator.comparingInt((FishGroup group) ->
                        group.bestChance == null ? Integer.MIN_VALUE : group.bestChance)
                .reversed().thenComparing(group -> group.name, String.CASE_INSENSITIVE_ORDER));
    }

    private void updateWaterInfo() {
        Map<WaterKind, Map<String, TimeFish>> grouped = new EnumMap<>(WaterKind.class);
        for(WaterKind kind : WaterKind.values())
            grouped.put(kind, new LinkedHashMap<>());
        for(FishingObservation observation : observations) {
            if(!FishingAnalytics.isCatch(observation))
                continue;
            WaterKind kind = WaterKind.of(observation.waterResource);
            grouped.get(kind).computeIfAbsent(fishKey(observation),
                    ignored -> new TimeFish(observation)).add(observation);
        }
        StringBuilder text = new StringBuilder("$b{Ocean and freshwater catches}\n")
                .append("Water type comes from the server tile resource recorded at the cast.\n");
        for(WaterKind kind : WaterKind.values()) {
            text.append("\n$b{").append(kind.label).append("}\n");
            List<TimeFish> fish = new ArrayList<>(grouped.get(kind).values());
            fish.sort(Comparator.comparingInt((TimeFish entry) -> entry.count).reversed()
                    .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
            if(fish.isEmpty()) {
                text.append("No recorded catches.\n");
                continue;
            }
            for(TimeFish entry : fish)
                text.append("$b{").append(quote(entry.name)).append("} — ")
                        .append(entry.count).append(entry.count == 1 ? " catch" : " catches")
                        .append(" | ").append(entry.qualityRange()).append('\n');
        }
        analysisInfo.settext(text.toString());
    }

    private void updateTackleInfo() {
        FishingAnalytics.Snapshot analytics = FishingAnalytics.analyze(observations);
        StringBuilder text = new StringBuilder("$b{Tackle results}\n")
                .append("Each rig combines pole, line, hook, and bait or lure. Offered chances are server percentages; catches are observed results.\n");
        if(analytics.rigs.isEmpty()) {
            text.append("\nNo tackle surveys or catches recorded yet.");
        } else {
            for(FishingAnalytics.RigSummary rig : analytics.rigs) {
                text.append("\n$b{").append(quote(rig.consumableKind)).append(": ")
                        .append(quote(rig.consumableName)).append("}\n")
                        .append(quote(rig.poleName)).append(" | ").append(quote(rig.lineName))
                        .append(" | ").append(quote(rig.hookName))
                        .append("\n").append(rig.catchCount).append(rig.catchCount == 1 ? " catch" : " catches")
                        .append(" | ").append(rig.surveyCount).append(rig.surveyCount == 1 ? " survey" : " surveys");
                if(rig.bestChance != null)
                    text.append(" | best offered ").append(quote(rig.bestFish)).append(' ')
                            .append(rig.bestChance).append('%');
                text.append('\n');
                for(FishingAnalytics.FishResult fish : rig.fish) {
                    text.append("  ").append(quote(fish.fishName)).append(" — ")
                            .append(fish.catches).append(fish.catches == 1 ? " catch" : " catches");
                    if(fish.bestOfferedChance != null)
                        text.append(" | best offered ").append(fish.bestOfferedChance).append('%');
                    if(fish.bestCaughtChance != null)
                        text.append(" | best landed ").append(fish.bestCaughtChance).append('%');
                    if(fish.averageQuality() != null)
                        text.append(String.format(Locale.ROOT, " | avg Q%.1f", fish.averageQuality()));
                    text.append('\n');
                }
            }
        }
        analysisInfo.settext(text.toString());
    }

    private void updateSpotInfo() {
        FishingAnalytics.Snapshot analytics = FishingAnalytics.analyze(observations);
        locationEntries.clear();
        int rank = 1;
        for(FishingAnalytics.SpotSummary spot : analytics.spots) {
            String chance = spot.bestChance == null ? "?%" : spot.bestChance + "%";
            String title = rank++ + ".  " + spot.bestFish + "  ·  " + chance;
            String subtitle = spot.samples + (spot.samples == 1 ? " chance record" : " chance records") +
                    "  ·  tile " + spot.tileX() + "," + spot.tileY();
            String detail = "$b{" + quote(spot.bestFish) + " — " + chance + "}\n" +
                    "Best server-offered chance recorded in this nearby shoreline cluster.\n\n" +
                    "$b{Best recorded rig}\n" + quote(spot.bestRig) + "\n\n" +
                    "$b{Evidence}\n" + spot.samples +
                    (spot.samples == 1 ? " chance-bearing observation" : " chance-bearing observations") +
                    "\nGrid " + spot.gridId + " · tile " + spot.tileX() + "," + spot.tileY() +
                    "\nLatest evidence " + CLOCK.format(Instant.ofEpochMilli(spot.latestObservedAt));
            locationEntries.add(new LocationEntry(title, subtitle, detail, spot.gridId,
                    spot.tileX(), spot.tileY()));
        }
        finishLocationEntries("No chance-bearing fishing spots have been recorded yet.");
    }

    private void updateBookmarksInfo() {
        locationEntries.clear();
        for(FishGroup group : groups) {
            if(!bookmarkedFish.contains(group.key))
                continue;
            FishingObservation best = group.catches.stream().max(Comparator.comparingInt(observation -> {
                Integer chance = FishingChanceTable.finalPercent(observation);
                return(chance == null ? -1 : chance);
            })).orElse(group.latest);
            Integer bestChance = FishingChanceTable.finalPercent(best);
            int tileX = (int)Math.floor(best.gridOffsetX / tilesz.x);
            int tileY = (int)Math.floor(best.gridOffsetY / tilesz.y);
            String title = "★  " + group.name +
                    (bestChance == null ? "" : "  ·  best " + bestChance + "%");
            String subtitle = group.catches.size() + (group.catches.size() == 1 ? " catch" : " catches") +
                    "  ·  " + WaterKind.of(best.waterResource).label +
                    "  ·  tile " + tileX + "," + tileY;
            String detail = "$b{" + quote(group.name) + "}\n" +
                    group.catches.size() + (group.catches.size() == 1 ? " recorded catch" : " recorded catches") +
                    " · " + group.qualityRange() +
                    (group.bestChance == null ? "" : " · best chance " + group.bestChance + "%") +
                    "\nLatest catch " + CLOCK.format(Instant.ofEpochMilli(group.latest.observedAt)) +
                    "\n\n$b{Best recorded location}\n" + WaterKind.of(best.waterResource).label +
                    " · Grid " + best.gridId + " · tile " + tileX + "," + tileY +
                    "\n\n$b{Rig used there}\n" + quote(best.poleName) + "\n" +
                    quote(best.lineName) + " · " + quote(best.hookName) + "\n" +
                    quote(capitalize(best.consumableKind)) + ": " + quote(best.consumableName);
            locationEntries.add(new LocationEntry(title, subtitle, detail, best.gridId, tileX, tileY));
        }
        finishLocationEntries("No bookmarked fish yet. Select a fish in the Fish tab and choose Bookmark fish.");
    }

    private void finishLocationEntries(String emptyMessage) {
        locationList.reset();
        LocationEntry first = locationEntries.isEmpty() ? null : locationEntries.get(0);
        locationList.change(first);
        if(first == null)
            locationDetails.settext("$b{Nothing here yet}\n" + quote(emptyMessage));
    }

    private void showSelectedLocation() {
        LocationEntry selected = locationList.sel;
        if(selected == null || ui == null || ui.gui == null)
            return;
        if(ui.gui.showFishingLocation(selected.gridId, selected.tileX, selected.tileY))
            ui.gui.msg("Showing the recorded fishing location on the map.", Color.WHITE);
    }

    private void toggleBookmark() {
        FishGroup selected = groupList.sel;
        if(selected == null)
            return;
        if(!bookmarkedFish.add(selected.key))
            bookmarkedFish.remove(selected.key);
        saveBookmarks();
        updateBookmarkButton();
    }

    private void updateBookmarkButton() {
        FishGroup selected = groupList == null ? null : groupList.sel;
        bookmarkButton.change(selected != null && bookmarkedFish.contains(selected.key) ?
                "Remove bookmark" : "Bookmark fish");
    }

    private void loadBookmarks() {
        bookmarkedFish.clear();
        try {
            JSONArray saved = new JSONArray(Utils.getpref(bookmarkPreference(), "[]"));
            for(int index = 0; index < saved.length(); index++) {
                String key = saved.optString(index, "").trim();
                if(!key.isEmpty())
                    bookmarkedFish.add(key);
            }
        } catch(RuntimeException ignored) {
        }
    }

    private void saveBookmarks() {
        JSONArray saved = new JSONArray();
        for(String key : bookmarkedFish)
            saved.put(key);
        Utils.setpref(bookmarkPreference(), saved.toString());
    }

    private String bookmarkPreference() {
        return("fishing-bookmarks-v2/" + service.worldId());
    }

    private void setView(View target) {
        view = target;
        boolean catches = target == View.FISH;
        fishTypesHeading.show(catches);
        catchesHeading.show(catches);
        groupList.show(catches);
        catchList.show(catches);
        detailsHeading.show(catches);
        details.show(catches);
        bookmarkButton.show(catches);
        boolean locations = target == View.SPOTS || target == View.BOOKMARKS;
        analysisInfo.show(!catches && !locations);
        locationList.show(locations);
        locationDetails.show(locations);
        mapLinkButton.show(locations && locationList.sel != null);
        for(JournalTabButton button : viewButtons.values())
            button.updateState();
        updateBookmarkButton();
        updateAnalysisInfo();
    }

    private void updateAnalysisInfo() {
        switch(view) {
        case WATER: updateWaterInfo(); break;
        case TACKLE: updateTackleInfo(); break;
        case SPOTS: updateSpotInfo(); break;
        case TIMES: updateTimeInfo(); break;
        case BOOKMARKS: updateBookmarksInfo(); break;
        default: break;
        }
    }

    private void updateTimeInfo() {
        if(observations.isEmpty()) {
            analysisInfo.settext("$b{Fish by time of day}\nNo recorded catches yet. Catch fish with the native Fishing action or the helper to build this local reference.");
            return;
        }
        Map<TimeBand, Map<String, TimeFish>> grouped = new EnumMap<>(TimeBand.class);
        for(TimeBand band : TimeBand.values())
            grouped.put(band, new LinkedHashMap<>());
        for(FishingObservation observation : observations) {
            if(!FishingAnalytics.isCatch(observation))
                continue;
            TimeBand band = TimeBand.of(observation.gameSecondOfDay);
            String key = fishKey(observation);
            grouped.get(band).computeIfAbsent(key, ignored -> new TimeFish(observation)).add(observation);
        }

        StringBuilder text = new StringBuilder("$b{Fish by time of day}\n")
                .append("Counts are your local catches grouped by the in-game clock. They show observed patterns, not a guaranteed server spawn table.\n");
        for(TimeBand band : TimeBand.values()) {
            text.append("\n$b{").append(band.label).append(" (").append(band.hours).append(")}\n");
            List<TimeFish> fish = new ArrayList<>(grouped.get(band).values());
            if(fish.isEmpty()) {
                text.append("No recorded catches.\n");
                continue;
            }
            fish.sort(Comparator.comparingInt((TimeFish entry) -> entry.count).reversed()
                    .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
            for(TimeFish entry : fish) {
                if(!entry.resource.isBlank())
                    text.append("$img[").append(entry.resource).append(",h=20] ");
                text.append("$b{").append(quote(entry.name)).append("} — ")
                        .append(entry.count).append(entry.count == 1 ? " catch" : " catches")
                        .append(" | ").append(entry.qualityRange())
                        .append(" | latest ").append(formatGameTime(entry.latest.gameSecondOfDay)).append("\n");
            }
        }
        analysisInfo.settext(text.toString());
    }

    private boolean markerStatusChanged() {
        return(displayedMarkerCount != mapMarkers.markerCount() ||
                displayedSummaryCount != mapMarkers.summaryCount() ||
                displayedUnresolvedCount != mapMarkers.unresolvedCount() ||
                !Objects.equals(displayedMarkerError, mapMarkers.lastError()));
    }

    private void updateStatus() {
        displayedMarkerCount = mapMarkers.markerCount();
        displayedSummaryCount = mapMarkers.summaryCount();
        displayedUnresolvedCount = mapMarkers.unresolvedCount();
        displayedMarkerError = mapMarkers.lastError();
        if(!service.available() && service.lastError() != null)
            status.settext(service.lastError());
        else
            status.settext(statusText());
    }

    private String statusText() {
        FishingAnalytics.Snapshot analytics = FishingAnalytics.analyze(observations);
        StringBuilder text = new StringBuilder();
        text.append(analytics.catchCount).append(analytics.catchCount == 1 ? " catch" : " catches")
                .append(" in ").append(groups.size()).append(groups.size() == 1 ? " fish type" : " fish types");
        if(analytics.surveyCount > 0)
            text.append(" | ").append(analytics.surveyCount)
                    .append(analytics.surveyCount == 1 ? " chance survey" : " chance surveys");
        if(spotGridId != null)
            text.append(" at this spot");
        if(mapMarkers.lastError() != null)
            text.append(" | map markers unavailable");
        else {
            text.append(" | ").append(mapMarkers.summaryCount())
                    .append(mapMarkers.summaryCount() == 1 ? " map area" : " map areas")
                    .append(" / ").append(mapMarkers.markerCount())
                    .append(mapMarkers.markerCount() == 1 ? " detailed spot" : " detailed spots");
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
        appendCatchChances(text, observation);
        appendQualityFactors(text, observation);
        details.settext(text.toString());
    }

    private void appendCatchChances(StringBuilder text, FishingObservation observation) {
        List<FishingChoice> choices = FishingChanceTable.parse(observation.choiceRowsJson);
        FishingChoice caught = FishingChanceTable.forFish(observation);
        text.append("\n\n$b{Catch chance for this pole and location}\n");
        if(caught == null) {
            text.append("The server percentage was not captured for this attempt.");
            return;
        }
        text.append(quote(caught.fishName)).append(": $b{").append(caught.finalPercent).append("%}")
                .append(" final chance");
        if(caught.gearPercent != null)
            text.append(" | pole/tackle ").append(caught.gearPercent).append('%');
        if(caught.lurePercent != null)
            text.append(" | bait/lure ").append(caught.lurePercent).append('%');
        text.append("\nAll fish offered here, highest first: ");
        for(int i = 0; i < choices.size(); i++) {
            if(i > 0)
                text.append(" | ");
            FishingChoice choice = choices.get(i);
            text.append(quote(choice.fishName)).append(' ').append(choice.finalPercent).append('%');
        }
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

    private final class JournalTabButton extends Button {
        private final View target;

        JournalTabButton(int width, View target) {
            super(width, target.label);
            this.target = target;
        }

        @Override
        public void click() {
            setView(target);
        }

        void updateState() {
            change(view == target ? target.label.toUpperCase(Locale.ROOT) : target.label);
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
            if(view == target) {
                g.chcolor(242, 193, 78, 235);
                g.frect(Coord.of(UI.scale(4), sz.y - UI.scale(3)),
                        Coord.of(sz.x - UI.scale(8), UI.scale(3)));
                g.chcolor();
            }
        }
    }

    private final class LocationEntryList extends SListBox<LocationEntry, Widget> {
        LocationEntryList(Coord size) {
            super(size, UI.scale(54), UI.scale(3));
        }

        @Override
        protected List<? extends LocationEntry> items() {
            return(locationEntries);
        }

        @Override
        protected Widget makeitem(LocationEntry entry, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<LocationEntry>(this, size, entry);
            Label title = row.add(new Label(entry.title), UI.scale(8, 7));
            title.setcolor(new Color(245, 220, 145));
            Label subtitle = row.add(new Label(entry.subtitle), UI.scale(8, 29));
            subtitle.setcolor(new Color(175, 184, 190));
            return(row);
        }

        @Override
        public void change(LocationEntry entry) {
            super.change(entry);
            locationDetails.settext(entry == null ? "Select a recorded location." : entry.details);
            mapLinkButton.show(visible() && entry != null);
        }
    }

    private static final class LocationEntry {
        final String title;
        final String subtitle;
        final String details;
        final long gridId;
        final int tileX;
        final int tileY;

        LocationEntry(String title, String subtitle, String details,
                      long gridId, int tileX, int tileY) {
            this.title = title;
            this.subtitle = subtitle;
            this.details = details;
            this.gridId = gridId;
            this.tileX = tileX;
            this.tileY = tileY;
        }
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
            updateBookmarkButton();
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
                    (group.bestChance == null ? "" : " | best " + group.bestChance + "%") +
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
            Integer chance = FishingChanceTable.finalPercent(observation);
            return(DATE.format(caught) + "  " + TIME.format(caught) + "  |  " +
                    (chance == null ? "chance ?" : chance + "% chance") + "  |  " +
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
        Integer bestChance;

        FishGroup(String key, FishingObservation latest) {
            this.key = key;
            this.latest = latest;
            this.name = latest.fishName.isBlank() ? "Unknown fish" : latest.fishName;
            this.resource = latest.fishResource;
        }

        void add(FishingObservation observation) {
            catches.add(observation);
            Integer chance = FishingChanceTable.finalPercent(observation);
            if(chance != null && (bestChance == null || chance > bestChance))
                bestChance = chance;
            if(observation.fishQuality != null) {
                qualityCount++;
                minimumQuality = Math.min(minimumQuality, observation.fishQuality);
                maximumQuality = Math.max(maximumQuality, observation.fishQuality);
            }
        }

        String qualityRange() {
            return(qualityCount == 0 ? "quality unknown" :
                    String.format(Locale.ROOT, "Q%.1f–%.1f", minimumQuality, maximumQuality));
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

    private enum TimeBand {
        NIGHT("Night", "21:00–04:59"),
        DAWN("Dawn", "05:00–08:59"),
        DAY("Day", "09:00–16:59"),
        DUSK("Dusk", "17:00–20:59");

        final String label;
        final String hours;

        TimeBand(String label, String hours) {
            this.label = label;
            this.hours = hours;
        }

        static TimeBand of(int secondOfDay) {
            int hour = Math.floorMod(secondOfDay, 24 * 60 * 60) / 3600;
            if(hour < 5 || hour >= 21)
                return(NIGHT);
            if(hour < 9)
                return(DAWN);
            if(hour < 17)
                return(DAY);
            return(DUSK);
        }
    }

    private static final class TimeFish {
        final String name;
        final String resource;
        FishingObservation latest;
        int count;
        int qualityCount;
        double minimumQuality = Double.POSITIVE_INFINITY;
        double maximumQuality = Double.NEGATIVE_INFINITY;

        TimeFish(FishingObservation observation) {
            name = observation.fishName.isBlank() ? "Unknown fish" : observation.fishName;
            resource = observation.fishResource;
        }

        void add(FishingObservation observation) {
            count++;
            if(latest == null || observation.observedAt > latest.observedAt)
                latest = observation;
            if(observation.fishQuality != null) {
                qualityCount++;
                minimumQuality = Math.min(minimumQuality, observation.fishQuality);
                maximumQuality = Math.max(maximumQuality, observation.fishQuality);
            }
        }

        String qualityRange() {
            if(qualityCount == 0)
                return("Q?");
            return(String.format(Locale.ROOT, "Q%.1f–%.1f", minimumQuality, maximumQuality));
        }
    }

    public enum View {
        FISH("Fish"),
        WATER("Water"),
        TACKLE("Tackle Results"),
        SPOTS("Best Spots"),
        TIMES("Times"),
        BOOKMARKS("Bookmarks");

        final String label;

        View(String label) {
            this.label = label;
        }
    }

    private enum WaterKind {
        FRESH("Freshwater"),
        OCEAN("Ocean"),
        UNKNOWN("Unknown water");

        final String label;

        WaterKind(String label) {
            this.label = label;
        }

        static WaterKind of(String resource) {
            String normalized = resource == null ? "" : resource.toLowerCase(Locale.ROOT);
            if(normalized.contains("/owater") || normalized.contains("/odeep") ||
                    normalized.contains("ocean"))
                return(OCEAN);
            if(normalized.contains("water"))
                return(FRESH);
            return(UNKNOWN);
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
