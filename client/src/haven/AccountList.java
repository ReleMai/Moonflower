package haven;

import java.util.*;


public class AccountList extends Widget {
    public static final LinkedHashMap<String, String> accountmap = new LinkedHashMap<>();
    private static final Coord ROW = UI.scale(268, 40);
    private static final int ROW_GAP = UI.scale(6);
    private static Scrollbar sb;

    public int height, y;
    public final List<Account> accounts = new ArrayList<>();

    static void loadAccounts() {
        accountmap.clear();
        String[] savedAccounts = Utils.getprefsa("savedAccounts", null);
        try {
            if (savedAccounts != null) {
                for (String s : savedAccounts) {
                    String[] split = s.split("\\(ಠ‿ಠ\\)");
                    if (!accountmap.containsKey(split[0])) {
                        accountmap.put(split[0], split[1]);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void storeAccount(String name, String pass) {
        synchronized(accountmap) {
            accountmap.put(name, pass);
        }
        saveAccounts();
    }

    public static void removeAccount(String name) {
        synchronized(accountmap) {
            accountmap.remove(name);
        }
        saveAccounts();
    }

    public static void saveAccounts() {
        synchronized(accountmap) {
            try {
                String[] savedAccounts = new String[accountmap.size()];
                int i = 0;
                for(Map.Entry<String, String> e : accountmap.entrySet()) {
                    savedAccounts[i] = e.getKey() + "(ಠ‿ಠ)" + e.getValue();
                    i++;
                }
                Utils.setprefsa("savedAccounts", savedAccounts);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class Account {
        public String name, token;
        Button plb, del, up, down;

        public Account(String name, String token) {
            this.name = name;
            this.token = token;
        }
    }

    public AccountList(int height) {
        super();
        loadAccounts();
        this.height = height;
        this.sz = new Coord(ROW.x + UI.scale(18), (ROW.y + ROW_GAP) * height);
        y = 0;
        sb = new Scrollbar((ROW.y + ROW_GAP) * height - ROW_GAP, 0, 100){
            @Override
            public void changed() {
                scrolled(val);
                super.changed();
            }
        };
        add(sb, new Coord(ROW.x + UI.scale(4), 0));

        for (Map.Entry<String, String> entry : accountmap.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
        sb.max = Math.max(0, accounts.size() - height);
        sb.show(accounts.size() > height);
    }

    public void scroll(int amount) {
        y += amount;
        synchronized(accounts) {
            if(y > accounts.size() - height)
                y = accounts.size() - height;
        }
        if(y < 0)
            y = 0;
    }

    public void scrolled(int val) {
        y = val;
        synchronized(accounts) {
            if(y > accounts.size() - height)
                y = accounts.size() - height;
        }
        if(y < 0)
            y = 0;
    }

    public void draw(GOut g) {
        Coord cc = Coord.z;
        synchronized (accounts) {
            for (Account account : accounts) {
                account.plb.hide();
                account.del.hide();
                account.up.hide();
                account.down.hide();
            }
            if(accounts.isEmpty()) {
                g.chcolor(MoonFlowerScreenTheme.MUTED);
                FastText.aprintfstroked(g, new Coord(sz.x / 2, UI.scale(24)), 0.5, 0,
                        "No saved accounts on this PC");
                g.chcolor();
            }
            for (int i = 0; (i < height) && (i + this.y < accounts.size()); i++) {
                Account account = accounts.get(i + this.y);
                g.chcolor(MoonFlowerScreenTheme.PANEL_SOFT);
                g.frect(cc, ROW);
                g.chcolor(MoonFlowerScreenTheme.BORDER);
                g.rect(cc, ROW);
                g.chcolor();
                account.plb.show();
                account.plb.c = cc.add(UI.scale(6), (ROW.y - account.plb.sz.y) / 2);
                account.del.show();
                account.del.c = cc.add(ROW.x - UI.scale(78), (ROW.y - account.del.sz.y) / 2);
                account.up.show();
                account.up.c = cc.add(ROW.x - UI.scale(52), (ROW.y - account.up.sz.y) / 2);
                account.down.show();
                account.down.c = cc.add(ROW.x - UI.scale(28), (ROW.y - account.down.sz.y) / 2);
                cc = cc.add(0, ROW.y + ROW_GAP);
            }
        }
        super.draw(g);
    }

    public boolean mousewheel(MouseWheelEvent ev) {
        sb.ch(ev.a);
        return (true);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(sender instanceof Button) {
            synchronized(accounts) {
                for(Account account : accounts) {
                    if(sender == account.plb) {
                        super.wdgmsg("account", account.name, account.token);
                        break;
                    } else if(sender == account.del) {
                        remove(account);
                        break;
                    } else if (sender == account.up) {
                        if (accounts.indexOf(account) > 0) {
                            swapAccountsPosition(accounts.indexOf(account), accounts.indexOf(account) - 1);
                        }
                        break;
                    } else if (sender == account.down) {
                        if (accounts.indexOf(account) < accounts.size() - 1) {
                            swapAccountsPosition(accounts.indexOf(account), accounts.indexOf(account) + 1);
                        }
                        break;
                    }
                }
            }
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public void add(String name, String token) {
        Account c = new Account(name, token);
        c.plb = add(new Button(UI.scale(176), name, false) {
        });
        c.plb.hide();
        c.del = add(new Button(UI.scale(22), "X") {
        });
        c.del.hide();
        c.up = add(new Button(UI.scale(22), "↑") {
        });
        c.up.hide();
        c.down = add(new Button(UI.scale(22), "↓") {
        });
        c.down.hide();
        synchronized (accounts) {
            accounts.add(c);
            sb.max = Math.max(0, accounts.size() - height);
            sb.show(accounts.size() > height);
        }
    }

    public void remove(Account account) {
        synchronized(accounts) {
            accounts.remove(account);
            sb.max = Math.max(0, accounts.size() - height);
            if (sb.val > sb.max) sb.val = Math.max(0, sb.max);
            sb.show(accounts.size() > height);
        }
        scroll(0);
        removeAccount(account.name);
        ui.destroy(account.plb);
        ui.destroy(account.del);
        ui.destroy(account.up);
        ui.destroy(account.down);
    }

    public void swapAccountsPosition(int oldIndex, int newIndex){
        Collections.swap(accounts, oldIndex, newIndex);
        accountmap.clear();
        for(Account account : accounts) {
            accountmap.put(account.name, account.token);
        }
        saveAccounts();
    }

    public Account getAccountFromName(String name){
        synchronized (accounts) {
            for (Account account : accounts) {
                if (name.equals(account.name)){
                    return account;
                }
            }
        }
        return null;
    }
}
