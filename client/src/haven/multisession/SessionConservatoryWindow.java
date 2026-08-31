package haven.multisession;

import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.Text;
import haven.TextEntry;
import haven.Tex;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;

import java.awt.Color;

/** Embedded MoonFlower presentation for the single-visible-window session host. */
public final class SessionConservatoryWindow extends Window {
    private final SessionConservatoryCanvas canvas;

    public SessionConservatoryWindow(SessionConservatoryService service) {
        super(SessionConservatoryLayout.PREFERRED, "Session Conservatory");
        canvas = add(new SessionConservatoryCanvas(service, SessionConservatoryLayout.PREFERRED), Coord.z);
        reqclose(() -> {
            canvas.closeLogin();
            hide();
        });
    }

    public void fitTo(Coord available) {
        Coord fitted = SessionConservatoryLayout.fittedSize(available);
        resize(fitted);
        canvas.resize(fitted);
    }

    private static final class SessionConservatoryCanvas extends Widget {
        private final SessionConservatoryService service;
        private final TextEntry loginName;
        private final TextEntry loginPassword;
        private final LeafControl loginSubmit;
        private final LeafControl loginCancel;
        private SessionLaunchMode previousMode = null;
        private String previousPrefill = null;
        private final Tex heading = Text.renderstroked("ONE WINDOW - MANY HEARTHS",
                MoonFlowerHudTheme.IVORY, Color.BLACK).tex();
        private final Tex live = Text.renderstroked("LIVE CURRENT SESSION",
                MoonFlowerHudTheme.TEAL_BRIGHT, Color.BLACK).tex();

        SessionConservatoryCanvas(SessionConservatoryService service, Coord size) {
            super(size);
            this.service = service;
            loginName = add(new TextEntry(UI.scale(280), ""));
            loginPassword = add(new TextEntry(UI.scale(280), ""));
            loginPassword.pw = true;
            loginSubmit = add(new LeafControl(UI.scale(174, 30), "OPEN INSIDE WINDOW", () -> {
                service.noteLoginBlocked(loginName.text());
                loginPassword.settext("");
            }));
            loginCancel = add(new LeafControl(UI.scale(92, 30), "CANCEL", this::closeLogin));
            setLoginControlsVisible(false);
        }

        @Override
        public void draw(GOut g) {
            SessionConservatorySnapshot view = service.snapshot();
            SessionCardSnapshot current = view.sessions().isEmpty() ? null : view.sessions().get(0);
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 242);
            MoonFlowerHudTheme.drawWindowFrame(g, Coord.z, sz);

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
                drawPreviewAperture(g, stage, stageSize, current, view);
            updateLoginControls(view, stage, stageSize);
            super.draw(g);
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

            int rowHeight = UI.scale(38);
            int rowGap = UI.scale(6);
            int rowY = fresh.y + freshSize.y + UI.scale(12);
            int currentHeight = (current == null) ? 0 : UI.scale(82);
            int availableRows = Math.max(0, (origin.y + size.y - rowY - currentHeight - UI.scale(18)) /
                    (rowHeight + rowGap));
            int shown = Math.min(availableRows, view.launchOptions().size());
            for(int index = 0; index < shown; index++) {
                SessionLaunchOptionSnapshot option = view.launchOptions().get(index);
                Coord row = Coord.of(fresh.x, rowY + index * (rowHeight + rowGap));
                Coord rowSize = Coord.of(freshSize.x, rowHeight);
                MoonFlowerHudTheme.drawSlot(g, row, rowSize, option.directSignInReady(), false);
                FastText.aprintfstroked(g, row.add(UI.scale(12), rowHeight / 2), 0, 0.5,
                        "%s", shortText(option.accountLabel(), 18));
                drawPlus(g, row.add(rowSize.x - UI.scale(22), rowHeight / 2), UI.scale(7));
            }
            if(view.launchOptions().size() > shown)
                FastText.aprintfstroked(g, fresh.add(freshSize.x / 2, rowY - fresh.y +
                        shown * (rowHeight + rowGap)), 0.5, 0, "+ %d MORE",
                        view.launchOptions().size() - shown);

            if(current != null) {
                Coord selected = Coord.of(fresh.x, origin.y + size.y - currentHeight - UI.scale(8));
                Coord selectedSize = Coord.of(freshSize.x, currentHeight);
                MoonFlowerHudTheme.drawSlot(g, selected, selectedSize, true, true);
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(18)), 0, 0,
                        "CURRENTLY PLAYING");
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(39)), 0, 0,
                        "%s", shortText(current.characterName(), 18));
                FastText.aprintfstroked(g, selected.add(UI.scale(12), UI.scale(60)), 0, 0,
                        "%s + %s", shortText(current.accountLabel(), 13), current.state().provenance);
            }
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
            Coord center = aperture.add(apertureSize.div(2));
            int bloomRadius = Math.max(UI.scale(18), Math.min(apertureSize.x, apertureSize.y) / 9);
            MoonFlowerHudTheme.drawBlossom(g, center.add(0, -UI.scale(32)), bloomRadius);
            MoonFlowerHudTheme.drawCurvedVine(g, aperture.add(UI.scale(18), apertureSize.y - UI.scale(28)),
                    center.add(-bloomRadius, -UI.scale(20)), 1.0);
            MoonFlowerHudTheme.drawCurvedVine(g, aperture.add(apertureSize.x - UI.scale(18), apertureSize.y - UI.scale(28)),
                    center.add(bloomRadius, -UI.scale(20)), 1.0);
            g.aimage(live, center.add(0, UI.scale(20)), 0.5, 0.5);
            if(card != null) {
                FastText.aprintfstroked(g, center.add(0, UI.scale(48)), 0.5, 0,
                        "%s", safe(card.detail()));
            }
            FastText.aprintfstroked(g, center.add(0, UI.scale(78)), 0.5, 0,
                    "%s", safe(view.status()));
            FastText.aprintfstroked(g, center.add(0, UI.scale(102)), 0.5, 0,
                    "WORKER %s - NO EXTRA WINDOW", view.workerReadiness().state().label);
            FastText.aprintfstroked(g, center.add(0, UI.scale(122)), 0.5, 0,
                    "OFFSCREEN RENDERER %s",
                    view.workerReadiness().offscreenRenderers().isEmpty() ? "MISSING" : "FOUND");
            FastText.aprintfstroked(g, center.add(0, UI.scale(142)), 0.5, 0,
                    "ONE-SHOT CREDENTIALS %s",
                    view.workerReadiness().credentialBrokerReady() ? "READY" : "LOCKED");
            FastText.aprintfstroked(g, center.add(0, UI.scale(162)), 0.5, 0,
                    "PREVIEW TELEMETRY %s",
                    view.workerReadiness().telemetryBridgeReady() ? "READY" : "LOCKED");
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
            int width = Math.min(UI.scale(300), stageSize.x - UI.scale(54));
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

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b != 1)
                return(super.mousedown(event));
            SessionConservatorySnapshot view = service.snapshot();
            int railWidth = Math.min(UI.scale(220), Math.max(UI.scale(150), sz.x / 3));
            Coord rail = UI.scale(14, 48);
            Coord railSize = Coord.of(railWidth, sz.y - UI.scale(66));
            int inset = UI.scale(10);
            Coord fresh = rail.add(inset, UI.scale(12));
            Coord freshSize = Coord.of(railSize.x - inset * 2, UI.scale(58));
            if(event.c.isect(fresh, freshSize)) {
                service.requestFreshLogin();
                return(true);
            }
            int rowHeight = UI.scale(38);
            int rowGap = UI.scale(6);
            int rowY = fresh.y + freshSize.y + UI.scale(12);
            int availableRows = Math.max(0, (rail.y + railSize.y - rowY - UI.scale(100)) /
                    (rowHeight + rowGap));
            int shown = Math.min(availableRows, view.launchOptions().size());
            for(int index = 0; index < shown; index++) {
                Coord row = Coord.of(fresh.x, rowY + index * (rowHeight + rowGap));
                if(event.c.isect(row, Coord.of(freshSize.x, rowHeight))) {
                    service.requestKnownAccount(view.launchOptions().get(index).accountLabel());
                    return(true);
                }
            }
            return(super.mousedown(event));
        }

        @Override
        public void destroy() {
            loginPassword.settext("");
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
