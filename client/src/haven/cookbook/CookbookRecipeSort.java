package haven.cookbook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** User-selectable ordering for grouped cookbook foods. */
enum CookbookRecipeSort {
    SELECTED_STAT("Selected stat"),
    TOTAL_FEPS("Total stats"),
    FEP_PER_HUNGER("FEP / hunger"),
    LATEST_QUALITY("Latest quality"),
    NAME("Food name");

    static final List<CookbookRecipeSort> ALL =
            Collections.unmodifiableList(Arrays.asList(values()));

    final String label;

    CookbookRecipeSort(String label) {
        this.label = label;
    }
}
