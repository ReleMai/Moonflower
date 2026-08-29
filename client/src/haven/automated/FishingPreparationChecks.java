package haven.automated;

import haven.ItemInfo;

import java.util.List;

/** Deterministic checks for tackle identity and multi-component pole inspection. */
public final class FishingPreparationChecks {
    private FishingPreparationChecks() {
    }

    public static void main(String[] args) {
        check(FishingPoleAssembler.semanticCursorMatch(FishingPoleInspector.Kind.BAIT,
                        "A stack of Earthworms", "gfx/invobjs/earthworm",
                        "Earthworm", "gfx/invobjs/earthworm"),
                "a unit taken from an Earthworm stack must match its wrapper");
        check(FishingPoleAssembler.semanticCursorMatch(FishingPoleInspector.Kind.BAIT,
                        "Stack of Grasshoppers", "gfx/invobjs/grasshopper",
                        "Grasshopper", "gfx/invobjs/grasshopper"),
                "singular and plural stack labels must match");
        check(!FishingPoleAssembler.semanticCursorMatch(FishingPoleInspector.Kind.BAIT,
                        "A stack of Earthworms", "gfx/invobjs/earthworm",
                        "Grasshopper", "gfx/invobjs/grasshopper"),
                "a different bait must not satisfy cursor validation");
        check(!FishingPoleAssembler.semanticCursorMatch(FishingPoleInspector.Kind.HOOK,
                        "Bone Hook", "gfx/invobjs/hook-bone",
                        "Metal Hook", "gfx/invobjs/hook-metal"),
                "a different hook must not satisfy cursor validation");
        check(FishingPoleAssembler.semanticCursorMatch(FishingPoleInspector.Kind.LINE,
                        "", "gfx/invobjs/fine-fishline",
                        "", "gfx/invobjs/fine-fishline"),
                "resource identity must support a temporarily unavailable tooltip name");

        FishingPoleInspector.State state = new FishingPoleInspector.State();
        FishingPoleInspector.addContents(state, new ItemInfo.Contents(null, List.of(
                new ItemInfo.Name(null, "Fine Fishline"),
                new ItemInfo.Name(null, "Bone Hook"),
                new ItemInfo.Name(null, "Earthworm"))));
        check(state.ready(FishingPoleInspector.Kind.BAIT),
                "one contents tooltip must expose every attached tackle component");
        check(state.summary().contains("line=Fine Fishline") &&
                        state.summary().contains("hook=Bone Hook") &&
                        state.summary().contains("bait=Earthworm"),
                "pole diagnostics must name every recognized component");

        FishingPoleInspector.State unknown = new FishingPoleInspector.State();
        FishingPoleInspector.addContents(unknown, new ItemInfo.Contents(null, List.of(
                new ItemInfo.Name(null, "Fine Fishline"),
                new ItemInfo.Name(null, "Bone Hook"),
                new ItemInfo.Name(null, "Mystery Tackle"))));
        check(!unknown.ready(FishingPoleInspector.Kind.BAIT) && unknown.unknown.contains("Mystery Tackle"),
                "unknown pole contents must remain visible and fail verification");

        System.out.println("Fishing preparation checks passed.");
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
