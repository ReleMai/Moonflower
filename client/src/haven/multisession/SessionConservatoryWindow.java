package haven.multisession;

import haven.Area;
import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.Text;
import haven.TextEntry;
import haven.Tex;
import haven.TexI;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

/** Embedded MoonFlower presentation for the single-visible-window session host. */
public final class SessionConservatoryWindow extends Window {
    private static final String SIZE_PREF = "wndsz-sessionConservatory";
    private final SessionConservatoryCanvas canvas;

    public SessionConservatoryWindow(SessionConservatoryService service) {
        this(service, SessionConservatoryLayout.PREFERRED);
    }

    public SessionConservatoryWindow(SessionConservatoryService service, Coord size) {
        super(SessionConservatoryLayout.clampSize(size, null), "Session Conservatory");
        canvas = add(new SessionConservatoryCanvas(service, csz()), Coord.z);
        reqclose(() -> {
            canvas.closeLogin();
            hide();
        });
    }

    @Override
    protected Deco makedeco() {
        return(new DefaultDeco(false).dragsize(true));
    }

    @Override
    public void resize(Coord requested) {
        Coord size = SessionConservatoryLayout.clampSize(requested, null);
        super.resize(size);
        if(canvas != null)
            canvas.resize(csz());
        Utils.setprefc(SIZE_PREF, size);
    }

    public void fitTo(Coord available) {
        Coord current = (canvas == null) ? SessionConservatoryLayout.PREFERRED : canvas.sz;
        Coord margin = UI.scale(42, 86);
        Coord maximum = Coord.of(Math.max(SessionConservatoryLayout.MINIMUM.x, available.x - margin.x),
                Math.max(SessionConservatoryLayout.MINIMUM.y, available.y - margin.y));
        resize(SessionConservatoryLayout.clampSize(current, maximum));
    }

    private static final class SessionConservatoryCanvas extends Widget {
        private final SessionConservatoryService service;
        private final TextEntry loginName;
        private final TextEntry loginPassword;
        private final LeafControl loginSubmit;
        private final LeafControl loginCancel;
        private final LeafControl stopWorker;
        private UI.Grab<Widget.PointerEvent> viewportGrab;
        private int railScroll;
        private SessionLaunchMode previousMode = null;
        private String previousPrefill = null;
        private BufferedImage previewImage;
        private TexI previewTexture;
        private final Tex heading = Text.renderstroked("ONE WINDOW - MANY HEARTHS",
                MoonFlowerHudTheme.IVORY, Color.BLACK).tex();
        private final Tex live = Text.renderstroked("LIVE CURRENT SESSION",
                MoonFlowerHudTheme.TEAL_BRIGHT, Color.BLACK).tex();

        SessionConservatoryCanvas(SessionConservatoryService service, Coord size) {
            super(size);
            this.service = service;
            setcanfocus(true);
            loginName = add(new TextEntry(UI.scale(280), ""));
            loginPassword = add(new TextEntry(UI.scale(280), ""));
            loginPassword.pw = true;
            loginSubmit = add(new LeafControl(UI.scale(174, 30), "OPEN INSIDE WINDOW", () -> {
                service.submitLogin(loginName.text(), loginPassword.text());
                loginPassword.settext("");
            }));
            loginCancel = add(new LeafControl(UI.scale(92, 30), "CANCEL", this::closeLogin));
            stopWorker = add(new LeafControl(UI.scale(150, 30), "CLOSE WORKER",
                    service::stopSelectedWorker));
            setLoginControlsVisible(false);
            stopWorker.hide();
        }

        @Override
        public void draw(GOut g) {
            SessionConservatorySnapshot view = service.snapshot();
            SessionCardSnapshot current = findSession(view.sessions(), "current");
            SessionCardSnapshot selected = findSession(view.sessions(), view.selectedSessionId());
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 242);

            int railWidth = Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3));
            Coord rail = UI.scale(14, 48);
            Coord railSize = Coord.of(railWidth, sz.y - UI.scale(66));
            Coord stage = Coord.of(rail.x + railWidth + UI.scale(10), rail.y);
            Coord stageSize = Coord.of(sz.x - stage.x - UI.scale(14), railSize.y);
            MoonFlowerHudTheme.drawPanel(g, rail, railSize, 218);
            MoonFlowerHudTheme.drawPanel(g, stage, stageSize, 218);

            g.aimage(heading, Coord.of(sz.x / 2, UI.scale(27)), 0.5, 0.5);
            drawLauncherRail(g, rail, railSize, current, view);
            if(view.launchMode() == SessionLaunchMode.LOGIN)
                drawEmbeddedLogin(g, stage, stageSize, view);
            else
                drawPreviewAperture(g, stage, stageSize, selected, view);
            updateLoginControls(view, stage, stageSize);
            super.draw(g);
        }

        private static SessionCardSnapshot findSession(List<SessionCardSnapshot> sessions, String id) {
            for(SessionCardSnapshot session : sessions) {
                if(session.sessionId().equals(id))
                    return(session);
            }
            return(null);
        }

        private void drawLauncherRail(GOut g, Coord origin, Coord size, SessionCardSnapshot current,
                                      SessionConservatorySnapshot view) {
            int inset = UI.scale(10);
            Coord fresh = origin.add(inset, UI.scale(12));
            Coord freshSize = Coord.of(size.x - inset * 2, UI.scale(58));
            MoonFlowerHudTheme.drawSlot(g, fresh, freshSize, true, false);
            drawPlus(g, fresh.add(UI.scale(29), freshSize.y / 2), UI.scale(11));
            FastText.aprintfstroked(g, fresh.add(UI.scale(52), UI.scale(20)), 0, 0,
                    "ADD ANOTHER ACCOUNT");
            FastText.aprintfstroked(g, fresh.add(UI.scale(52), UI.scale(38)), 0, 0,
                    "OPEN LOGIN +");

            int rowHeight = UI.scale(42);
            int rowGap = UI.scale(6);
            int rowY = fresh.y + freshSize.y + UI.scale(12);
            int currentHeight = (current == null) ? 0 : UI.scale(82);
            int availableRows = Math.max(0, (origin.y + size.y - rowY - currentHeight - UI.scale(18)) /
                    (rowHeight + rowGap));
            int activeCount = Math.max(0, view.sessions().size() - 1);
            int totalRows = activeCount + view.launchOptions().size();
            int start = railScroll(totalRows, availableRows);
            int shown = Math.min(availableRows, totalRows - start);
            for(int index = 0; index < shown; index++) {
                int rowIndex = start + index;
                Coord row = Coord.of(fresh.x, rowY + index * (rowHeight + rowGap));
                if(rowIndex < activeCount) {
                    drawSessionRow(g, row, Coord.of(freshSize.x, rowHeight), view.sessions().get(rowIndex + 1));
                } else {
                    SessionLaunchOptionSnapshot option = view.launchOptions().get(rowIndex - activeCount);
                    Coord rowSize = Coord.of(freshSize.x, rowHeight);
                    MoonFlowerHudTheme.drawSlot(g, row, rowSize, option.directSignInReady(), false);
                    FastText.aprintfstroked(g, row.add(UI.scale(12), rowHeight / 2), 0, 0.5,
                            "%s", shortText(option.accountLabel(), 18));
                    drawPlus(g, row.add(rowSize.x - UI.scale(22), rowHeight / 2), UI.scale(7));
                }
            }
            if(start > 0)
                FastText.aprintfstroked(g, fresh.add(freshSize.x / 2, rowY - fresh.y - UI.scale(3)),
                        0.5, 1, "^ SCROLL FOR MORE");
            if(start + shown < totalRows)
                FastText.aprintfstroked(g, fresh.add(freshSize.x / 2, rowY - fresh.y +
                        shown * (rowHeight + rowGap) - UI.scale(3)), 0.5, 0, "v SCROLL FOR MORE");

            if(current != null) {
                Coord selected = Coord.of(fresh.x, origin.y + size.y - currentHeight - UI.scale(8));
                Coord selectedSize = Coord.of(freshSize.x, currentHeight);
                MoonFlowerHudTheme.drawSlot(g, selected, selectedSize, true, current.selected());
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(18)), 0, 0,
                        "CURRENTLY PLAYING");
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(39)), 0, 0,
                        "%s", shortText(current.characterName(), 18));
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(60)), 0, 0,
                        "%s + %s", shortText(current.accountLabel(), 13), current.state().provenance);
            }
        }

        private void drawSessionRow(GOut g, Coord row, Coord rowSize, SessionCardSnapshot session) {
            MoonFlowerHudTheme.drawSlot(g, row, rowSize, true, session.selected());
            FastText.aprintfstroked(g, row.add(UI.scale(12), UI.scale(16)), 0, 0,
                    "%s", shortText(session.accountLabel(), 16));
            FastText.aprintfstroked(g, row.add(UI.scale(12), UI.scale(32)), 0, 0,
                    "%s / %s", session.state().label, session.state().provenance);
        }

        private void drawEmbeddedLogin(GOut g, Coord origin, Coord size, SessionConservatorySnapshot view) {
            int inset = UI.scale(13);
            Coord panel = origin.add(inset, inset);
            Coord panelSize = size.sub(inset * 2, inset * 2);
            MoonFlowerHudTheme.drawSlot(g, panel, panelSize, true, true);
            Coord center = panel.add(panelSize.x / 2, UI.scale(48));
            MoonFlowerHudTheme.drawBlossom(g, center, UI.scale(18));
            FastText.aprintfstroked(g, center.add(0, UI.scale(35)), 0.5, 0,
                    "OPEN ANOTHER HEARTH INSIDE MOONFLOWER");
            FastText.aprintfstroked(g, center.add(0, UI.scale(58)), 0.5, 0,
                    "ACCOUNT NAME");
            FastText.aprintfstroked(g, center.add(0, UI.scale(112)), 0.5, 0,
                    "PASSWORD OR LOGIN TOKEN");
            FastText.aprintfstroked(g, center.add(0, UI.scale(225)), 0.5, 0,
                    "%s", safe(view.status()));
            FastText.aprintfstroked(g, center.add(0, UI.scale(248)), 0.5, 0,
                    "THE NEW CLIENT WILL REMAIN INSIDE THIS WINDOW");
        }

        private void drawPreviewAperture(GOut g, Coord origin, Coord size, SessionCardSnapshot card,
                                         SessionConservatorySnapshot view) {
            int inset = UI.scale(13);
            Coord aperture = origin.add(inset, inset);
            Coord apertureSize = size.sub(inset * 2, inset * 2);
            MoonFlowerHudTheme.drawSlot(g, aperture, apertureSize, card != null, true);
            g.chcolor(Color.BLACK);
            g.frect(aperture, apertureSize);
            g.chcolor();

            BufferedImage frame = service.selectedFrame();
            Coord nativeSize = service.selectedPreviewSize();
            Area video = SessionConservatoryLayout.previewArea(apertureSize, nativeSize);
            video = Area.sized(aperture.add(video.ul), video.sz());
            if(frame != null && nativeSize != null) {
                if(frame != previewImage) {
                    if(previewTexture != null)
                        previewTexture.dispose();
                    previewImage = frame;
                    previewTexture = new TexI(frame, false);
                }
                g.chcolor(Color.WHITE);
                g.image(previewTexture, video.ul, video.sz());
                g.chcolor();
            } else if(card != null) {
                Coord center = aperture.add(apertureSize.div(2));
                int bloomRadius = Math.max(UI.scale(18), Math.min(apertureSize.x, apertureSize.y) / 9);
                MoonFlowerHudTheme.drawBlossom(g, center.add(0, -UI.scale(32)), bloomRadius);
                g.aimage(card.sessionId().equals("current") ? live : heading,
                        center.add(0, UI.scale(20)), 0.5, 0.5);
            }
            if(card != null) {
                FastText.aprintfstroked(g, aperture.add(UI.scale(10), apertureSize.y - UI.scale(38)), 0, 0,
                        "%s", safe(card.detail()));
                FastText.aprintfstroked(g, aperture.add(UI.scale(10), apertureSize.y - UI.scale(18)), 0, 0,
                        "%s", safe(view.status()));
            }
        }

        private static String safe(String value) {
            String text = (value == null) ? "Unavailable" : value;
            StringBuilder safe = new StringBuilder(text.length());
            for(int i = 0; i < text.length(); i++) {
                char glyph = text.charAt(i);
                safe.append(glyph < 256 ? glyph : '?');
            }
            text = safe.toString();
            int limit = 72;
            return(text.length() <= limit ? text : text.substring(0, limit - 3) + "...");
        }

        private static String shortText(String value, int limit) {
            String text = safe(value);
            return(text.length() <= limit ? text : text.substring(0, Math.max(1, limit - 3)) + "...");
        }

        private static void drawPlus(GOut g, Coord center, int radius) {
            MoonFlowerHudTheme.drawBlossom(g, center, Math.max(UI.scale(5), radius));
            g.chcolor(MoonFlowerHudTheme.IVORY);
            g.line(center.add(-radius, 0), center.add(radius, 0), Math.max(1, UI.scale(2)));
            g.line(center.add(0, -radius), center.add(0, radius), Math.max(1, UI.scale(2)));
            g.chcolor();
        }

        private void updateLoginControls(SessionConservatorySnapshot view, Coord stage, Coord stageSize) {
            boolean loginVisible = view.launchMode() == SessionLaunchMode.LOGIN;
            setLoginControlsVisible(loginVisible);
            boolean stopVisible = !loginVisible && service.selectedWorker();
            stopWorker.show(stopVisible);
            if(stopVisible)
                stopWorker.c = Coord.of(stage.x + stageSize.x - stopWorker.sz.x - UI.scale(12),
                        stage.y + stageSize.y - stopWorker.sz.y - UI.scale(10));
            if(!loginVisible) {
                previousMode = view.launchMode();
                return;
            }
            if(previousMode != view.launchMode() || !view.loginAccountLabel().equals(previousPrefill)) {
                loginName.settext(view.loginAccountLabel());
                loginPassword.settext("");
                previousPrefill = view.loginAccountLabel();
            }
            previousMode = view.launchMode();
            int width = Math.max(UI.scale(220), Math.min(UI.scale(300), stageSize.x - UI.scale(54)));
            int x = stage.x + (stageSize.x - width) / 2;
            int y = stage.y + UI.scale(140);
            loginName.resize(Coord.of(width, loginName.sz.y));
            loginPassword.resize(Coord.of(width, loginPassword.sz.y));
            loginName.c = Coord.of(x, y);
            loginPassword.c = Coord.of(x, y + UI.scale(47));
            int actionsY = y + UI.scale(94);
            loginSubmit.c = Coord.of(x, actionsY);
            loginCancel.c = Coord.of(x + width - loginCancel.sz.x, actionsY);
        }

        private void setLoginControlsVisible(boolean visible) {
            loginName.show(visible);
            loginPassword.show(visible);
            loginSubmit.show(visible);
            loginCancel.show(visible);
        }

        private void closeLogin() {
            loginPassword.settext("");
            service.cancelLogin();
        }

        private Coord stage() {
            int railWidth = Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3));
            return(Coord.of(UI.scale(14) + railWidth + UI.scale(10), UI.scale(48)));
        }

        private Coord railOrigin() {
            return(UI.scale(14, 48));
        }

        private Coord railSize() {
            int width = Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3));
            return(Coord.of(width, sz.y - UI.scale(66)));
        }

        private int railRowY() {
            return(railOrigin().y + UI.scale(12) + UI.scale(58) + UI.scale(12));
        }

        private int railCapacity() {
            int rowHeight = UI.scale(42);
            int rowGap = UI.scale(6);
            int currentHeight = UI.scale(82);
            return(Math.max(0, (railOrigin().y + railSize().y - railRowY() - currentHeight - UI.scale(18)) /
                    (rowHeight + rowGap)));
        }

        private int railScroll(int totalRows, int capacity) {
            railScroll = Math.max(0, Math.min(railScroll, Math.max(0, totalRows - capacity)));
            return(railScroll);
        }

        private boolean inRail(Coord point) {
            return(point.isect(railOrigin(), railSize()));
        }

        private Coord stageSize() {
            Coord railSize = Coord.of(Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3)),
                    sz.y - UI.scale(66));
            Coord stage = stage();
            return(Coord.of(sz.x - stage.x - UI.scale(14), railSize.y));
        }

        private Area videoArea() {
            Coord stage = stage();
            Coord stageSize = stageSize();
            int inset = UI.scale(13);
            Coord aperture = stage.add(inset, inset);
            Coord apertureSize = stageSize.sub(inset * 2, inset * 2);
            Area relative = SessionConservatoryLayout.previewArea(apertureSize, service.selectedPreviewSize());
            return(Area.sized(aperture.add(relative.ul), relative.sz()));
        }

        private Coord workerPoint(Coord local) {
            Area video = videoArea();
            Coord nativeSize = service.selectedPreviewSize();
            int x = Math.max(0, Math.min(video.sz().x - 1,
                    (local.x - video.ul.x) * nativeSize.x / Math.max(1, video.sz().x)));
            int y = Math.max(0, Math.min(video.sz().y - 1,
                    (local.y - video.ul.y) * nativeSize.y / Math.max(1, video.sz().y)));
            return(Coord.of(x, y));
        }

        private int inputModifiers() {
            return(ui == null ? 0 : ui.modflags());
        }

        private boolean inVideo(Coord local) {
            return(service.selectedWorker() && videoArea().contains(local));
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b == 1) {
                SessionConservatorySnapshot view = service.snapshot();
                Coord rail = UI.scale(14, 48);
                int railWidth = Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3));
                Coord railSize = Coord.of(railWidth, sz.y - UI.scale(66));
                int inset = UI.scale(10);
                Coord fresh = rail.add(inset, UI.scale(12));
                Coord freshSize = Coord.of(railSize.x - inset * 2, UI.scale(58));
                if(event.c.isect(fresh, freshSize)) {
                    service.requestFreshLogin();
                    return(true);
                }
                int rowHeight = UI.scale(42);
                int rowGap = UI.scale(6);
                int rowY = fresh.y + freshSize.y + UI.scale(12);
                int availableRows = railCapacity();
                int activeCount = Math.max(0, view.sessions().size() - 1);
                int totalRows = activeCount + view.launchOptions().size();
                int start = railScroll(totalRows, availableRows);
                int shown = Math.min(availableRows, totalRows - start);
                for(int index = 0; index < shown; index++) {
                    int rowIndex = start + index;
                    Coord row = Coord.of(fresh.x, rowY + index * (rowHeight + rowGap));
                    if(event.c.isect(row, Coord.of(freshSize.x, rowHeight))) {
                        if(rowIndex < activeCount)
                            service.selectSession(view.sessions().get(rowIndex + 1).sessionId());
                        else
                            service.requestKnownAccount(view.launchOptions().get(rowIndex - activeCount).accountLabel());
                        setfocus(this);
                        return(true);
                    }
                }
                int currentHeight = UI.scale(82);
                Coord current = Coord.of(fresh.x, rail.y + railSize.y - currentHeight - UI.scale(8));
                if(event.c.isect(current, Coord.of(freshSize.x, currentHeight))) {
                    service.selectSession("current");
                    setfocus(this);
                    return(true);
                }
            }
            if(service.selectedWorker() && inVideo(event.c)) {
                Coord point = workerPoint(event.c);
                service.routeMouseDown(point.x, point.y, event.b, inputModifiers());
                if(ui != null)
                    viewportGrab = ui.grab(this, Widget.PointerEvent.class, this::handleViewportGrab);
                setfocus(this);
                return(true);
            }
            return(super.mousedown(event));
        }

        private boolean handleViewportGrab(Widget.PointerEvent event) {
            Coord local = event.c.add(event.target.rootpos()).sub(rootpos());
            if(event instanceof MouseMoveEvent) {
                Coord point = clampWorkerPoint(local);
                service.routeMouseMove(point.x, point.y, inputModifiers());
                return(true);
            } else if(event instanceof MouseWheelEvent) {
                Coord point = clampWorkerPoint(local);
                MouseWheelEvent wheel = (MouseWheelEvent)event;
                service.routeMouseWheel(point.x, point.y, (int)Math.round(wheel.s), inputModifiers());
                return(true);
            } else if(event instanceof MouseUpEvent) {
                Coord point = clampWorkerPoint(local);
                MouseUpEvent up = (MouseUpEvent)event;
                service.routeMouseUp(point.x, point.y, up.b, inputModifiers());
                if(viewportGrab != null) {
                    viewportGrab.remove();
                    viewportGrab = null;
                }
                return(true);
            }
            return(false);
        }

        private Coord clampWorkerPoint(Coord local) {
            Area video = videoArea();
            return(workerPoint(Coord.of(Math.max(video.ul.x, Math.min(video.br.x - 1, local.x)),
                    Math.max(video.ul.y, Math.min(video.br.y - 1, local.y)))));
        }

        @Override
        public boolean mousewheel(MouseWheelEvent event) {
            SessionConservatorySnapshot view = service.snapshot();
            int totalRows = viewTotalRows(view);
            if(inRail(event.c) && (totalRows > railCapacity())) {
                railScroll += (event.a < 0) ? 1 : -1;
                railScroll(totalRows, railCapacity());
                return(true);
            }
            if(inVideo(event.c)) {
                Coord point = workerPoint(event.c);
                service.routeMouseWheel(point.x, point.y, (int)Math.round(event.s), inputModifiers());
                return(true);
            }
            return(super.mousewheel(event));
        }

        private int viewTotalRows(SessionConservatorySnapshot view) {
            return(Math.max(0, view.sessions().size() - 1) + view.launchOptions().size());
        }

        @Override
        public void mousemove(MouseMoveEvent event) {
            if(inVideo(event.c)) {
                Coord point = workerPoint(event.c);
                service.routeMouseMove(point.x, point.y, inputModifiers());
            }
            super.mousemove(event);
        }

        @Override
        public boolean keydown(KeyDownEvent event) {
            if(service.selectedWorker()) {
                service.routeKey(true, event.code, event.mods, event.c);
                return(true);
            }
            return(super.keydown(event));
        }

        @Override
        public boolean keyup(KeyUpEvent event) {
            if(service.selectedWorker()) {
                service.routeKey(false, event.code, event.mods, event.c);
                return(true);
            }
            return(super.keyup(event));
        }

        @Override
        public void destroy() {
            if(viewportGrab != null)
                viewportGrab.remove();
            loginPassword.settext("");
            service.close();
            if(previewTexture != null)
                previewTexture.dispose();
            heading.dispose();
            live.dispose();
            super.destroy();
        }

        private static final class LeafControl extends Widget {
            private final String label;
            private final Runnable action;
            private boolean hover;

            LeafControl(Coord size, String label, Runnable action) {
                super(size);
                this.label = label;
                this.action = action;
            }

            @Override
            public void draw(GOut g) {
                MoonFlowerHudTheme.drawLeafButton(g, Coord.z, sz, hover, hover);
                FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5, "%s", label);
            }

            @Override
            public boolean mousedown(MouseDownEvent event) {
                if(event.b == 1) {
                    action.run();
                    return(true);
                }
                return(false);
            }

            @Override
            public void mousemove(MouseMoveEvent event) {
                hover = event.c.isect(Coord.z, sz);
            }
        }
    }
}
