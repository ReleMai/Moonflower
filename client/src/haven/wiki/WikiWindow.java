package haven.wiki;

import haven.Area;
import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.Label;
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
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Native, read-only Ring of Brodgar search and article reader. */
public final class WikiWindow extends Window {
    private static final Coord DEFAULT_SIZE = UI.scale(1000, 650);
    private static final Coord LEGACY_DEFAULT_SIZE = UI.scale(860, 565);
    private static final Coord MINIMUM_SIZE = UI.scale(760, 500);
    private static final Text.Foundry ROW_TITLE =
            new Text.Foundry(Text.sans, 13, new Color(239, 211, 126)).aa(true);
    private static final Text.Foundry ROW_SNIPPET =
            new Text.Foundry(Text.sans, 10, new Color(188, 198, 205)).aa(true);
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final GameUI gui;
    private final RingOfBrodgarWikiService service;
    private final TextEntry searchEntry;
    private final Button searchButton;
    private final Button homeButton;
    private final Label status;
    private final Label resultsHeading;
    private final Label articleHeading;
    private final ResultList resultList;
    private final WikiImageView imageView;
    private final RichTextBox articleView;
    private final Button readArticleButton;
    private final Button openWebButton;
    private final Button licenseButton;
    private final Label attribution;
    private final List<WikiSearchResult> results = new ArrayList<>();
    private Future<WikiSearchResponse> pendingSearch;
    private Future<WikiArticle> pendingArticle;
    private WikiSearchResult selected;
    private WikiArticle article;
    private String requestedArticleTitle;
    private String baseStatus = "Search for an item, creature, skill, or game mechanic.";
    private long displayedCooldown = -1;

    public WikiWindow(GameUI gui, RingOfBrodgarWikiService service, Coord initialSize) {
        super(constrain(initialSize), "RoB Wiki");
        this.gui = gui;
        this.service = service;

        searchEntry = add(new TextEntry(UI.scale(650), "") {
            @Override
            public void activate(String text) {
                search();
            }
        }, UI.scale(10, 8));
        searchButton = add(new Button(UI.scale(80), "Search", this::search), UI.scale(670, 8));
        homeButton = add(new Button(UI.scale(90), "Wiki home",
                () -> openExternal(RingOfBrodgarWikiService.HOME_URI)), UI.scale(760, 8));
        status = add(new Label(baseStatus), UI.scale(10, 40));
        status.setcolor(new Color(214, 197, 145));

        resultsHeading = add(new Label("Search results"), UI.scale(10, 66));
        articleHeading = add(new Label("Wiki page"), UI.scale(310, 66));
        resultList = add(new ResultList(UI.scale(290, 510)), UI.scale(10, 84));
        imageView = add(new WikiImageView(service, UI.scale(680, 165)), UI.scale(310, 84));
        imageView.hide();
        articleView = add(new RichTextBox(UI.scale(680, 510), initialArticle()), UI.scale(310, 84));
        articleView.bg = new Color(7, 12, 18, 238);

        readArticleButton = add(new Button(UI.scale(110), "Read in game", this::readSelected),
                UI.scale(10, 608));
        openWebButton = add(new Button(UI.scale(90), "Open web", this::openSelectedWeb),
                UI.scale(130, 608));
        licenseButton = add(new Button(UI.scale(75), "License",
                () -> openExternal(RingOfBrodgarWikiService.COPYRIGHT_URI)), UI.scale(230, 608));
        readArticleButton.disable(true);
        openWebButton.disable(true);
        attribution = add(new Label("Unofficial player-maintained source • Ring of Brodgar text is GFDL"),
                UI.scale(315, 613));
        attribution.setcolor(new Color(165, 175, 185));

        reqclose(this::hide);
        resize(constrain(initialSize));
    }

    public static Coord defaultSize() {
        return(DEFAULT_SIZE);
    }

    @Override
    protected Deco makedeco() {
        return(new DefaultDeco(true).dragsize(true));
    }

    public void focusSearch() {
        setfocus(searchEntry);
    }

    private void search() {
        if(pendingSearch != null) {
            setBaseStatus("A wiki search is already running.");
            return;
        }
        String query = RingOfBrodgarWikiService.normalizeQuery(searchEntry.text());
        if(query.length() < 2) {
            setBaseStatus("Enter at least two characters.");
            return;
        }
        searchEntry.settext(query);
        pendingSearch = service.search(query);
        searchButton.disable(true);
        setBaseStatus("Searching Ring of Brodgar...");
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(pendingSearch != null && pendingSearch.isDone())
            finishSearch();
        if(pendingArticle != null && pendingArticle.isDone())
            finishArticle();
        updateCooldownStatus();
    }

    private void finishSearch() {
        try {
            WikiSearchResponse response = pendingSearch.get();
            results.clear();
            results.addAll(response.results);
            resultList.reset();
            resultList.change(results.isEmpty() ? null : results.get(0));
            setBaseStatus(results.isEmpty() ? "No Ring of Brodgar articles matched “" +
                    response.query + "”." : results.size() + " result" +
                    (results.size() == 1 ? "" : "s") + " for “" + response.query + "”" +
                    (response.cached ? " • cached" : " • downloaded"));
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            setBaseStatus("Wiki search was interrupted.");
        } catch(ExecutionException failure) {
            Throwable cause = failure.getCause();
            if(cause instanceof RingOfBrodgarWikiService.RateLimitException) {
                RingOfBrodgarWikiService.RateLimitException limited =
                        (RingOfBrodgarWikiService.RateLimitException)cause;
                setBaseStatus("Please wait " + limited.remainingSeconds +
                        "s before a different wiki search. Cached searches remain available.");
            } else {
                setBaseStatus(message(cause, "Could not search Ring of Brodgar."));
            }
        } finally {
            pendingSearch = null;
            searchButton.disable(false);
        }
    }

    private void readSelected() {
        if(selected == null || pendingArticle != null)
            return;
        requestedArticleTitle = selected.title;
        pendingArticle = service.article(selected.title);
        readArticleButton.disable(true);
        setBaseStatus("Loading “" + selected.title + "” for in-game reading...");
    }

    private void finishArticle() {
        try {
            WikiArticle loaded = pendingArticle.get();
            if(selected != null && selected.title.equals(requestedArticleTitle)) {
                article = loaded;
                articleHeading.settext(loaded.title);
                articleView.settext(articleText(loaded));
                imageView.setImage(loaded.leadImageUri);
                layoutArticle();
                setBaseStatus("Loaded “" + loaded.title + "”" +
                        (loaded.cached ? " • cached" : " • downloaded") +
                        (loaded.leadImageUri == null ? " • no lead image" : " • image loading"));
            }
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            setBaseStatus("Wiki article loading was interrupted.");
        } catch(ExecutionException failure) {
            setBaseStatus(message(failure.getCause(), "Could not load this wiki article."));
        } finally {
            pendingArticle = null;
            requestedArticleTitle = null;
            readArticleButton.disable(selected == null);
        }
    }

    private void updateCooldownStatus() {
        long cooldown = service.secondsUntilNextRequest();
        if(cooldown == displayedCooldown)
            return;
        displayedCooldown = cooldown;
        status.settext(baseStatus + (cooldown > 0 ? " • new search in " + cooldown + "s" : ""));
    }

    private void setBaseStatus(String text) {
        baseStatus = text;
        displayedCooldown = -1;
        updateCooldownStatus();
    }

    private void showSelection(WikiSearchResult result) {
        selected = result;
        article = null;
        imageView.setImage(null);
        readArticleButton.disable(result == null || pendingArticle != null);
        openWebButton.disable(result == null);
        articleHeading.settext(result == null ? "Wiki page" : result.title);
        articleView.settext(result == null ? initialArticle() : searchPreview(result));
        layoutArticle();
    }

    private String searchPreview(WikiSearchResult result) {
        StringBuilder text = new StringBuilder();
        text.append("$font[serif,18]{$col[239,211,126]{")
                .append(quote(result.title)).append("}}\n\n");
        if(result.updatedAt != null)
            text.append("Last wiki edit: ").append(quote(UPDATED.format(result.updatedAt))).append("\n");
        if(result.wordCount > 0)
            text.append("Article length: ").append(result.wordCount).append(" words\n");
        text.append("\n").append(quote(result.snippet.isBlank() ?
                "No useful excerpt was returned for this result." : result.snippet));
        text.append("\n\n$col[239,211,126]{Select Read in game to load the full article and its lead image.}");
        text.append("\n\n$col[188,169,113]{Community-maintained information may be incomplete or outdated.}");
        return(text.toString());
    }

    private String articleText(WikiArticle loaded) {
        StringBuilder rich = new StringBuilder();
        rich.append("$font[serif,19]{$col[239,211,126]{").append(quote(loaded.title)).append("}}\n");
        if(loaded.revisionId > 0)
            rich.append("$col[145,165,182]{Wiki revision ").append(loaded.revisionId).append("}\n");
        rich.append('\n');
        for(String line : loaded.text.split("\\R")) {
            if(line.startsWith("## "))
                rich.append("\n$font[serif,16]{$col[226,197,109]{")
                        .append(quote(line.substring(3))).append("}}\n");
            else if(line.startsWith("### "))
                rich.append("\n$font[serif,14]{$col[205,185,125]{")
                        .append(quote(line.substring(4))).append("}}\n");
            else
                rich.append(quote(line)).append('\n');
        }
        rich.append("\n$col[145,165,182]{Source: ").append(quote(loaded.articleUri.toASCIIString()))
                .append("}");
        return(rich.toString());
    }

    private static String initialArticle() {
        return("$font[serif,18]{$col[239,211,126]{Ring of Brodgar Wiki}}\n\n" +
                "Search the unofficial Haven & Hearth wiki without leaving the game. " +
                "Exact title matches are placed first, and result excerpts are cleaned for readability.\n\n" +
                "$col[239,211,126]{Choose a result, then select Read in game to load the full page and lead image.}\n\n" +
                "$col[188,169,113]{Searches run only when submitted. Different network searches are " +
                "limited to one per minute; repeated searches and opened pages are cached.}");
    }

    private void openSelectedWeb() {
        if(selected != null)
            openExternal(article == null ? selected.articleUri : article.articleUri);
    }

    private void openExternal(URI uri) {
        if(uri == null || !"https".equalsIgnoreCase(uri.getScheme()) ||
                !"ringofbrodgar.com".equalsIgnoreCase(uri.getHost())) {
            gui.error("Refused an unsafe wiki link.");
            return;
        }
        try {
            ui.wnd.toolkit().browse(uri);
        } catch(Exception failure) {
            gui.error("Could not open web browser: " + failure.getMessage());
        }
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
        int margin = UI.scale(10);
        int buttonWidth = UI.scale(80);
        int homeWidth = UI.scale(90);
        int gap = UI.scale(10);
        int searchWidth = Math.max(UI.scale(300), sz.x - (margin * 2) - buttonWidth - homeWidth - (gap * 2));
        searchEntry.move(Coord.of(margin, UI.scale(8)));
        searchEntry.resize(searchWidth);
        searchButton.move(Coord.of(margin + searchWidth + gap, UI.scale(8)));
        homeButton.move(Coord.of(sz.x - margin - homeWidth, UI.scale(8)));
        status.move(Coord.of(margin, UI.scale(40)));

        int listWidth = Math.max(UI.scale(245), Math.min(UI.scale(320), sz.x * 31 / 100));
        int contentTop = UI.scale(84);
        int footerTop = sz.y - UI.scale(42);
        int contentHeight = Math.max(UI.scale(375), footerTop - contentTop - UI.scale(7));
        int articleX = margin + listWidth + gap;
        int articleWidth = Math.max(UI.scale(430), sz.x - articleX - margin);
        resultsHeading.move(Coord.of(margin, UI.scale(66)));
        articleHeading.move(Coord.of(articleX, UI.scale(66)));
        resultList.move(Coord.of(margin, contentTop));
        resultList.resize(Coord.of(listWidth, contentHeight));
        resultList.reset();
        articleView.move(Coord.of(articleX, contentTop));
        articleView.resize(Coord.of(articleWidth, contentHeight));
        imageView.move(Coord.of(articleX, contentTop));
        layoutArticle();

        int footerY = sz.y - UI.scale(33);
        readArticleButton.move(Coord.of(margin, footerY));
        openWebButton.move(Coord.of(margin + UI.scale(120), footerY));
        licenseButton.move(Coord.of(margin + UI.scale(220), footerY));
        attribution.move(Coord.of(margin + UI.scale(305), sz.y - UI.scale(28)));
    }

    private void layoutArticle() {
        if(articleView == null)
            return;
        int margin = UI.scale(10);
        int listWidth = Math.max(UI.scale(245), Math.min(UI.scale(320), sz.x * 31 / 100));
        int articleX = margin + listWidth + UI.scale(10);
        int contentTop = UI.scale(84);
        int footerTop = sz.y - UI.scale(42);
        int contentHeight = Math.max(UI.scale(375), footerTop - contentTop - UI.scale(7));
        int articleWidth = Math.max(UI.scale(430), sz.x - articleX - margin);
        if(article != null && article.leadImageUri != null) {
            int imageHeight = Math.min(UI.scale(180), Math.max(UI.scale(110), contentHeight / 3));
            imageView.show();
            imageView.move(Coord.of(articleX, contentTop));
            imageView.resize(Coord.of(articleWidth, imageHeight));
            articleView.move(Coord.of(articleX, contentTop + imageHeight + UI.scale(8)));
            articleView.resize(Coord.of(articleWidth, contentHeight - imageHeight - UI.scale(8)));
        } else {
            imageView.hide();
            articleView.move(Coord.of(articleX, contentTop));
            articleView.resize(Coord.of(articleWidth, contentHeight));
        }
    }

    private static Coord constrain(Coord requested) {
        Coord value = requested == null || requested.equals(LEGACY_DEFAULT_SIZE) ? DEFAULT_SIZE : requested;
        return(Coord.of(Math.max(MINIMUM_SIZE.x, value.x), Math.max(MINIMUM_SIZE.y, value.y)));
    }

    private static String quote(String text) {
        return(RichText.Parser.quote(text == null ? "" : text));
    }

    private static String message(Throwable failure, String fallback) {
        return(failure == null || failure.getMessage() == null || failure.getMessage().isBlank() ?
                fallback : failure.getMessage());
    }

    private final class ResultList extends SListBox<WikiSearchResult, Widget> {
        ResultList(Coord size) {
            super(size, UI.scale(56), UI.scale(2));
        }

        @Override
        protected List<? extends WikiSearchResult> items() {
            return(results);
        }

        @Override
        protected void drawbg(GOut g) {
            g.chcolor(new Color(8, 13, 19, 225));
            g.frect(Coord.z, sz);
            g.chcolor();
        }

        @Override
        protected void drawbg(GOut g, WikiSearchResult item, int index, Area area) {
            g.chcolor(index % 2 == 0 ? new Color(31, 36, 41, 210) : new Color(23, 28, 34, 210));
            g.frect(area.ul, area.sz());
            g.chcolor();
        }

        @Override
        protected Widget makeitem(WikiSearchResult result, int index, Coord size) {
            Widget row = new SListWidget.ItemWidget<WikiSearchResult>(this, size, result);
            row.add(SListWidget.TextItem.of(Coord.of(size.x - UI.scale(10), UI.scale(24)),
                    ROW_TITLE, () -> result.title), UI.scale(5, 2));
            row.add(SListWidget.TextItem.of(Coord.of(size.x - UI.scale(10), UI.scale(26)),
                    ROW_SNIPPET, () -> result.snippet), UI.scale(5, 28));
            return(row);
        }

        @Override
        public void change(WikiSearchResult result) {
            super.change(result);
            showSelection(result);
        }
    }
}
