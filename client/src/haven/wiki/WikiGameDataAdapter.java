package haven.wiki;

import haven.GameUI;
import haven.Loading;
import haven.MenuGrid;
import haven.Resource;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the current character's server-provided action menu into Codex
 * records. Invoking a record delegates to MenuGrid so native crafting and
 * server-side requirement checks remain authoritative.
 */
public final class WikiGameDataAdapter {
    private final GameUI gui;
    private final Map<String, MenuGrid.Pagina> pages = new LinkedHashMap<>();
    private final Map<String, WikiReference> references = new LinkedHashMap<>();
    private final Map<String, List<WikiReference>> children = new HashMap<>();
    private int indexedSequence = -1;

    public WikiGameDataAdapter(GameUI gui) {
        this.gui = gui;
    }

    public synchronized boolean refresh() {
        MenuGrid menu = gui.menu;
        if(menu == null)
            return(false);
        if(indexedSequence == menu.pagseq && !references.isEmpty())
            return(false);
        Map<String, MenuGrid.Pagina> nextPages = new LinkedHashMap<>();
        Map<String, WikiReference> nextReferences = new LinkedHashMap<>();
        List<MenuGrid.Pagina> snapshot;
        synchronized(menu.paginae) {
            snapshot = new ArrayList<>(menu.paginae);
        }
        for(MenuGrid.Pagina page : snapshot) {
            try {
                Resource resource = page.res();
                Resource.AButton action = resource.layer(Resource.action);
                if(action == null || action.name == null || action.name.isBlank())
                    continue;
                WikiReference reference = WikiReference.action(resource.name, action.name,
                        category(resource.name, action.ad));
                nextPages.put(resource.name, page);
                nextReferences.put(resource.name, reference);
            } catch(Loading ignored) {
                // A later menu update or refresh indexes the resource once loaded.
            } catch(RuntimeException malformedResource) {
                // One malformed optional resource must not disable the archive.
            }
        }
        Map<String, List<WikiReference>> nextChildren = new HashMap<>();
        for(Map.Entry<String, MenuGrid.Pagina> entry : nextPages.entrySet()) {
            try {
                MenuGrid.Pagina parent = entry.getValue().parent();
                if(parent == null)
                    continue;
                WikiReference parentReference = nextReferences.get(parent.res().name);
                WikiReference childReference = nextReferences.get(entry.getKey());
                if(parentReference != null && childReference != null)
                    nextChildren.computeIfAbsent(parentReference.resourceName,
                            ignored -> new ArrayList<>()).add(childReference);
            } catch(Loading ignored) {
            } catch(RuntimeException malformedParent) {
            }
        }
        for(List<WikiReference> value : nextChildren.values())
            value.sort(Comparator.comparing(reference -> reference.title, String.CASE_INSENSITIVE_ORDER));
        pages.clear();
        pages.putAll(nextPages);
        references.clear();
        references.putAll(nextReferences);
        children.clear();
        children.putAll(nextChildren);
        indexedSequence = menu.pagseq;
        return(true);
    }

    public synchronized Collection<WikiSearchIndex.Record> records() {
        List<WikiSearchIndex.Record> records = new ArrayList<>();
        for(WikiReference reference : references.values()) {
            String summary = description(reference);
            records.add(new WikiSearchIndex.Record(reference, summary,
                    List.of(reference.resourceName, actionKind(reference))));
        }
        return(records);
    }

    public synchronized WikiArticle article(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null)
            return(null);
        String description = description(reference);
        List<WikiReference> related = new ArrayList<>();
        related.add(WikiReference.guide(reference.title, "Community Archive"));
        try {
            MenuGrid.Pagina parent = page.parent();
            if(parent != null) {
                WikiReference parentReference = references.get(parent.res().name);
                if(parentReference != null)
                    related.add(parentReference);
            }
        } catch(Loading ignored) {
        } catch(RuntimeException malformedParent) {
        }
        related.addAll(children.getOrDefault(reference.resourceName, Collections.emptyList()));
        StringBuilder text = new StringBuilder();
        text.append("## Overview\n");
        text.append(description.isBlank() ?
                "This record is available from the current character's action menu." : description);
        text.append("\n\n## Source and behavior\n");
        text.append("LIVE — This action was registered by the current Haven session. ");
        if(canInvoke(reference)) {
            if(isCraftAction(reference))
                text.append("Open Crafting uses Haven's existing action and server-created crafting window. " +
                        "The server remains responsible for ingredients, skills, tools, and availability.");
            else
                text.append("Open Action uses the existing action-menu path and preserves its normal cursor, " +
                        "placement, selection, and server behavior.");
        } else {
            text.append("This is an action category. Open its related records instead of invoking it directly.");
        }
        return(new WikiArticle(reference, reference.title, text.toString(), -1L,
                null, null, false, List.of(reference.category), related));
    }

    public synchronized BufferedImage image(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null)
            return(null);
        try {
            return(page.button().img());
        } catch(Loading ignored) {
            return(null);
        } catch(RuntimeException malformedSprite) {
            return(null);
        }
    }

    public synchronized boolean canInvoke(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null || !children.getOrDefault(reference.resourceName,
                Collections.emptyList()).isEmpty())
            return(false);
        try {
            Resource.AButton action = page.button().act();
            return(action.ad != null && action.ad.length > 0);
        } catch(Loading ignored) {
            return(false);
        } catch(RuntimeException malformedAction) {
            return(false);
        }
    }

    public synchronized boolean isCraftAction(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null)
            return(false);
        try {
            String[] arguments = page.button().act().ad;
            return(arguments.length > 0 && ("craft".equals(arguments[0]) || "bp".equals(arguments[0])));
        } catch(Loading ignored) {
            return(false);
        } catch(RuntimeException malformedAction) {
            return(false);
        }
    }

    public synchronized boolean invoke(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null || !canInvoke(reference) || gui.menu == null)
            return(false);
        gui.menu.use(page.button(), new MenuGrid.Interaction(), true);
        return(true);
    }

    public synchronized WikiReference findByResource(String resourceName, String fallbackTitle) {
        refresh();
        WikiReference exact = references.get(resourceName);
        if(exact != null)
            return(exact);
        return(WikiReference.guide(fallbackTitle, "Items"));
    }

    private MenuGrid.Pagina page(WikiReference reference) {
        return(reference == null || reference.provenance != WikiReference.Provenance.LIVE ?
                null : pages.get(reference.resourceName));
    }

    private String description(WikiReference reference) {
        MenuGrid.Pagina page = page(reference);
        if(page == null)
            return("");
        try {
            Resource.Pagina description = page.res().layer(Resource.pagina);
            return(description == null ? "" : RingOfBrodgarWikiService.cleanWikiMarkup(description.text));
        } catch(Loading ignored) {
            return("");
        } catch(RuntimeException malformedDescription) {
            return("");
        }
    }

    private String actionKind(WikiReference reference) {
        return(isCraftAction(reference) ? "craft recipe" : "action menu");
    }

    static String category(String resourceName, String[] action) {
        String name = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        String verb = action == null || action.length == 0 ? "" : action[0].toLowerCase(Locale.ROOT);
        if("craft".equals(verb) || name.contains("/craft/"))
            return("Crafting");
        if("bp".equals(verb) || name.contains("/bld/") || name.contains("/build/"))
            return("Buildings");
        if(name.contains("/skill") || name.contains("/abil"))
            return("Skills and Abilities");
        if(name.contains("/combat") || name.contains("/atk") || name.contains("/maneuver"))
            return("Combat");
        if(name.contains("/gather") || name.contains("/harvest") || name.contains("/fish"))
            return("Gathering");
        if(name.contains("/food") || name.contains("/cook") || name.contains("/meal"))
            return("Food");
        if(name.contains("/clothes") || name.contains("/armor") || name.contains("/weapon") ||
                name.contains("/equip"))
            return("Equipment");
        if(name.contains("/res") || name.contains("/material") || name.contains("/ore"))
            return("Resources");
        if(name.contains("/animal") || name.contains("/creature") || name.contains("/mobs"))
            return("Creatures");
        if(name.contains("/item") || name.contains("/obj"))
            return("Items");
        return("Actions");
    }
}
