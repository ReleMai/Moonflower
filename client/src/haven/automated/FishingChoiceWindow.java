package haven.automated;

import haven.Button;
import haven.GameUI;
import haven.Label;
import haven.Widget;
import haven.Window;
import haven.automated.helpers.FishingAtlas;
import haven.fishing.FishingChoice;
import haven.fishing.FishingChoiceParser;
import haven.fishing.FishingChanceTable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds, validates, records, and optionally selects current fishing choices. */
final class FishingChoiceWindow {
    private FishingChoiceWindow() {
    }

    static Optional<Selection> selectBest(GameUI gui, Widget helper, Widget journal) {
        return(select(gui, helper, journal, null));
    }

    static Optional<Selection> select(GameUI gui, Widget helper, Widget journal,
                                      String preferredFish) {
        Optional<List<Row>> found = find(gui, helper, journal);
        if(found.isEmpty() || found.get().isEmpty())
            return(Optional.empty());
        List<Row> rows = found.get();
        Row best = null;
        if(preferredFish != null && !preferredFish.isBlank() &&
                !"Highest chance".equalsIgnoreCase(preferredFish)) {
            best = rows.stream().filter(row -> row.choice.fishName.equalsIgnoreCase(preferredFish))
                    .findFirst().orElse(null);
        }
        boolean matchedPreferred = best != null;
        if(best == null) {
            best = rows.stream()
                .max(Comparator.comparingInt((Row row) ->
                                row.choice.finalPercent == null ? -1 : row.choice.finalPercent)
                        .thenComparingInt(row -> row.choice.gearPercent == null ? -1 : row.choice.gearPercent)
                        .thenComparingInt(row -> row.choice.lurePercent == null ? -1 : row.choice.lurePercent)
                        .thenComparing(row -> row.choice.fishName, String.CASE_INSENSITIVE_ORDER.reversed()))
                .orElse(null);
        }
        if(best == null)
            return(Optional.empty());
        Snapshot snapshot = snapshot(rows);
        List<String> fishNames = new ArrayList<>();
        rows.stream().map(row -> row.choice.fishName).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).forEach(fishNames::add);
        best.button.click();
        return(Optional.of(new Selection(best.choice, snapshot.rowsJson, snapshot.summary,
                fishNames, matchedPreferred)));
    }

    static Optional<Snapshot> inspect(GameUI gui, Widget helper, Widget journal) {
        Optional<List<Row>> found = find(gui, helper, journal);
        return(found.isEmpty() || found.get().isEmpty() ? Optional.empty() :
                Optional.of(snapshot(found.get())));
    }

    private static Snapshot snapshot(List<Row> rows) {
        JSONArray serialized = new JSONArray();
        for(Row row : rows) {
            serialized.put(new JSONObject()
                    .put("fish", row.choice.fishName)
                    .put("gear", row.choice.gearPercent)
                    .put("lure", row.choice.lurePercent)
                    .put("final", row.choice.finalPercent));
        }
        List<FishingChoice> ordered = new ArrayList<>();
        for(Row row : rows)
            ordered.add(row.choice);
        ordered.sort(FishingChanceTable.highestFirst());
        return(new Snapshot(serialized.toString(), FishingChanceTable.compact(ordered, 3)));
    }

    static boolean present(GameUI gui, Widget helper, Widget journal) {
        return(find(gui, helper, journal).isPresent());
    }

    private static Optional<List<Row>> find(GameUI gui, Widget helper, Widget journal) {
        for(Widget widget : gui.ui.getAllWidgets()) {
            if(!(widget instanceof Window) || widget == helper || widget == journal)
                continue;
            Window window = (Window)widget;
            List<Row> rows = parse(window);
            boolean knownFish = rows.stream().anyMatch(row ->
                    FishingAtlas.classify(row.choice.fishName) == FishingAtlas.Part.FISH);
            if(knownFish || "This is bait".equals(window.cap) && !rows.isEmpty())
                return(Optional.of(rows));
        }
        return(Optional.empty());
    }

    private static List<Row> parse(Window window) {
        List<Row> rows = new ArrayList<>();
        Button button = null;
        List<String> labels = new ArrayList<>();
        for(Widget child : window.children()) {
            if(child instanceof Button) {
                add(rows, button, labels);
                button = (Button)child;
                labels = new ArrayList<>();
            } else if(button != null && child instanceof Label) {
                labels.add(((Label)child).texts);
            }
        }
        add(rows, button, labels);
        return(rows);
    }

    private static void add(List<Row> rows, Button button, List<String> labels) {
        if(button == null)
            return;
        FishingChoice choice = FishingChoiceParser.parse(labels);
        if(choice != null)
            rows.add(new Row(button, choice));
    }

    static class Snapshot {
        final String rowsJson;
        final String summary;

        Snapshot(String rowsJson, String summary) {
            this.rowsJson = rowsJson;
            this.summary = summary;
        }
    }

    static final class Selection extends Snapshot {
        final FishingChoice choice;
        final List<String> fishNames;
        final boolean matchedPreferred;

        Selection(FishingChoice choice, String rowsJson, String summary, List<String> fishNames,
                  boolean matchedPreferred) {
            super(rowsJson, summary);
            this.choice = choice;
            this.fishNames = List.copyOf(fishNames);
            this.matchedPreferred = matchedPreferred;
        }
    }

    private static final class Row {
        final Button button;
        final FishingChoice choice;

        Row(Button button, FishingChoice choice) {
            this.button = button;
            this.choice = choice;
        }
    }
}
