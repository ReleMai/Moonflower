package haven.wiki;

import haven.Area;
import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.Label;
import haven.MoonFlowerHudTheme;
import haven.RichText;
import haven.RichTextBox;
import haven.SListBox;
import haven.SListWidget;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Native MoonFlower field archive backed by safe community and live action records. */
public final class WikiWindow extends Window {
    private static final Coord DEFAULT_SIZE = UI.scale(1080, 700);
    private static final Coord LEGACY_DEFAULT_SIZE = UI.scale(1000, 650);
    private static final Coord MINIMUM_SIZE = UI.scale(820, 540);
    private static final int LOCAL_RESULT_LIMIT = 120;
    private static final Text.Foundry ROW_TITLE =
            new Text.Foundry(Text.sans, 13, MoonFlowerHudTheme.IVORY).aa(true);
    private static final Text.Foundry ROW_META =
            new Text.Foundry(Text.sans, 10, new Color(151, 181, 181)).aa(true);

    private enum ShelfMode {CATEGORIES, RECENT, BOOKMARKS, SEARCHES, RECORDS}

    private static final class ShelfEntry {
        final String label, meta, category, search, communitySearch;
        final WikiReference reference;

        private ShelfEntry(String label, String meta, WikiReference reference,
                           String category, String search, String communitySearch) {
            this.label = label;
            this.meta = meta;
            this.reference = reference;
            this.category = category;
            this.search = search;
            this.communitySearch = communitySearch;
        }

        static ShelfEntry reference(WikiSearchIndex.Record record) {
            return(reference(record.reference));
        }

        static ShelfEntry reference(WikiReference reference) {
            return(new ShelfEntry(reference.title,
                    reference.provenance.name() + " • " + reference.category,
                    reference, null, null, null));
        }

        static ShelfEntry category(String category, int count) {
            return(new ShelfEntry(category, count + (count == 1 ? " known record" : " known records"),
                    null, category, null, null));
        }

        static ShelfEntry search(String query) {
            return(new ShelfEntry(query, "Previous archive search", null, null, query, null));
        }

        static ShelfEntry communitySearch(String query) {
            return(new ShelfEntry("Search community for “" + query + "”",
                    "Select this row or press Enter", null, null, null, query));
        }

        static ShelfEntry empty(String label, String guidance) {
            return(new ShelfEntry(label, guidance, null, null, null, null));
        }
    }

    private final GameUI gui;
    private final RingOfBrodgarWikiService service;
    private final WikiLibrary library;
    private final WikiSearchIndex index = new WikiSearchIndex();
    private final WikiNavigationState navigation = new WikiNavigationState();
    private final WikiGameDataAdapter gameData;
    private final TextEntry searchEntry;
    private final Button archiveSearchButton, backButton, forwardButton, homeButton, bookmarkButton;
    private final Button categoriesButton, recentButton, bookmarksButton, searchesButton;
    private final Label status, shelfHeading, breadcrumb, relatedHeading, attribution;
    private final ShelfList shelfList;
    private final WikiGalleryView galleryView;
    private final WikiOrnamentWidget crestOrnament, railOrnament;
    private final RichTextBox articleView;
    private final RelatedList relatedList;
    private final Button actionButton, openSourceButton, licenseButton;
    private final List<ShelfEntry> shelfEntries = new ArrayList<>();
    private final List<WikiReference> related = new ArrayList<>();
    private Future<WikiSearchResponse> pendingSearch;
    private Future<WikiArticle> pendingArticle;
    private WikiReference requestedReference;
    private WikiArticle article;
    private ShelfMode shelfMode = ShelfMode.CATEGORIES;
    private String baseStatus = "The archive is ready. Local records update as you type.";
    private long displayedCooldown = -1;

    public WikiWindow(GameUI gui, RingOfBrodgarWikiService service, Coord initialSize) {
        super(constrain(initialSize), "MoonFlower Codex");
        this.gui = gui;
        this.service = service;
        this.library = new WikiLibrary(gui.chrid);
        this.gameData = new WikiGameDataAdapter(gui);

        backButton = add(new Button(UI.scale(48), "<", this::goBack), UI.scale(10, 8));
        backButton.tooltip = "Back (Alt+Left or mouse Back)";
        forwardButton = add(new Button(UI.scale(48), ">", this::goForward), UI.scale(63, 8));
        forwardButton.tooltip = "Forward (Alt+Right or mouse Forward)";
        homeButton = add(new Button(UI.scale(58), "Home", this::showHome), UI.scale(116, 8));
        searchEntry = add(new TextEntry(UI.scale(600), "") {
            @Override
            protected void changed() {
                super.changed();
                localSearch(text());
            }

            @Override
            public void activate(String text) {
                searchArchive();
            }
        }, UI.scale(184, 8));
        archiveSearchButton = add(new Button(UI.scale(116), "Search Wiki", this::searchArchive),
                UI.scale(794, 8));
        archiveSearchButton.tooltip = "Search Ring of Brodgar. Pressing Enter in the search field does the same.";
        bookmarkButton = add(new Button(UI.scale(92), "☆ Save", this::toggleBookmark), UI.scale(920, 8));
        status = add(new Label(baseStatus), UI.scale(10, 40));
        status.setcolor(MoonFlowerHudTheme.IVORY);
        breadcrumb = add(new Label("Archive"), UI.scale(330, 66));
        breadcrumb.setcolor(MoonFlowerHudTheme.GOLD);
        crestOrnament = add(new WikiOrnamentWidget(WikiOrnamentWidget.Kind.CREST), UI.scale(450, 82));

        categoriesButton = add(new Button(UI.scale(72), "Index", () -> showShelf(ShelfMode.CATEGORIES)),
                UI.scale(10, 66));
        recentButton = add(new Button(UI.scale(72), "Recent", () -> showShelf(ShelfMode.RECENT)),
                UI.scale(87, 66));
        bookmarksButton = add(new Button(UI.scale(72), "Saved", () -> showShelf(ShelfMode.BOOKMARKS)),
                UI.scale(164, 66));
        searchesButton = add(new Button(UI.scale(72), "Queries", () -> showShelf(ShelfMode.SEARCHES)),
                UI.scale(241, 66));
        shelfHeading = add(new Label("Archive index"), UI.scale(10, 99));
        shelfHeading.setcolor(MoonFlowerHudTheme.GOLD);
        shelfList = add(new ShelfList(UI.scale(310, 530)), UI.scale(10, 119));

        galleryView = add(new WikiGalleryView(service, UI.scale(525, 104)), UI.scale(330, 88));
        galleryView.hide();
        articleView = add(new RichTextBox(UI.scale(525, 540), homeText()), UI.scale(330, 88));
        articleView.bg = MoonFlowerHudTheme.INK_DEEP;
        relatedHeading = add(new Label("Linked records"), UI.scale(865, 88));
        relatedHeading.setcolor(MoonFlowerHudTheme.GOLD);
        relatedList = add(new RelatedList(UI.scale(205, 470)), UI.scale(865, 109));
        railOrnament = add(new WikiOrnamentWidget(WikiOrnamentWidget.Kind.ARCHIVE_RAIL), UI.scale(310, 240));

        actionButton = add(new Button(UI.scale(125), "Open Action", this::invokeAction), UI.scale(330, 656));
        openSourceButton = add(new Button(UI.scale(110), "Open Source", this::openSource), UI.scale(465, 656));
        licenseButton = add(new Button(UI.scale(75), "License",
                () -> openExternal(RingOfBrodgarWikiService.COPYRIGHT_URI)), UI.scale(585, 656));
        attribution = add(new Label("GUIDE community archive • LIVE current session action • GFDL text"),
                UI.scale(670, 661));
        attribution.setcolor(new Color(151, 181, 181));
        actionButton.disable(true);
        openSourceButton.disable(true);
        bookmarkButton.disable(true);
        reqclose(this::hide);
        resize(constrain(initialSize));
        refreshGameData();
        showHome();
    }

    public static Coord defaultSize() {return(DEFAULT_SIZE);}

    @Override
    protected Deco makedeco() {return(new DefaultDeco(true).dragsize(true));}

    public void focusSearch() {
        refreshGameData();
        crestOrnament.awaken();
        railOrnament.awaken();
        setfocus(searchEntry);
    }

    public void open(WikiReference reference) {
        if(reference == null)
            return;
        show();
        raise();
        refreshGameData();
        loadReference(navigation.open(reference));
    }

    public void open(String title, String resourceName) {
        open(gameData.findByResource(resourceName, title));
    }

    private void refreshGameData() {
        if(gameData.refresh())
            index.putAll(gameData.records());
    }

    private void searchArchive() {
        if(pendingSearch != null) {
            setBaseStatus("An archive search is already running.");
            return;
        }
        String query = RingOfBrodgarWikiService.normalizeQuery(searchEntry.text());
        if(query.length() < 2) {
            setBaseStatus("Enter at least two characters.");
            return;
        }
        searchEntry.settext(query);
        library.searched(query);
        pendingSearch = service.search(query);
        archiveSearchButton.disable(true);
        setBaseStatus("Searching the community archive for “" + query + "”...");
    }

    private void localSearch(String query) {
        String normalized = RingOfBrodgarWikiService.normalizeQuery(query);
        if(normalized.length() < 2) {
            if(shelfMode == ShelfMode.RECORDS)
                showShelf(ShelfMode.CATEGORIES);
            return;
        }
        List<WikiSearchIndex.Record> local = index.search(normalized, LOCAL_RESULT_LIMIT);
        showRecords(local, "Known records matching “" + normalized + "”");
        if(local.isEmpty()) {
            shelfEntries.add(ShelfEntry.communitySearch(normalized));
            shelfList.reset();
            setBaseStatus("No session-known record matched. Press Enter or select the community search below.");
        } else {
            setBaseStatus(local.size() + " known record" + (local.size() == 1 ? "" : "s") +
                    " • Press Enter or Search Wiki for community results.");
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        refreshGameData();
        if(pendingSearch != null && pendingSearch.isDone())
            finishSearch();
        if(pendingArticle != null && pendingArticle.isDone())
            finishArticle();
        updateCooldownStatus();
    }

    private void finishSearch() {
        try {
            WikiSearchResponse response = pendingSearch.get();
            Map<String, WikiSearchIndex.Record> uniqueRecords = new LinkedHashMap<>();
            for(WikiSearchResult result : response.results) {
                WikiReference reference = WikiReference.guide(result.title, "Community Search", result.articleUri);
                WikiSearchIndex.Record record = new WikiSearchIndex.Record(reference, result.snippet,
                        List.of(Integer.toString(result.pageId)));
                index.put(record);
                uniqueRecords.putIfAbsent(relatedKey(reference), record);
            }
            List<WikiSearchIndex.Record> records = new ArrayList<>(uniqueRecords.values());
            showRecords(records, "Community results for “" + response.query + "”");
            if(records.isEmpty()) {
                shelfEntries.add(ShelfEntry.empty("No community results",
                        "Try a broader name or check the spelling"));
                shelfList.reset();
            }
            setBaseStatus(records.isEmpty() ? "No community records matched “" + response.query + "”." :
                    records.size() + " community result" + (records.size() == 1 ? "" : "s") +
                            (response.cached ? " • cached" : " • downloaded"));
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            setBaseStatus("Archive search was interrupted.");
        } catch(ExecutionException failure) {
            Throwable cause = failure.getCause();
            if(cause instanceof RingOfBrodgarWikiService.RateLimitException) {
                long seconds = ((RingOfBrodgarWikiService.RateLimitException)cause).remainingSeconds;
                setBaseStatus("Please wait " + seconds +
                        "s before a different community search. Local records remain instant.");
            } else {
                setBaseStatus(message(cause, "Could not search the community archive."));
            }
        } finally {
            pendingSearch = null;
            archiveSearchButton.disable(false);
        }
    }

    private void loadReference(WikiReference reference) {
        if(reference == null)
            return;
        if(pendingArticle != null) {
            pendingArticle.cancel(true);
            pendingArticle = null;
        }
        article = null;
        requestedReference = reference;
        library.viewed(reference);
        updateControls(reference);
        breadcrumb.settext("Archive > " + reference.category + " > " + reference.title);
        related.clear();
        relatedList.reset();
        setRelatedVisible(false);
        galleryView.setImages(null);
        galleryView.hide();
        if(reference.provenance == WikiReference.Provenance.LIVE) {
            WikiArticle liveArticle = gameData.article(reference);
            if(liveArticle == null) {
                showMissing(reference, "This live action is no longer registered in the current session.");
                return;
            }
            displayArticle(liveArticle);
            return;
        }
        articleView.settext(loadingText(reference));
        pendingArticle = service.article(reference.title);
        setBaseStatus("Opening “" + reference.title + "” from the community archive...");
    }

    private void finishArticle() {
        WikiReference requested = requestedReference;
        try {
            WikiArticle loaded = pendingArticle.get();
            if(requested != null && navigation.current() != null && navigation.current().equals(requested))
                displayArticle(loaded);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            setBaseStatus("Record loading was interrupted.");
        } catch(ExecutionException failure) {
            showMissing(requested, message(failure.getCause(), "Could not load this community record."));
        } finally {
            pendingArticle = null;
            requestedReference = null;
        }
    }

    private void displayArticle(WikiArticle loaded) {
        article = loaded;
        crestOrnament.pageTurn();
        railOrnament.pageTurn();
        WikiReference current = navigation.current();
        if(current != null) {
            index.put(new WikiSearchIndex.Record(current, firstParagraph(loaded.text), loaded.categories));
            updateControls(current);
        }
        articleView.settext(articleText(loaded));
        related.clear();
        Map<String, WikiReference> uniqueLinks = new LinkedHashMap<>();
        for(WikiReference link : loaded.links) {
            if(current == null || !current.equals(link))
                uniqueLinks.putIfAbsent(relatedKey(link), link);
        }
        related.addAll(uniqueLinks.values());
        relatedList.reset();
        relatedHeading.settext(related.isEmpty() ? "No linked records" :
                "Linked records (" + related.size() + ")");
        setRelatedVisible(!related.isEmpty());
        if(current != null && current.provenance == WikiReference.Provenance.LIVE) {
            java.awt.image.BufferedImage icon = gameData.image(current);
            galleryView.setImage(icon);
            if(icon != null)
                galleryView.show();
        } else {
            galleryView.setImages(loaded.imageUris);
            if(!loaded.imageUris.isEmpty())
                galleryView.show();
        }
        layoutArticle();
        setBaseStatus("Opened “" + loaded.title + "” • " +
                (current == null ? "GUIDE" : current.provenance.name()) +
                (loaded.cached ? " • cached" : ""));
    }

    private void showMissing(WikiReference reference, String reason) {
        article = null;
        galleryView.setImages(null);
        galleryView.hide();
        related.clear();
        relatedList.reset();
        setRelatedVisible(false);
        articleView.settext("$font[serif,19]{$col[239,225,185]{Record unavailable}}\n\n" +
                quote(reason) + "\n\n$col[207,164,72]{The archive remains open. Use Back, search, or the index.}");
        setBaseStatus(reason);
        updateControls(reference);
        layoutArticle();
    }

    private void showHome() {
        if(pendingArticle != null) {
            pendingArticle.cancel(true);
            pendingArticle = null;
        }
        navigation.clear();
        crestOrnament.pageTurn();
        railOrnament.pageTurn();
        article = null;
        requestedReference = null;
        breadcrumb.settext("Archive > Field Desk");
        articleView.settext(homeText());
        galleryView.setImages(null);
        galleryView.hide();
        related.clear();
        relatedList.reset();
        relatedHeading.settext("Linked records");
        setRelatedVisible(false);
        updateControls(null);
        showShelf(ShelfMode.CATEGORIES);
        setBaseStatus("The archive is ready. Local records update as you type.");
        layoutArticle();
    }

    private void goBack() {
        if(navigation.canBack())
            loadReference(navigation.back());
    }

    private void goForward() {
        if(navigation.canForward())
            loadReference(navigation.forward());
    }

    private void toggleBookmark() {
        WikiReference current = navigation.current();
        if(current == null)
            return;
        boolean saved = library.toggleBookmark(current);
        bookmarkButton.change(saved ? "★ Saved" : "☆ Save");
        if(shelfMode == ShelfMode.BOOKMARKS)
            showShelf(ShelfMode.BOOKMARKS);
        setBaseStatus(saved ? "Saved “" + current.title + "” to your field library." :
                "Removed “" + current.title + "” from your field library.");
    }

    private void invokeAction() {
        WikiReference current = navigation.current();
        if(!gameData.invoke(current)) {
            setBaseStatus("This action is no longer available in the current session.");
            return;
        }
        setBaseStatus((gameData.isCraftAction(current) ? "Opened native crafting for “" :
                "Opened native action “") + current.title + "”.");
    }

    private void openSource() {
        WikiReference current = navigation.current();
        URI source = article != null && article.articleUri != null ? article.articleUri :
                current == null ? null : current.articleUri;
        openExternal(source);
    }

    private void openExternal(URI uri) {
        if(uri == null || !"https".equalsIgnoreCase(uri.getScheme()) ||
                !"ringofbrodgar.com".equalsIgnoreCase(uri.getHost())) {
            gui.error("Refused an unsafe community source link.");
            return;
        }
        try {
            ui.wnd.toolkit().browse(uri);
        } catch(Exception failure) {
            gui.error("Could not open the community source: " + failure.getMessage());
        }
    }

    private void updateControls(WikiReference reference) {
        backButton.disable(!navigation.canBack());
        forwardButton.disable(!navigation.canForward());
        bookmarkButton.disable(reference == null);
        bookmarkButton.change(reference != null && library.bookmarked(reference) ? "★ Saved" : "☆ Save");
        boolean liveAction = reference != null && gameData.canInvoke(reference);
        actionButton.disable(!liveAction);
        actionButton.change(liveAction && gameData.isCraftAction(reference) ? "Open Crafting" : "Open Action");
        openSourceButton.disable(reference == null || reference.provenance != WikiReference.Provenance.GUIDE);
    }

    private void showShelf(ShelfMode mode) {
        shelfMode = mode;
        shelfEntries.clear();
        if(mode == ShelfMode.CATEGORIES) {
            shelfHeading.settext("Archive index");
            for(String category : index.categories())
                shelfEntries.add(ShelfEntry.category(category, index.category(category, 0).size()));
        } else if(mode == ShelfMode.RECENT) {
            shelfHeading.settext("Recently viewed");
            for(WikiReference reference : library.recent())
                shelfEntries.add(ShelfEntry.reference(reference));
        } else if(mode == ShelfMode.BOOKMARKS) {
            shelfHeading.settext("Saved records");
            for(WikiReference reference : library.bookmarks())
                shelfEntries.add(ShelfEntry.reference(reference));
        } else if(mode == ShelfMode.SEARCHES) {
            shelfHeading.settext("Search history");
            for(String query : library.searches())
                shelfEntries.add(ShelfEntry.search(query));
        }
        if(shelfEntries.isEmpty()) {
            if(mode == ShelfMode.CATEGORIES)
                shelfEntries.add(ShelfEntry.empty("No session records yet",
                        "Search Wiki to browse community records"));
            else if(mode == ShelfMode.RECENT)
                shelfEntries.add(ShelfEntry.empty("No recent records", "Opened records appear here"));
            else if(mode == ShelfMode.BOOKMARKS)
                shelfEntries.add(ShelfEntry.empty("No saved records", "Save an open record to keep it here"));
            else if(mode == ShelfMode.SEARCHES)
                shelfEntries.add(ShelfEntry.empty("No previous queries", "Community searches appear here"));
        }
        shelfList.reset();
    }

    private void showRecords(List<WikiSearchIndex.Record> records, String heading) {
        shelfMode = ShelfMode.RECORDS;
        shelfEntries.clear();
        for(WikiSearchIndex.Record record : records)
            shelfEntries.add(ShelfEntry.reference(record));
        shelfHeading.settext(heading);
        shelfList.reset();
    }

    private void openShelfEntry(ShelfEntry entry) {
        if(entry == null)
            return;
        if(entry.reference != null) {
            open(entry.reference);
        } else if(entry.category != null) {
            showRecords(index.category(entry.category, LOCAL_RESULT_LIMIT), entry.category);
        } else if(entry.search != null) {
            searchEntry.settext(entry.search);
            localSearch(entry.search);
        } else if(entry.communitySearch != null) {
            searchEntry.settext(entry.communitySearch);
            searchArchive();
        }
    }

    private void setRelatedVisible(boolean visible) {
        if(visible) {
            relatedHeading.show();
            relatedList.show();
        } else {
            relatedHeading.hide();
            relatedList.hide();
        }
    }

    private void updateCooldownStatus() {
        long cooldown = service.secondsUntilNextRequest();
        if(cooldown == displayedCooldown)
            return;
        displayedCooldown = cooldown;
        status.settext(baseStatus + (cooldown > 0 ? " • new community query in " + cooldown + "s" : ""));
    }

    private void setBaseStatus(String text) {
        baseStatus = text;
        displayedCooldown = -1;
        updateCooldownStatus();
    }

    @Override
    public boolean keydown(KeyDownEvent event) {
        if((event.mods & haven.KeyMatch.M) != 0 && event.code == KeyEvent.VK_LEFT) {
            goBack();
            return(true);
        }
        if((event.mods & haven.KeyMatch.M) != 0 && event.code == KeyEvent.VK_RIGHT) {
            goForward();
            return(true);
        }
        return(super.keydown(event));
    }

    @Override
    public boolean mousedown(MouseDownEvent event) {
        if(event.b == 4) {goBack(); return(true);}
        if(event.b == 5) {goForward(); return(true);}
        return(super.mousedown(event));
    }

    @Override
    public void resize(Coord requested) {
        Coord next = constrain(requested);
        super.resize(next);
        if(searchEntry != null)
            layoutChildren();
        if(parent != null)
            Utils.setprefc("wndsz-wiki", next);
    }

    private void layoutChildren() {
        int margin = UI.scale(10), gap = UI.scale(8), topY = UI.scale(8);
        backButton.move(Coord.of(margin, topY));
        forwardButton.move(Coord.of(margin + UI.scale(53), topY));
        homeButton.move(Coord.of(margin + UI.scale(106), topY));
        int searchX = margin + UI.scale(174), searchButtonWidth = UI.scale(116), bookmarkWidth = UI.scale(92);
        int searchWidth = Math.max(UI.scale(250), sz.x - searchX - searchButtonWidth -
                bookmarkWidth - (gap * 2) - margin);
        searchEntry.move(Coord.of(searchX, topY));
        searchEntry.resize(searchWidth);
        archiveSearchButton.move(Coord.of(searchX + searchWidth + gap, topY));
        bookmarkButton.move(Coord.of(sz.x - margin - bookmarkWidth, topY));
        status.move(Coord.of(margin, UI.scale(40)));

        int sidebarWidth = Math.max(UI.scale(270), Math.min(UI.scale(330), sz.x * 30 / 100));
        int modeY = UI.scale(66), modeWidth = (sidebarWidth - (gap * 3)) / 4;
        Button[] modes = {categoriesButton, recentButton, bookmarksButton, searchesButton};
        for(int i = 0; i < modes.length; i++) {
            modes[i].move(Coord.of(margin + (modeWidth + gap) * i, modeY));
            modes[i].resize(Coord.of(modeWidth, modes[i].sz.y));
        }
        shelfHeading.move(Coord.of(margin, UI.scale(99)));
        int contentTop = UI.scale(119), footerTop = sz.y - UI.scale(44);
        int contentHeight = Math.max(UI.scale(240), footerTop - contentTop - gap);
        shelfList.move(Coord.of(margin, contentTop));
        shelfList.resize(Coord.of(sidebarWidth, contentHeight));
        shelfList.reset();

        int articleX = margin + sidebarWidth + UI.scale(10);
        boolean hasRelated = relatedList.visible();
        int relatedWidth = hasRelated ?
                Math.max(UI.scale(180), Math.min(UI.scale(235), sz.x * 21 / 100)) : 0;
        int relatedX = hasRelated ? sz.x - margin - relatedWidth : sz.x - margin;
        breadcrumb.move(Coord.of(articleX, UI.scale(66)));
        int articleWidth = Math.max(UI.scale(300), relatedX - articleX - UI.scale(10));
        crestOrnament.move(Coord.of(articleX + Math.max(0, (articleWidth - crestOrnament.sz.x) / 2),
                UI.scale(84)));
        if(hasRelated) {
            relatedHeading.move(Coord.of(relatedX, UI.scale(88)));
            relatedList.move(Coord.of(relatedX, UI.scale(109)));
            relatedList.resize(Coord.of(relatedWidth,
                    Math.max(UI.scale(300), footerTop - UI.scale(109) - gap)));
            relatedList.reset();
        }
        railOrnament.move(Coord.of(articleX - UI.scale(20),
                contentTop + Math.max(0, (contentHeight - railOrnament.sz.y) / 2)));
        layoutArticle();
        int footerY = sz.y - UI.scale(35);
        actionButton.move(Coord.of(articleX, footerY));
        openSourceButton.move(Coord.of(articleX + UI.scale(135), footerY));
        licenseButton.move(Coord.of(articleX + UI.scale(255), footerY));
        attribution.move(Coord.of(articleX + UI.scale(340), sz.y - UI.scale(29)));
        if(sz.x >= UI.scale(1000))
            attribution.show();
        else
            attribution.hide();
    }

    private void layoutArticle() {
        if(articleView == null)
            return;
        int margin = UI.scale(10), sidebarWidth = Math.max(UI.scale(270), Math.min(UI.scale(330), sz.x * 30 / 100));
        int articleX = margin + sidebarWidth + UI.scale(10);
        boolean hasRelated = relatedList.visible();
        int relatedWidth = hasRelated ?
                Math.max(UI.scale(180), Math.min(UI.scale(235), sz.x * 21 / 100)) : 0;
        int relatedX = hasRelated ? sz.x - margin - relatedWidth : sz.x - margin;
        int articleWidth = Math.max(UI.scale(300), relatedX - articleX - UI.scale(10));
        int contentTop = UI.scale(196), footerTop = sz.y - UI.scale(44);
        int contentHeight = Math.max(UI.scale(150), footerTop - contentTop - UI.scale(8));
        if(galleryView.visible()) {
            int imageHeight = Math.min(UI.scale(116), Math.max(UI.scale(84), contentHeight / 3));
            galleryView.move(Coord.of(articleX, contentTop));
            galleryView.resize(Coord.of(articleWidth, imageHeight));
            articleView.move(Coord.of(articleX, contentTop + imageHeight + UI.scale(8)));
            articleView.resize(Coord.of(articleWidth, contentHeight - imageHeight - UI.scale(8)));
        } else {
            articleView.move(Coord.of(articleX, contentTop));
            articleView.resize(Coord.of(articleWidth, contentHeight));
        }
    }

    private static Coord constrain(Coord requested) {
        Coord value = requested == null || requested.equals(LEGACY_DEFAULT_SIZE) ? DEFAULT_SIZE : requested;
        return(Coord.of(Math.max(MINIMUM_SIZE.x, value.x), Math.max(MINIMUM_SIZE.y, value.y)));
    }

    private static String homeText() {
        return("$font[serif,21]{$col[239,225,185]{The MoonFlower Codex}}\n" +
                "$col[207,164,72]{Field archive • settlement reference • survival journal}\n\n" +
                "Search known records as you type, or submit a community archive search for a record " +
                "not yet indexed. Follow linked records without losing your place, and save useful entries.\n\n" +
                "$font[serif,16]{$col[207,164,72]{Two kinds of knowledge}}\n" +
                "$col[73,174,178]{LIVE} records come from the current character's server-provided action menu. " +
                "Open Crafting delegates to Haven's normal crafting flow.\n\n" +
                "$col[207,164,72]{GUIDE} records are player-maintained Ring of Brodgar material. They can be " +
                "incomplete or outdated and never override server state.\n\n" +
                "$col[151,181,181]{Unknown values are omitted rather than invented.}");
    }

    private static String loadingText(WikiReference reference) {
        return("$font[serif,19]{$col[239,225,185]{" + quote(reference.title) + "}}\n" +
                "$col[207,164,72]{GUIDE • community record}\n\nOpening the selected field record...");
    }

    private static String articleText(WikiArticle loaded) {
        StringBuilder rich = new StringBuilder();
        rich.append("$font[serif,21]{$col[239,225,185]{").append(quote(loaded.title)).append("}}\n");
        rich.append("$col[").append(loaded.reference.provenance == WikiReference.Provenance.LIVE ?
                "73,174,178]{LIVE • current session action}" : "207,164,72]{GUIDE • community-maintained record}")
                .append("\n");
        if(loaded.revisionId > 0)
            rich.append("$col[151,181,181]{Revision ").append(loaded.revisionId).append("}\n");
        if(!loaded.categories.isEmpty())
            rich.append("$col[151,181,181]{Filed under: ")
                    .append(quote(String.join(" • ", loaded.categories))).append("}\n");
        rich.append('\n');
        for(String line : loaded.text.split("\\R")) {
            if(line.startsWith("## "))
                rich.append("\n$font[serif,16]{$col[207,164,72]{").append(quote(line.substring(3))).append("}}\n");
            else if(line.startsWith("### "))
                rich.append("\n$font[serif,14]{$col[151,181,181]{").append(quote(line.substring(4))).append("}}\n");
            else
                rich.append(quote(line)).append('\n');
        }
        return(rich.toString());
    }

    private static String firstParagraph(String text) {
        if(text == null)
            return("");
        for(String paragraph : text.split("\\R\\s*\\R")) {
            String value = paragraph.replaceFirst("^#+\\s*", "").trim();
            if(!value.isBlank())
                return(value.length() > 240 ? value.substring(0, 240) : value);
        }
        return("");
    }

    private static String relatedKey(WikiReference reference) {
        if(reference == null)
            return("");
        if(reference.provenance == WikiReference.Provenance.GUIDE && reference.articleUri != null)
            return("guide:" + reference.articleUri.normalize().getPath().toLowerCase(Locale.ROOT));
        return(reference.provenance.name() + ':' + reference.id);
    }

    private static String quote(String text) {return(RichText.Parser.quote(text == null ? "" : text));}
    private static String message(Throwable failure, String fallback) {
        return(failure == null || failure.getMessage() == null || failure.getMessage().isBlank() ?
                fallback : failure.getMessage());
    }

    private final class ShelfList extends SListBox<ShelfEntry, Widget> {
        ShelfList(Coord size) {super(size, UI.scale(52), UI.scale(2));}
        @Override protected List<? extends ShelfEntry> items() {return(shelfEntries);}
        @Override protected void drawbg(GOut g) {MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 232);}
        @Override protected void drawbg(GOut g, ShelfEntry item, int index, Area area) {
            if(item == sel) {
                g.chcolor(new Color(24, 95, 105, 220)); g.frect(area.ul, area.sz());
                g.chcolor(MoonFlowerHudTheme.GOLD); g.rect(area.ul, area.sz().sub(1, 1)); g.chcolor();
            } else if((index & 1) == 0) {
                g.chcolor(new Color(3, 12, 18, 150)); g.frect(area.ul, area.sz()); g.chcolor();
            }
        }
        @Override protected Widget makeitem(ShelfEntry entry, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<ShelfEntry>(this, size, entry);
            row.add(SListWidget.TextItem.of(Coord.of(size.x - UI.scale(12), UI.scale(24)),
                    ROW_TITLE, () -> entry.label), UI.scale(6, 2));
            row.add(SListWidget.TextItem.of(Coord.of(size.x - UI.scale(12), UI.scale(20)),
                    ROW_META, () -> entry.meta), UI.scale(6, 28));
            row.tooltip = entry.meta;
            return(row);
        }
        @Override public void change(ShelfEntry entry) {super.change(entry); openShelfEntry(entry);}
    }

    private final class RelatedList extends SListBox<WikiReference, Widget> {
        RelatedList(Coord size) {super(size, UI.scale(38), UI.scale(2));}
        @Override protected List<? extends WikiReference> items() {return(related);}
        @Override protected void drawbg(GOut g) {MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 220);}
        @Override protected void drawbg(GOut g, WikiReference item, int index, Area area) {
            if(item == sel) {g.chcolor(new Color(24, 95, 105, 220)); g.frect(area.ul, area.sz()); g.chcolor();}
        }
        @Override protected Widget makeitem(WikiReference reference, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<WikiReference>(this, size, reference);
            row.add(SListWidget.TextItem.of(Coord.of(size.x - UI.scale(10), size.y),
                    ROW_TITLE, () -> reference.title), UI.scale(5, 0));
            row.tooltip = reference.provenance.name() + " • Open " + reference.title;
            return(row);
        }
        @Override public void change(WikiReference reference) {
            super.change(reference);
            if(reference != null) open(reference);
        }
    }
}
