package haven.automated;

import haven.Button;
import haven.GameUI;
import haven.Label;
import haven.Widget;
import haven.Window;
import haven.automated.helpers.FishingAtlas;
import haven.fishing.FishingChoice;
import haven.fishing.FishingChoiceParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds, validates, records, and selects current lure-fishing choices. */
final class FishingChoiceWindow {
    private FishingChoiceWindow() {
    }

    static Optional<Selection> selectBest(GameUI gui, Widget helper, Widget journal) {
        Optional<List<Row>> found = find(gui, helper, journal);
        if(found.isEmpty() || found.get().isEmpty())
            return(Optional.empty());
        List<Row> rows = found.get();
        JSONArray serialized = new JSONArray();
        for(Row row : rows) {
            serialized.put(new JSONObject()
                    .put("fish", row.choice.fishName)
                    .put("gear", row.choice.gearPercent)
                    .put("lure", row.choice.lurePercent)
                    .put("final", row.choice.finalPercent));
        }
        Row best = rows.stream()
                .max(Comparator.comparingInt(row -> row.choice.finalPercent == null ? -1 : row.choice.finalPercent))
                .orElse(null);
        if(best == null)
            return(Optional.empty());
        best.button.click();
        return(Optional.of(new Selection(best.choice, serialized.toString())));
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

    static final class Selection {
        final FishingChoice choice;
        final String rowsJson;

        Selection(FishingChoice choice, String rowsJson) {
            this.choice = choice;
            this.rowsJson = rowsJson;
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
