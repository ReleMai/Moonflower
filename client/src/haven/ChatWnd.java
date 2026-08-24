package haven;

public class ChatWnd extends Window {
    GameUI gui;
    private Widget settingsButton;

    public ChatWnd(Coord sz, String cap, GameUI gui) {
        super(sz, cap);
        this.gui = gui;
    }

    protected Deco makedeco() {
        return(new DefaultDeco(true){

            @Override
            protected void drawframe(GOut g) {
                super.drawframe(g);
                if(MoonFlowerHudTheme.active()) {
                    Coord grip = ca.br.sub(UI.scale(5), UI.scale(5));
                    g.chcolor(MoonFlowerHudTheme.TEAL_BRIGHT);
                    g.line(grip.add(-UI.scale(8), 0), grip, UI.scale(1));
                    g.line(grip.add(-UI.scale(5), -UI.scale(3)), grip, UI.scale(1));
                    g.line(grip.add(-UI.scale(2), -UI.scale(6)), grip, UI.scale(1));
                    g.chcolor();
                }
            }

            @Override
            public void mousemove(MouseMoveEvent ev) {
                if (szdrag != null) {
                    gui.chat.resize(sz.x - UI.scale(36), sz.y - UI.scale(44));
                }
                super.mousemove(ev);
            }

            @Override
            public boolean mouseup(MouseUpEvent ev) {
                preventResizingOutside();
                preventDraggingOutside();
                if (szdrag != null) {
                    gui.chat.resize(sz.x - UI.scale(36), sz.y - UI.scale(44));
                }
                return super.mouseup(ev);
            }

            @Override
            public boolean checkhit(Coord c) {
                Coord cpc = c.sub(cptl);
                Coord cpsz2 = new Coord(cpsz.x + (UI.scale(14)), cpsz.y); // ND: Fix top-right corner drag not working. It's just some stupid bug involving ALL OF THIS SPAGHETTI CODE.
                return(ca.contains(c) || (c.isect(cptl, cpsz2) && (cm.back.getRaster().getSample(cpc.x % cm.back.getWidth(), cpc.y, 3) >= 128)));
            }

        }.dragsize(true));
    }

    @Override
    protected void added() { // ND: Resize the chat widget to match the chat window, after the window is added to the GUI
        super.added();
        if (deco instanceof DefaultDeco) {
            DefaultDeco decoration = (DefaultDeco)deco;
            decoration.cbtn.hide();
            settingsButton = decoration.add(new ChatSettingsButton(), Coord.z);
            layoutSettingsButton();
        }
        gui.chat.resize(sz.x - UI.scale(36), sz.y - UI.scale(44));
    }

    @Override
    public void resize(Coord sz) {
        sz.x = Math.max(sz.x, UI.scale(280));
        sz.y = Math.max(sz.y, UI.scale(58));
        super.resize(sz);
        layoutSettingsButton();
        Utils.setprefc("wndsz-chat", sz);
    }

    private void layoutSettingsButton() {
        if(settingsButton != null && deco != null)
            settingsButton.move(Coord.of(Math.max(0, deco.sz.x - settingsButton.sz.x - UI.scale(9)), -UI.scale(8)));
    }

    private class ChatSettingsButton extends Widget {
        private boolean hover;

        ChatSettingsButton() {
            super(Coord.of(UI.scale(24), UI.scale(24)));
            settip("Chat settings");
        }

        @Override
        public void draw(GOut g) {
            if(MoonFlowerHudTheme.active())
                MoonFlowerHudTheme.drawCircularSlot(g, sz.div(2), UI.scale(11), hover);
            g.aimage(MoonFlowerUiAssets.chatSettings, sz.div(2), 0.5, 0.5,
                    Coord.of(UI.scale(19), UI.scale(19)));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && ev.c.isect(Coord.z, sz)) {
                gui.openChatSettings();
                return true;
            }
            return false;
        }

        @Override
        public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
            hover = hovering;
            return true;
        }
    }

}
