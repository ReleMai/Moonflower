/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;

public class LoginScreen extends Widget {
    public static final Config.Variable<String> authmech = Config.Variable.prop("haven.authmech", "native");
    public static final Text.Foundry
	textf = new Text.Foundry(Text.sans, 18).aa(true),
	textfs = new Text.Foundry(Text.sans, 15).aa(true);
    public static final Tex bg = Resource.loadtex("gfx/loginscr");
	public static final Position bgc = new Position(UI.scale(780, 185));
    public final Widget login;
    public final String confname;
    public Widget loginSteam = null;
	private Text error, progress;
	private Button optbtn;
	private OptWnd opts;
	AccountList accounts;
	private String lastUser = "";
	private String lastPass = "";
	public static HSlider loginScreenMusicVolumeSlider;
	public Img backgroundImg;
	static public Audio.CS mainThemeClip = null;
	static public boolean mainThemeStopped = false;
	static public Audio.CS charSelectThemeClip = null;
	static public boolean charSelectThemeStopped = false;
	private Window firstTimeUseWindow = null;
	private Window firstTimeUseExtraBackgroundWindow = null; // ND: Do an extra window to have a solid background, no transparency.
	private boolean firstTimeWindowCreated = false;
    private Coord rightPanelPos = MoonFlowerScreenTheme.LOGIN_RIGHT_POS;

    private String getpref(String name, String def) {
	return(Utils.getpref(name + "@" + confname, def));
    }

    public LoginScreen(String confname) {
	super(bg.sz());
	Tex loginBackground = bg(MoonFlowerScreenTheme.nextLoginBackground());
	this.confname = confname;
	setfocustab(true);
	add(backgroundImg = new Img(loginBackground), Coord.z);
	MoonFlowerScreenTheme.Panel left = add(new MoonFlowerScreenTheme.Panel(MoonFlowerScreenTheme.LOGIN_LEFT_SZ),
		MoonFlowerScreenTheme.LOGIN_LEFT_POS);
	MoonFlowerScreenTheme.Panel right = add(new MoonFlowerScreenTheme.Panel(MoonFlowerScreenTheme.LOGIN_RIGHT_SZ),
		MoonFlowerScreenTheme.LOGIN_RIGHT_POS);
	rightPanelPos = right.c;
	left.lower();
	right.lower();
	backgroundImg.lower();
	adda(MoonFlowerScreenTheme.title("MoonFlower", 46), sz.x / 2, UI.scale(28), 0.5, 0.0);
	adda(MoonFlowerScreenTheme.subtitle("A quieter path into the Hearthlands"), sz.x / 2, UI.scale(84), 0.5, 0.0);
	add(MoonFlowerScreenTheme.title("Saved Hearthlings", 22), left.c.add(UI.scale(16, 14)));
	add(MoonFlowerScreenTheme.subtitle("Stored only on this Windows account"), left.c.add(UI.scale(16, 44)));
	add(MoonFlowerScreenTheme.title("Enter the Hearthlands", 22), right.c.add(UI.scale(16, 14)));
	add(new CircleFadein(0.5));
	optbtn = adda(new Button(UI.scale(110), "Options"), pos("cbl").add(UI.scale(20, -20)), 0, 1);
	optbtn.setgkey(GameUI.kb_opt);
//	if(HttpStatus.mond.get() != null)
//	    adda(new StatusLabel(HttpStatus.mond.get(), 1.0), sz.x - UI.scale(10), UI.scale(10), 1.0, 0.0);
//	switch(authmech.get()) {
//	case "native":
//	    login = new Credbox();
//	    break;
//	case "steam":
//	    login = new Steambox();
//	    break;
//	default:
//	    throw(new RuntimeException("Unknown authmech: " + authmech.get()));
//	}
	login = new Credbox();
	add(login, right.c.add(UI.scale(50, 72))).hide();
	loginSteam = new Steambox();
	add(loginSteam, login.c.add(UI.scale(0, login.sz.y + UI.scale(12)))).hide();
	accounts = add(new AccountList(6), left.c.add(UI.scale(16, 78)));
	try {
		adda(new StatusLabel(new URI("http", confname, "/mt/srv-mon", null), 0.5), sz.x / 2, sz.y - UI.scale(16), 0.5, 1.0);
	} catch(URISyntaxException e) {
		throw(new RuntimeException(e));
	}
	mainThemeStopped = false;
	add(loginScreenMusicVolumeSlider = new HSlider(UI.scale(220), 0, 100, Utils.getprefi("loginScreenMusicVolume", 40)) {
		protected void attach(UI ui) {
			super.attach(ui);
		}
		public void changed() {
            if (LoginScreen.mainThemeClip != null) ((Audio.VolAdjust) LoginScreen.mainThemeClip).vol = val/100d;
            Utils.setprefi("loginScreenMusicVolume", val);
		}
	}, sz.x - UI.scale(240), sz.y - UI.scale(24));
	add(MoonFlowerScreenTheme.subtitle("Music"), sz.x - UI.scale(285), sz.y - UI.scale(27));
	GameUI.swimmingToggled = false;
	GameUI.trackingToggled = false;
	GameUI.crimesToggled = false;
	MenuGrid.loginTogglesNeedUpdate = true;
	Gob.batWingCapeEquipped = false;
	Gob.nightQueenDefeated = false;
    Gob.caveHermitAcquired = false;
	Gob.alarmPlayed.clear();
	GameUI.verifiedAccount = false;
	GameUI.subscribedAccount = false;
    Config.setPlayerName(null);
    GameUI.gameTimeSpeedMultiplier = 3.29f;
    }

//    public static final KeyBinding kb_savtoken = KeyBinding.get("login/savtoken", KeyMatch.forchar('R', KeyMatch.M)); // ND: Why the fuck are there keybinds for these? Someone might press one of those by mistake
//    public static final KeyBinding kb_deltoken = KeyBinding.get("login/deltoken", KeyMatch.forchar('F', KeyMatch.M)); // ND: No drink button keybind, BUT OH BOY WE COULD REALLY USE A REMEMBER/FORGET ACCOUNT KEYBIND!
	public class Credbox extends Widget {
	public final UserEntry user;
	private final TextEntry pass;
	private final CheckBox saveaccount;
//	private final CheckBox savetoken;
	private final Button fbtn;
	private final Button exec;
	private final Widget pwbox, tkbox;
	private byte[] token = null;
	private boolean inited = false;

	public class UserEntry extends TextEntry {
	    private final List<String> history = new ArrayList<>();
	    private int hpos = -1;
	    private String hcurrent;

	    private UserEntry(int w) {
		super(w, "");
//		history.addAll(Utils.getprefsl("saved-tokens@" + confname, new String[] {}));
	    }

	    protected void changed() {
//		checktoken();
//		savetoken.set(token != null); // ND: Don't need the "remember me" to untick whenever we write inside the username input
	    }

	    public void settext2(String text) {
		rsettext(text);
		changed();
	    }

	    public boolean keydown(KeyDownEvent ev) {
		if(ConsoleHost.kb_histprev.key().match(ev)) {
		    if(hpos < history.size() - 1) {
			if(hpos < 0)
			    hcurrent = text();
			settext2(history.get(++hpos));
		    }
		} else if(ConsoleHost.kb_histnext.key().match(ev)) {
		    if(hpos >= 0) {
			if(--hpos < 0)
			    settext2(hcurrent);
			else
			    settext2(history.get(hpos));
		    }
		} else {
		    return(super.keydown(ev));
		}
		return(true);
	    }

	    public void init(String name) {
		history.remove(name);
		settext2(name);
	    }
	}

	private Credbox() {
	    super(UI.scale(280, 210));
	    setfocustab(true);
		Widget prev = add(new Label("Username", textf){{setstroked(Color.BLACK);}}, 0, 0);
	    add(user = new UserEntry(this.sz.x), prev.pos("bl").adds(0, 1));
	    setfocus(user);

	    add(pwbox = new Widget(Coord.z), user.pos("bl").adds(0, 10));
		pwbox.add(prev = new Label("Password", textf){{setstroked(Color.BLACK);}}, Coord.z);
	    pwbox.add(pass = new TextEntry(this.sz.x, ""), prev.pos("bl").adds(0, 1)).pw = true;
		pwbox.add(saveaccount = new CheckBox("Save on this PC", true), pass.pos("bl").adds(0, 10));
		saveaccount.set(true);
		saveaccount.settip("Saved accounts stay in your Windows user preferences and are never included in MoonFlower updates.", true);
//	    pwbox.add(savetoken = new CheckBox("Remember me", true), pass.pos("bl").adds(0, 10));
//	    savetoken.setgkey(kb_savtoken); // ND: Stupid keybind
//	    savetoken.settip("Saving your login does not save your password, but rather " +
//			     "a randomly generated token that will be used to log in. " +
//			     "You can manage your saved tokens in your Account Settings.",
//			     true);
	    pwbox.pack();
//	    pwbox.hide();

	    add(tkbox = new Widget(new Coord(this.sz.x, 0)), user.pos("bl").adds(0, 10));
		tkbox.add(prev = new Label("Login saved", textfs){{setstroked(Color.BLACK);}}, UI.scale(0, 25));
		tkbox.adda(fbtn = new Button(UI.scale(100), "Forget me"), prev.pos("mid").x(this.sz.x), 1.0, 0.5).action(() -> {
//			forget();
			if(accounts.getAccountFromName(user.text()) != null)
				accounts.remove(accounts.getAccountFromName(user.text()));
			user.rsettext("");
		});
//	    fbtn.setgkey(kb_deltoken); // ND: Stupid keybind
	    tkbox.pack();
	    tkbox.hide();

	    adda(exec = new Button(this.sz.x, "Enter with this account").action(this::enter),
		pos("cmid").y(Math.max(pwbox.pos("bl").y, tkbox.pos("bl").y)).adds(0, 18), 0.5, 0.0);
	    pack();
	}

	private void init() {
	    if(inited)
		return;
	    inited = true;
//		user.init(getpref("loginname", "")); // ND: This line sets the user text if the "remember me" is checked. I don't want that, since we have the accounts on the left side.
//		This way, if a new account needs to be added, you don't need to clear the box.
	}

//	private void checktoken() {
//	    if(this.token != null) {
//		Arrays.fill(this.token, (byte)0);
//		this.token = null;
//	    }
//	    byte[] token = Bootstrap.gettoken(user.text(), confname);
//	    if(token == null) {
//		tkbox.hide();
//		pwbox.show();
//	    } else {
//		tkbox.show();
//		pwbox.hide();
//		this.token = token;
//	    }
//	}
//
//	private void forget() {
//	    String nm = user.text();
//	    Bootstrap.settoken(nm, confname, null);
//	    savetoken.set(false);
//	    checktoken();
//	}

    private void enter() {
        if(user.text().equals("")) {
            setfocus(user);
        } else if(pwbox.visible && pass.text().equals("")) {
            setfocus(pass);
        } else {
			if(saveaccount.state()) {
				lastUser = user.text();
				lastPass = pass.text();
			}
			LoginScreen.this.wdgmsg("login", creds(), pwbox.visible && saveaccount.state());
        }
    }

	private void enter2() {
		if(user.text().equals("")) {
			setfocus(user);
		} else if(pwbox.visible && pass.text().equals("")) {
			setfocus(pass);
		} else {
			LoginScreen.this.wdgmsg("login", creds(), pwbox.visible && saveaccount.state());
		}
	}

	private AuthClient.Credentials creds() {
//	    byte[] token = this.token;
	    AuthClient.Credentials ret;
//	    if(token != null) {
//		ret = new AuthClient.TokenCred(user.text(), Arrays.copyOf(token, token.length));
//	    } else {
		String pw = pass.text();
		ret = null;
		parse: if(pw.length() == 64) {
		    byte[] ptok;
		    try {
			ptok = Utils.hex.dec(pw);
		    } catch(IllegalArgumentException e) {
			break parse;
		    }
		    ret = new AuthClient.TokenCred(user.text(), ptok);
		}
		if(ret == null)
		    ret = new AuthClient.NativeCred(user.text(), pw);
		pass.rsettext("");
//	    }
	    return(ret);
	}

	public boolean keydown(KeyDownEvent ev) {
	    if(key_act.match(ev)) {
		enter();
		return(true);
	    }
	    return(super.keydown(ev));
	}

	public void show() {
	    if(!inited)
		init();
	    super.show();
//	    checktoken();
	    if(pwbox.visible && !user.text().equals(""))
		setfocus(pass);
	}
    }

    private static boolean steam_autologin = false;
    public class Steambox extends Widget {

	private Steambox() {
	    super(UI.scale(280, 64));
	    Widget prev = adda(MoonFlowerScreenTheme.subtitle("Or continue with Steam"), sz.x / 2, 0, 0.5, 0);
	    adda(new Button(this.sz.x, "Continue with Steam").action(this::enter),
		prev.pos("bl").adds(0, 8).x(sz.x / 2), 0.5, 0.0)
		.setgkey(key_act);
	}

	private AuthClient.Credentials creds() throws java.io.IOException {
	    return(new SteamCreds());
	}

	private void enter() {
	    try {
		LoginScreen.this.wdgmsg("login", creds(), false);
	    } catch(java.io.IOException e) {
		error(e.getMessage());
	    }
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(steam_autologin) {
		enter();
		steam_autologin = false;
	    }
	}
    }

    public static class StatusLabel extends Widget {
	public final HttpStatus stat;
	public final double ax;

	public StatusLabel(URI svc, double ax) {
	    super(new Coord(UI.scale(150), FastText.h * 2));
	    this.stat = new HttpStatus(svc);
	    this.ax = ax;
	}

	public void draw(GOut g) {
	    int x = (int)Math.round(sz.x * ax);
	    synchronized(stat) {
		if(!stat.syn || (stat.status == ""))
		    return;
		if(stat.status == "up") {
			FastText.aprintfstroked(g, new Coord(x, FastText.h * 0), ax, 0, "Server status: Online");
			try {
				FastText.aprintfstroked(g, new Coord(x, FastText.h * 1), ax, 0, "Hearthlings connected: %,d", stat.users);
			} catch (ArrayIndexOutOfBoundsException e) {

			}
		} else if(stat.status == "down") {
		    FastText.aprintfstroked(g, new Coord(x, FastText.h * 0), ax, 0, "Server status: Offline");
        } else if(stat.status == "terminating") {
            FastText.aprintfstroked(g, new Coord(x, FastText.h * 0), ax, 0, "Server status: Shutting down");
        } else if(stat.status == "shutdown") {
		    FastText.aprintfstroked(g, new Coord(x, FastText.h * 0), ax, 0, "Server status: Down");
		} else if(stat.status == "crashed") {
		    FastText.aprintfstroked(g, new Coord(x, FastText.h * 0), ax, 0, "Server status: Crashed");
		}
	    }
	}

	protected void added() {
	    stat.start();
	}

	public void dispose() {
	    stat.quit();
	}
    }

    private void mklogin() {
	login.show();
	if (Steam.get() != null)
		loginSteam.show();
	progress(null);
    }

    private void error(String error) {
	if(this.error != null)
	    this.error = null;
	if(error != null)
	    this.error = textf.render(error, java.awt.Color.RED);
    }

    private void progress(String p) {
	if(progress != null)
	    progress = null;
	if(p != null)
	    progress = textf.render(p, java.awt.Color.WHITE);
    }

    private void clear() {
	login.hide();
	if (Steam.get() != null)
		loginSteam.hide();
	progress(null);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == accounts) {
		if("account".equals(msg)) {
			String name = (String)args[0];
			String pass = (String)args[1];
			((Credbox)login).user.settext2(name);
			((Credbox)login).pass.settext(pass);
			((Credbox)login).enter2();
		}
		return;
	}
	if(sender == optbtn) {
		if (!opts.attached)
			ui.root.adda(opts, 0.5, 0.5);
		else
			opts.show(!opts.visible());
		return;
	} else if(sender == opts) { // ND: Pretty sure this part never happens, ever
		opts.show(!opts.visible());
	}
	super.wdgmsg(sender, msg, args);
    }

	public void tick(double dt) {
		playMainTheme();
		if (!firstTimeWindowCreated && Utils.getprefb("firstTimeOpeningClient", true)){
			createFirstTimeUseWindow();
		}
		super.tick(dt);
	}


    public void cdestroy(Widget ch) {
	if(ch == opts) {
	    opts = null;
	}
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "login") {
	    mklogin();
	} else if(msg == "error") {
	    error((String)args[0]);
		lastUser = "";
		lastPass = "";
	} else if(msg == "prg") {
	    error(null);
	    clear();
	    progress((String)args[0]);
		if(((String)args[0]).equals("Connecting...") &&
		   ((Credbox)login).saveaccount.state() &&
		   !lastUser.equals("") && !lastPass.equals("")) {
			AccountList.storeAccount(lastUser, lastPass);
			lastUser = "";
			lastPass = "";
		}
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void presize() {
	c = parent.sz.div(2).sub(sz.div(2));
    }

    protected void added() {
	presize();
	parent.setfocus(this);
    opts = new OptWnd(false); // ND: This needs to be created when the login screen is created, to prevent options nullpointers once we log into a character
	playMainTheme();
    if (ui != null) {
		GameUI.stopAllThemes(ui);
		ui.root.adda(opts, 0.5, 0.5);
		opts.hide();
	}
    }

	public void dispose() {
		stopMainTheme();
	}

    public void draw(GOut g) {
	super.draw(g);
	Coord msgc = rightPanelPos.add(MoonFlowerScreenTheme.LOGIN_RIGHT_SZ.x / 2, UI.scale(56));
	if(error != null)
		g.aimage(PUtils.strokeTex(error), msgc, 0.5, 0.0);
	if(progress != null)
		g.aimage(PUtils.strokeTex(progress), msgc.add(0, UI.scale(22)), 0.5, 0.0);
    }

	private void playMainTheme() {
		if (!mainThemeStopped &&(mainThemeClip == null || !ui.globalSfxIsPlaying(mainThemeClip))) {
				Audio.CS klippi = MoonFlowerAudio.loop(MoonFlowerScreenTheme.LOGIN_MUSIC);
				mainThemeClip = new Audio.VolAdjust(klippi, Utils.getprefi("loginScreenMusicVolume", 40)/100d);
                ui.globalSfxPlay(mainThemeClip);
		}
	}

	private void stopMainTheme() {
		if(mainThemeClip != null){
            ui.globalSfxStop(mainThemeClip);
			mainThemeStopped = true;
		}
	}
	private void createFirstTimeUseWindow(){
		firstTimeUseWindow = new Window(Coord.z, "Hey!", true) {
			{
				Widget prev;
				prev = add(new Label("This is your first time launching MoonFlower!"), UI.scale(new Coord(34, 3)));
				prev = add(new Label("Please make sure to set up your Keybindings and Settings!"), prev.pos("bl").adds(0, 8).xs(0));
				prev = add(new Label("The default settings are a safe place to start."), prev.pos("bl").adds(0, 8).xs(34));
				Button close = new Button(UI.scale(120), "Okay!", false) {
					@Override
					public void click() {
						parent.reqdestroy();
						firstTimeUseExtraBackgroundWindow.reqdestroy();
						Utils.setprefb("firstTimeOpeningClient", false);
					}
				};
				add(close, prev.pos("bl").adds(0, 10).adds(0, 6).xs(76));
				pack();
			}

			@Override
			public void drag(Coord off) {
				// ND: Don't do anything, so it can't be dragged
			}
			@Override
			public void wdgmsg(Widget sender, String msg, Object... args) {
				if (msg.equals("close")) {
					firstTimeUseExtraBackgroundWindow.reqdestroy();
					reqdestroy();
					Utils.setprefb("firstTimeOpeningClient", false);
				}
				else
					super.wdgmsg(sender, msg, args);
			}
		};
		firstTimeUseExtraBackgroundWindow = new Window(Coord.z, " ", true);
		firstTimeUseExtraBackgroundWindow.resize(firstTimeUseWindow.csz());
		adda(firstTimeUseExtraBackgroundWindow, 0.5, 0.5);
		adda(firstTimeUseWindow, 0.5, 0.5);
		firstTimeWindowCreated = true;
	}

	static Tex bg(String imgPath){
		try {
			BufferedImage originalImage = ImageIO.read(new File(imgPath));
			int targetWidth = bg.sz().x;
			int targetHeight = bg.sz().y;
			int sourceWidth = originalImage.getWidth();
			int sourceHeight = originalImage.getHeight();
			double targetAspect = targetWidth / (double)targetHeight;
			double sourceAspect = sourceWidth / (double)sourceHeight;
			int cropWidth = sourceWidth;
			int cropHeight = sourceHeight;
			int cropX = 0;
			int cropY = 0;
			if(sourceAspect > targetAspect) {
				cropWidth = (int)Math.round(sourceHeight * targetAspect);
				cropX = (sourceWidth - cropWidth) / 2;
			} else if(sourceAspect < targetAspect) {
				cropHeight = (int)Math.round(sourceWidth / targetAspect);
				cropY = (sourceHeight - cropHeight) / 2;
			}
			BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = resizedImage.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
			g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight,
					cropX, cropY, cropX + cropWidth, cropY + cropHeight, null);
			g2d.dispose();
			originalImage.flush();
			return new TexI(resizedImage);
		} catch (IOException ignored) {
			ignored.printStackTrace();
			return bg;
		}
	}

}
