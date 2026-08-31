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

import haven.combat.AnimalHealthCatalog;
import haven.combat.AnimalHealthEstimate;
import haven.combat.AnimalHealthEstimator;
import haven.combat.CombatDamageSnapshot;
import haven.combat.CombatDamageTracker;
import haven.render.*;
import haven.sprites.CombatRangeSprite;
import haven.sprites.CurrentAggroSprite;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.awt.event.KeyEvent;
import java.util.List;

public class Fightsess extends Widget {
    private static final Coord off = new Coord(UI.scale(32), UI.scale(32));
    public static final Tex cdframe = Resource.loadtex("gfx/hud/combat/cool");
    public static final Tex actframe = Buff.frame;
    public static final Coord actframeo = Buff.imgoff;
    public static final Tex indframe = Resource.loadtex("gfx/hud/combat/indframe");
    public static final Coord indframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex indbframe = Resource.loadtex("gfx/hud/combat/indbframe");
    public static final Coord indbframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex useframe = Resource.loadtex("gfx/hud/combat/lastframe");
    public static final Coord useframeo = (useframe.sz().sub(off)).div(2);
    public static final int actpitch = UI.scale(45);
	public static final int actpitch2 = UI.scale(62);
    public final Action[] actions;
    public int use = -1, useb = -1;
    public Coord pcc;
    public int pho;
    public Fightview fv;
	public static final Text.Foundry ipFoundry = new Text.Foundry(Text.serif.deriveFont(Font.BOLD), 22);
	public static final Text.Foundry ipAdditionalFont = new Text.Foundry(Text.dfont.deriveFont(Font.BOLD), 12);
	public static final Text.Foundry openingAdditionalFont = new Text.Foundry(Text.dfont.deriveFont(Font.BOLD), 10);
	private static final Text.Foundry integratedOpeningFont = new Text.Foundry(Text.dfont.deriveFont(Font.BOLD), 14);
	private static final Text.Foundry integratedTimerFont = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 16);
	public static final Text.Foundry cleaveAdditionalFont = new Text.Foundry(Text.dfont.deriveFont(Font.BOLD), 10);
	int combatMedColorShift = 0;
	public static final Text.Foundry keybindsFoundry = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 14);
	public static final Text.Foundry damageFoundry = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 11);

	private static final Map<String, Tex> keybindTexCache = new HashMap<>();
	private static final Map<String, Tex> damagePredictionTexCache = new HashMap<>();
	private static final Tex[] openingValueTexCache = new Tex[101];
	private static final Tex[] integratedOpeningValueTexCache = new Tex[101];
	private static final Map<Integer, Tex> ipTexCache = new HashMap<>();
	private static final Map<Integer, Tex> oipTexCache = new HashMap<>();
	private final Map<String, Tex> circularIconCache = new HashMap<>();
	private static final LinkedHashMap<String, Tex> timerTexCache = new LinkedHashMap<String, Tex>(100, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
			return size() > 50;
		}
	};
	private static final LinkedHashMap<String, Tex> agilityTexCache = new LinkedHashMap<String, Tex>(50, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
			return size() > 30;
		}
	};
	private static Tex estAgiLabelTex = null;
	private static Tex unknownTextTex = null;
	private static final Tex combatDeckTitle = Text.renderstroked("COMBAT DECK", MoonFlowerHudTheme.IVORY,
			Color.BLACK, keybindsFoundry).tex();

	public static final Color stamBarBlue = new Color(47, 58, 207, 200);
	public static final Color hpBarGreen = new Color(0, 166, 10, 255);
	public static final Color hpBarGray = new Color(113, 113, 113, 255);
	public static final Color hpBarRed = new Color(168, 0, 0, 255);
	public static final Color hpBarYellow = new Color(182, 165, 0, 255);
	private static final Color barFrame = new Color(255, 255, 255, 111);

	public static boolean loadoutChecked = false;
	private static int[] openingArr = new int[] {0,0,0,0};
	private static int wepdmg = 0;
	private static double ql = 1;
	private static int basedmg = 0;
	public static double myStrength = 1;

	private boolean combatMedAlphaShiftUp = true;

	private Tex myHealthBarTex = null;
	private String myLastHealthBarText = "";
	private Tex myStaminaBarTex = null;
	private String myLastStaminaBarText = "";

	private static Coord actc(int i) {
		return MoonFlowerCombatLayout.actionCoord(i);
	}

    public static class Action {
	public final Indir<Resource> res;
	public double cs, ct;

	public Action(Indir<Resource> res) {
	    this.res = res;
	}
    }

    @RName("fsess")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
		loadoutChecked = false;
	    int nact = Utils.iv(args[0]);
		if(OptWnd.combatStartSoundEnabledCheckbox.a) {
			try {
				File file = new File(haven.Client.gameDir + "AlarmSounds/" + OptWnd.combatStartSoundFilename.buf.line() + ".wav");
				if(file.exists()) {
					AudioInputStream in = AudioSystem.getAudioInputStream(file);
					AudioFormat tgtFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
					AudioInputStream pcmStream = AudioSystem.getAudioInputStream(tgtFormat, in);
					Audio.CS klippi = new Audio.PCMClip(pcmStream, 2, 2);
					ui.globalSfxPlay(new Audio.VolAdjust(klippi, OptWnd.combatStartSoundVolumeSlider.val/50.0));
				}
			} catch(Exception ignored) {
			}
		}
	    return(new Fightsess(nact));
	}
    }

    @SuppressWarnings("unchecked")
    public Fightsess(int nact) {
	pho = -UI.scale(40);
	this.actions = new Action[nact];
    }

    protected void added() {
	fv = parent.getparent(GameUI.class).fv;
	presize();
    }

    public void presize() {
	resize(parent.sz);
	pcc = sz.div(2);
    }

    private void updatepos() {
	MapView map;
	Gob pl;
	if(((map = getparent(GameUI.class).map) == null) || ((pl = map.player()) == null))
	    return;
	Coord3f raw = pl.placed.getc();
	if(raw == null)
	    return;
	pcc = map.screenxf(raw).round2();
	pho = (int)(map.screenxf(raw.add(0, 0, UI.scale(20))).round2().sub(pcc).y) - UI.scale(20);
    }


	private static Tex getKeybindTexture(String keybindString) {
        return keybindTexCache.computeIfAbsent(keybindString, key -> new TexI(Utils.outline2(keybindsFoundry.render(key).img, Color.BLACK, true)));
	}


	private static Tex getDamagePredictionTexture(String damageValue) {
        return damagePredictionTexCache.computeIfAbsent(damageValue, key -> new TexI(Utils.outline2(damageFoundry.render(key, Color.RED).img, Color.BLACK, true)));
	}

	private void renderMyOpeningValue(GOut g, int ameteri, Coord position, Coord imageSize) {
		if (ameteri > 0) {
			Tex tex = openingValueTexCache[ameteri];
			if (tex == null) {
				tex = Text.renderstroked(String.valueOf(ameteri), openingAdditionalFont).tex();
				openingValueTexCache[ameteri] = tex;
			}
			g.aimage(tex, position.add(imageSize).sub(1, 1), 1, 1);
		}
	}

	private void renderEnemyOpeningValue(GOut g, int ameteri, Coord position, Coord imageSize) {
		if (ameteri > 0) {
			Tex tex = openingValueTexCache[ameteri];
			if (tex == null) {
				tex = Text.renderstroked(String.valueOf(ameteri), openingAdditionalFont).tex();
				openingValueTexCache[ameteri] = tex;
			}
			g.aimage(tex, position.add(imageSize).sub(1, 1), 1, 1);
		}
	}

	private void drawIntegratedOpening(GOut g, Coord center, Tex icon, String resourceName,
			int value, boolean player, int diameter, double reveal) {
		if(reveal <= 0.01)
			return;
		Color base = OptWnd.improvedOpeningsImageColor.getOrDefault(resourceName, MoonFlowerHudTheme.GOLD_SOFT);
		int alpha = (int)Math.round(255 * reveal);
		int radius = Math.max(UI.scale(7), (diameter / 2) - UI.scale(2));
		int inner = Math.max(UI.scale(4), radius - UI.scale(6));
		Color allegiance = player ? MoonFlowerHudTheme.TEAL_BRIGHT : new Color(218, 62, 69);
		g.chcolor(new Color(allegiance.getRed(), allegiance.getGreen(), allegiance.getBlue(),
				230 * alpha / 255));
		g.fellipse(center, Coord.of(radius, radius));
		g.chcolor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 195 * alpha / 255));
		g.fellipse(center, Coord.of(Math.max(1, radius - UI.scale(2)),
				Math.max(1, radius - UI.scale(2))));
		if(value > 0) {
			double sweep = Math.PI * 2 * Math.min(1.0, value / 100.0);
			g.chcolor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 255 * alpha / 255));
			g.fellipse(center, Coord.of(radius, radius), -Math.PI / 2, -Math.PI / 2 + sweep);
		}
		g.chcolor(new Color(2, 11, 15, 205 * alpha / 255));
		g.fellipse(center, Coord.of(inner, inner));
		g.chcolor();
		if(OptWnd.showCombatOpeningsAsLettersCheckBox.a) {
			Coord iconSize = Coord.of(Math.max(UI.scale(10), diameter - UI.scale(12)),
					Math.max(UI.scale(10), diameter - UI.scale(12)));
			g.chcolor(255, 255, 255, Math.min(alpha, player ? 150 : 115));
			g.image(icon, center.sub(iconSize.div(2)), iconSize);
			g.chcolor();
		}
		int bounded = Math.max(0, Math.min(openingValueTexCache.length - 1, value));
		Tex valueTex = integratedOpeningValueTexCache[bounded];
		if(valueTex == null) {
			valueTex = Text.renderstroked(Integer.toString(bounded), integratedOpeningFont).tex();
			integratedOpeningValueTexCache[bounded] = valueTex;
		}
		g.chcolor(255, 255, 255, alpha);
		g.aimage(valueTex, center, 0.5, 0.5);
		g.chcolor();
	}

	private Tex circularCombatIcon(Resource resource, int diameter) {
		int size = Math.max(UI.scale(12), diameter);
		String key = resource.name + "@" + size;
		Tex cached = circularIconCache.get(key);
		if(cached != null)
			return cached;
		BufferedImage source = resource.flayer(Resource.imgc).img;
		BufferedImage image = TexI.mkbuf(Coord.of(size, size));
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setClip(new Ellipse2D.Double(0, 0, size, size));
			graphics.drawImage(source, 0, 0, size, size, null);
		} finally {
			graphics.dispose();
		}
		Tex result = new TexI(image);
		circularIconCache.put(key, result);
		return result;
	}

	private void drawRoundCombatIcon(GOut g, Resource resource, Coord center, int diameter, double reveal) {
		if(resource == null || reveal <= 0.01)
			return;
		Tex icon = circularCombatIcon(resource, Math.max(UI.scale(12), diameter - UI.scale(6)));
		g.chcolor(255, 255, 255, (int)Math.round(255 * reveal));
		g.image(icon, center.sub(icon.sz().div(2)));
		g.chcolor();
	}

	private void drawIntegratedInitiative(GOut g, Coord center, int value, boolean opponent,
			double reveal) {
		if(reveal <= 0.01)
			return;
		Map<Integer, Tex> cache = opponent ? oipTexCache : ipTexCache;
		Color color = opponent ? OptWnd.enemyIPCombatColorOptionWidget.currentColor :
				OptWnd.myIPCombatColorOptionWidget.currentColor;
		Tex tex = cache.computeIfAbsent(value, key -> PUtils.strokeTex(Text.renderstroked(Integer.toString(key),
				color, Color.BLACK, ipFoundry)));
		g.chcolor(255, 255, 255, (int)Math.round(255 * reveal));
		g.aimage(tex, center.add(0, UI.scale(2)), 0.5, 0.5);
		g.chcolor();
	}

	private void drawIntegratedCooldown(GOut g, Coord center, int diameter, double start,
			double end, double now, double reveal) {
		if(reveal <= 0.01)
			return;
		int alpha = (int)Math.round(255 * reveal);
		int radius = Math.max(UI.scale(8), diameter / 2 - UI.scale(3));
		g.chcolor(new Color(4, 16, 20, 220 * alpha / 255));
		g.fellipse(center, Coord.of(Math.max(1, radius - UI.scale(5)),
				Math.max(1, radius - UI.scale(5))));
		boolean cooling = now < end && end > start;
		double progress = cooling ? Utils.clip((now - start) / (end - start), 0.0, 1.0) : 1.0;
		drawSegmentedCooldownRing(g, center, radius, progress, alpha, 24, UI.scale(3));
		if(cooling) {
			if(reveal > 0.35) {
				g.chcolor(255, 255, 255, alpha);
				renderIntegratedAttackTimer(g, end - now, center);
				g.chcolor();
			}
		}
	}

	private void drawIntegratedActionCooldown(GOut g, Coord center, int diameter,
			double progress, double reveal) {
		int alpha = (int)Math.round(255 * reveal);
		int radius = Math.max(UI.scale(7), (diameter / 2) - UI.scale(2));
		drawSegmentedCooldownRing(g, center, radius, Utils.clip(progress, 0.0, 1.0),
				alpha, 24, UI.scale(3));
		g.chcolor();
	}

	private void drawSegmentedCooldownRing(GOut g, Coord center, int radius, double progress,
			int alpha, int segments, int lineWidth) {
		int completed = (int)Math.floor(Utils.clip(progress, 0.0, 1.0) * segments);
		int inner = Math.max(1, radius - UI.scale(4));
		for(int i = 0; i < segments; i++) {
			double angle = (-Math.PI / 2) + ((Math.PI * 2 * i) / segments);
			Coord from = center.add((int)Math.round(Math.cos(angle) * inner),
					(int)Math.round(Math.sin(angle) * inner));
			Coord to = center.add((int)Math.round(Math.cos(angle) * radius),
					(int)Math.round(Math.sin(angle) * radius));
			if(i < completed) {
				g.chcolor(new Color(MoonFlowerHudTheme.TEAL_BRIGHT.getRed(),
						MoonFlowerHudTheme.TEAL_BRIGHT.getGreen(), MoonFlowerHudTheme.TEAL_BRIGHT.getBlue(),
						220 * alpha / 255));
			} else {
				g.chcolor(new Color(222, 178, 70, 225 * alpha / 255));
			}
			g.line(from, to, lineWidth);
		}
		g.chcolor();
	}

	private void renderRelationOpeningValue(GOut g, int openingValue, Coord topLeft, int openingOffsetX) {
		Tex tex = openingValueTexCache[openingValue];
		if (tex == null) {
			tex = Text.renderstroked(String.valueOf(openingValue), openingAdditionalFont).tex();
			openingValueTexCache[openingValue] = tex;
		}
		int valueOffset = openingValue < 10 ? 15 : openingValue < 100 ? 18 : 20;
		g.aimage(tex, new Coord(topLeft.x + UI.scale(openingOffsetX) + UI.scale(valueOffset) - UI.scale(1), topLeft.y + UI.scale(39)), 1, 0.5);
	}

	private void renderPlayerIP(GOut g, int ip, int x, int y) {
		Tex tex = ipTexCache.computeIfAbsent(ip, key -> PUtils.strokeTex(Text.renderstroked(Integer.toString(key),
            OptWnd.myIPCombatColorOptionWidget.currentColor, Color.BLACK, ipFoundry)));
		g.aimage(tex, new Coord(x - UI.scale(40), y - UI.scale(30)), 1, 0.5);
	}

	private void renderOpponentIP(GOut g, int oip, int x, int y) {
		Tex tex = oipTexCache.computeIfAbsent(oip, key -> PUtils.strokeTex(Text.renderstroked(Integer.toString(key),
            OptWnd.enemyIPCombatColorOptionWidget.currentColor, Color.BLACK, ipFoundry)));
		g.aimage(tex, new Coord(x + UI.scale(40), y - UI.scale(30)), 0, 0.5);
	}

	private void renderAttackTimer(GOut g, double atkctValue, Coord position) {
		String formatted = fmt1DecPlace(atkctValue);
		Tex tex = timerTexCache.computeIfAbsent(formatted, key -> Text.renderstroked(key).tex());
		g.aimage(tex, position, 0.5, 0.5);
	}

	private void renderIntegratedAttackTimer(GOut g, double seconds, Coord position) {
		String formatted = fmt1DecPlace(seconds);
		Tex tex = timerTexCache.computeIfAbsent("combat:" + formatted,
				key -> Text.renderstroked(formatted, integratedTimerFont).tex());
		g.aimage(tex, position, 0.5, 0.5);
	}

	private void renderMoveCooldown(GOut g, double cooldownSeconds, Coord position) {
		String formatted = fmt2DecPlaces(cooldownSeconds);
		Tex tex = timerTexCache.computeIfAbsent(formatted, key -> Text.renderstroked(key).tex());
		g.aimage(tex, position, 0.5, 0.5);
	}

	private void renderEstimatedAgilityLabel(GOut g, Coord position) {
		if (estAgiLabelTex == null) {
			estAgiLabelTex = Text.renderstroked("Est. Agi: ").tex();
		}
		g.aimage(estAgiLabelTex, position, 1, 0.5);
	}

	private void renderAgilityRange(GOut g, String agiText, Coord position) {
		Tex tex = agilityTexCache.computeIfAbsent(agiText, key -> Text.renderstroked(key).tex());
		g.aimage(tex, position, 0, 0.5);
	}

	private void renderUnknownText(GOut g, Coord position) {
		if (unknownTextTex == null) {
			unknownTextTex = Text.renderstroked("Unknown").tex();
		}
		g.aimage(unknownTextTex, position, 0, 0.5);
	}

	private void renderAgilityMin(GOut g, double minAgi, Coord position) {
		String agiText = ">" + minAgi + "x";
		Tex tex = agilityTexCache.computeIfAbsent(agiText, key -> Text.renderstroked(key, OptWnd.enemyIPCombatColorOptionWidget.currentColor, Color.BLACK).tex());
		g.aimage(tex, position, 0, 0.5);
	}

	private void renderAgilityMax(GOut g, double maxAgi, Coord position) {
		String agiText = "<" + maxAgi + "x";
		Tex tex = agilityTexCache.computeIfAbsent(agiText, key -> Text.renderstroked(key, OptWnd.myIPCombatColorOptionWidget.currentColor, Color.BLACK).tex());
		g.aimage(tex, position, 0, 0.5);
	}

    private static class Effect implements RenderTree.Node {
	Sprite spr;
	RenderTree.Slot slot;
	boolean used = true;

	Effect(Sprite spr) {this.spr = spr;}

	public void added(RenderTree.Slot slot) {
	    slot.add(spr);
	}
    }

    private static final Resource tgtfx = Resource.local().loadwait("gfx/hud/combat/trgtarw");
    private final Collection<Effect> curfx = new ArrayList<>();

    private Effect fxon(long gobid, Resource fx, Effect cur) {
	MapView map = getparent(GameUI.class).map;
	Gob gob = ui.sess.glob.oc.getgob(gobid);
	if((map == null) || (gob == null))
	    return(null);
	Pipe.Op place;
	try {
	    place = gob.placed.curplace();
	} catch(Loading l) {
	    return(null);
	}
	if((cur == null) || (cur.slot == null)) {
	    try {
		cur = new Effect(Sprite.create(new Sprite.UIOwner(this), fx, Message.nil));
		cur.slot = map.basic.add(cur.spr, place);
	    } catch(Loading l) {
		return(null);
	    }
	    curfx.add(cur);
	} else {
	    cur.slot.cstate(place);
	}
	cur.used = true;
	return(cur);
    }

    public void tick(double dt) {
	if (!loadoutChecked) {
		try {
			myStrength = ui.sess.glob.getcattr("str").comp;
			wepdmg = basedmg = 0;
			Equipory equipory = ui.gui.getequipory();
			GItem wep = equipory.getWeapon();
			if (wep != null) {
				setupWepDmg(ui.gui);
				loadoutChecked = true;
			}
		} catch (Exception ignored) {
		}
	}
	for(Iterator<Effect> i = curfx.iterator(); i.hasNext();) {
	    Effect fx = i.next();
	    if(!fx.used) {
		if(fx.slot != null) {
		    fx.slot.remove();
		    fx.slot = null;
		}
		i.remove();
	    } else {
		fx.used = false;
		fx.spr.tick(dt);
	    }
	}
    }

    public void destroy() {
	for(Effect fx : curfx) {
	    if(fx.slot != null)
		fx.slot.remove();
	}
	curfx.clear();
	for(Tex texture : circularIconCache.values())
	    texture.dispose();
	circularIconCache.clear();
	super.destroy();
    }

//    private static final Text.Furnace ipf = new PUtils.BlurFurn(new Text.Foundry(Text.serif, 18, new Color(128, 128, 255)).aa(true), 1, 1, new Color(48, 48, 96));
//    private final Indir<Text> ip =  Utils.transform(() -> fv.current.ip , v -> ipf.render("IP: " + v));
//    private final Indir<Text> oip = Utils.transform(() -> fv.current.oip, v -> ipf.render("IP: " + v));

//    private static Coord actc(int i) {
//	int rl = 5;
//	return(new Coord((actpitch * (i % rl)) - (((rl - 1) * actpitch) / 2), UI.scale(125) + ((i / rl) * actpitch)));
//    }

    private static final Coord cmc = UI.scale(new Coord(0, 67));
    private static final Coord usec1 = UI.scale(new Coord(-65, 67));
    private static final Coord usec2 = UI.scale(new Coord(65, 67));
    private Indir<Resource> lastact1 = null, lastact2 = null;
    private Text lastacttip1 = null, lastacttip2 = null;
    private Effect curtgtfx;
	private Effect curtgtfx2;
	private Effect unarmedRangeFx;
	private Effect weaponRangeFx;
    public void draw(GOut g) {
//	updatepos();
		combatMedAlphaShift();
	if (OptWnd.drawFloatingCombatOpeningsAboveYourselfCheckBox.a) {
		try {
			drawSelfCombatOpenings(g);
		} catch (Exception ignored) {}
	}

	Coord statusAnchor = MoonFlowerCombatLayout.statusCenter(ui.gui,
			MoonFlowerHudTheme.active() ? MoonFlowerHudSettings.combatStatusOffset() : Coord.z, actions.length);
	Coord deckAnchor = MoonFlowerCombatLayout.deckAnchor(ui.gui,
			MoonFlowerHudTheme.active() ? MoonFlowerHudSettings.combatDeckOffset() : Coord.z, actions.length);
	int x = statusAnchor.x;
	int y = statusAnchor.y;
	int actionX = deckAnchor.x;
	int bottom = deckAnchor.y;
	MoonFlowerPortraitHub integratedHub = (MoonFlowerHudTheme.active() && ui.gui.moonFlowerHud != null &&
			ui.gui.moonFlowerHud.visible()) ? ui.gui.moonFlowerHud : null;
	if(integratedHub != null) {
		integratedHub.drawCombatCrown(g);
		g = integratedHub.combatClip(g);
	}
	double integratedReveal = integratedHub == null ? 1.0 : integratedHub.combatContentReveal();

	double now = Utils.rtime();

//	for(Buff buff : fv.buffs.children(Buff.class))
//	    buff.draw(g.reclip(pcc.add(-buff.c.x - Buff.cframe.sz().x - UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y), buff.sz));
	ArrayList<Buff> myOpenings = new ArrayList<>(fv.buffs.children(Buff.class));
	myOpenings.sort((o2, o1) -> Integer.compare(getOpeningValue(o1), getOpeningValue(o2)));
	Buff myManeuver = null;
	for (Buff buff : myOpenings) {
		try {
			if (buff.res != null && buff.res.get() != null) {
				String name = buff.res.get().name;
				if (Config.maneuvers.contains(name)) {
					myManeuver = buff;
					break;
				}
			}
		} catch (Loading ignored) {
		}
	}
	if(myManeuver != null) {
		myOpenings.remove(myManeuver);
		if(integratedHub == null)
			myOpenings.add(myManeuver);
	}

	if(MoonFlowerHudTheme.active()) {
		int enemyOpeningCount = (fv.current == null) ? 0 : fv.current.buffs.children(Buff.class).size();
		int sideCount = Math.max(2, Math.max(myOpenings.size(), enemyOpeningCount));
		int railWidth = UI.scale(190) + (sideCount * UI.scale(70));
		if(integratedHub == null)
			MoonFlowerHudTheme.drawCombatStatusRail(g, Coord.of(x - (railWidth / 2), y - UI.scale(43)),
					Coord.of(railWidth, UI.scale(86)));
		if(fv.current != null && (integratedHub == null || integratedReveal > 0.35)) {
			OpponentHealth health = opponentHealth();
			if(integratedHub != null) {
				Area healthArea = integratedHub.combatHealthArea();
				MoonFlowerHudTheme.drawOpponentHealthPlate(g, healthArea.ul, healthArea.sz(),
						health.fraction, health.label);
			} else {
				Coord healthSize = Coord.of(Math.min(UI.scale(228), railWidth - UI.scale(36)), UI.scale(17));
				MoonFlowerHudTheme.drawOpponentHealthPlate(g,
						Coord.of(x - (healthSize.x / 2), y - UI.scale(40)), healthSize,
						health.fraction, health.label);
			}
		}
	}

	int myLocation = - Buff.cframe.sz().x - UI.scale(80);
	for (Buff buff : myOpenings) {
		try {
			Tex img = buff.res.get().flayer(Resource.imgc).tex();
			Coord isz = img.sz();
			Double ameter = buff.ameteri.get();
			int ameteri = ameter == null ? 0 : (int)(100 * ameter);
			if(integratedHub != null) {
				String name = buff.res.get().name;
				Coord center = integratedHub.combatOpeningCenter(name, false);
				if(center != null)
					drawIntegratedOpening(g, center, img, name, ameteri, true,
							integratedHub.combatOpeningDiameter(), integratedReveal);
				continue;
			}
			g.chcolor(255, 255, 255, 255);
			if(ameter != null) {
				g.image(Buff.cframe, new Coord(x + myLocation - UI.scale(3), y - UI.scale(20) - UI.scale(3)));
				g.chcolor(0, 0, 0, 255);
				g.frect(new Coord(x + myLocation, y - UI.scale(20) + UI.scale(37) - UI.scale(3)), Buff.ametersz);
				g.chcolor(255, 255, 255, 255);
				g.frect(new Coord(x + myLocation, y - UI.scale(20) + UI.scale(37) - UI.scale(3)), new Coord((int)Math.floor(ameter * Buff.ametersz.x), Buff.ametersz.y));
			} else {
				g.image(Buff.frame, new Coord(x + myLocation - UI.scale(3), y - UI.scale(20) - UI.scale(3)));
			}
			if (OptWnd.improvedOpeningsImageColor.containsKey(buff.res.get().name)) {
				g.chcolor(OptWnd.improvedOpeningsImageColor.get(buff.res.get().name));
				if (OptWnd.showCombatOpeningsAsLettersCheckBox.a)
					g.image(img, new Coord(x + myLocation, y - UI.scale(20)));
				else
					g.frect(new Coord(x + myLocation, y - UI.scale(20)), isz);
				g.chcolor(Color.WHITE);
			} else {
				g.image(img, new Coord(x + myLocation, y - UI.scale(20)));
			}
            renderMyOpeningValue(g, ameteri, new Coord(x + myLocation, y - UI.scale(20)), isz);
			myLocation -= UI.scale(40);
		} catch (Loading ignored) {
		}
	}
	if(integratedHub != null && myManeuver != null) {
		try {
			drawRoundCombatIcon(g, myManeuver.res.get(), integratedHub.combatDefenseCenter(false),
					integratedHub.combatDefenseDiameter(), integratedReveal);
		} catch(Loading ignored) {
		}
	}
	if(fv.current != null) {
//	    for(Buff buff : fv.current.buffs.children(Buff.class))
//		buff.draw(g.reclip(pcc.add(buff.c.x + UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y), buff.sz));
		ArrayList<Buff> enemyOpenings = new ArrayList<>(fv.current.buffs.children(Buff.class));
		setupOpeningArr(enemyOpenings);
		enemyOpenings.sort((o1, o2) -> Integer.compare(getOpeningValue(o2), getOpeningValue(o1)));
		Buff maneuver = null;
		for (Buff buff : enemyOpenings) {
			try {
				if (buff.res != null && buff.res.get() != null) {
					String name = buff.res.get().name;
					if (Config.maneuvers.contains(name)) {
						maneuver = buff;
						break;
					}
				}
			} catch (Loading ignored) {
			}
		}
		if(maneuver != null) {
			enemyOpenings.remove(maneuver);
			if(integratedHub == null)
				enemyOpenings.add(maneuver);
		}
		int location = UI.scale(80);
		for (Buff buff : enemyOpenings) {
			try {
				String name = buff.res.get().name;
				int meterValue = getOpeningValue(buff);
				Tex img = buff.res.get().flayer(Resource.imgc).tex();
				Coord isz = img.sz();
				Double ameter = buff.ameteri.get();
				int ameteri = ameter == null ? 0 : (int)(100 * ameter);
				if(integratedHub != null) {
					Coord center = integratedHub.combatOpeningCenter(name, true);
					if(center != null)
						drawIntegratedOpening(g, center, img, name, ameteri, false,
								integratedHub.combatOpeningDiameter(), integratedReveal);
					continue;
				}
				g.chcolor(255, 255, 255, 255);
				if(ameter != null) {
					g.image(Buff.cframe, new Coord(x + location - UI.scale(3), y - UI.scale(20) - UI.scale(3)));
					g.chcolor(0, 0, 0, 255);
					g.frect(new Coord(x + location, y - UI.scale(20) + UI.scale(37) - UI.scale(3)), Buff.ametersz);
					g.chcolor(255, 255, 255, 255);
					g.frect(new Coord(x + location, y - UI.scale(20) + UI.scale(37) - UI.scale(3)), new Coord((int)Math.floor(ameter * Buff.ametersz.x), Buff.ametersz.y));
				} else {
					g.image(Buff.frame, new Coord(x + location - UI.scale(3), y - UI.scale(20) - UI.scale(3)));
				}
				if (OptWnd.improvedOpeningsImageColor.containsKey(name)) {
					g.chcolor(OptWnd.improvedOpeningsImageColor.get(name));
					if (OptWnd.showCombatOpeningsAsLettersCheckBox.a)
						g.image(img, new Coord(x + location, y - UI.scale(20)));
					else
						g.frect(new Coord(x + location, y - UI.scale(20)), isz);
					g.chcolor(Color.WHITE);
				} else {
					if (name.equals("paginae/atk/combmed")){
						if(meterValue > 70){
							g.chcolor(255, 255-combatMedColorShift, 255-combatMedColorShift, 255);
						}
					}
					g.image(img, new Coord(x + location, y - UI.scale(20)));
					g.chcolor(255, 255, 255, 255);
				}
                renderEnemyOpeningValue(g, ameteri, new Coord(x + location, y - UI.scale(20)), isz);
				location += UI.scale(40);
			} catch (Loading ignored) {
			}
		}
		if(integratedHub != null && maneuver != null) {
			try {
				drawRoundCombatIcon(g, maneuver.res.get(), integratedHub.combatDefenseCenter(true),
						integratedHub.combatDefenseDiameter(), integratedReveal);
			} catch(Loading ignored) {
			}
		}

//	    g.aimage(ip.get().tex(), new Coord(x - UI.scale(40), y - UI.scale(30)), 1, 0.5);
		if(integratedHub != null)
			drawIntegratedInitiative(g, integratedHub.combatInitiativeCenter(false), fv.current.ip,
					false, integratedReveal);
		else
			renderPlayerIP(g, fv.current.ip, x, y);

//	    g.aimage(oip.get().tex(), new Coord(x + UI.scale(40), y - UI.scale(30)), 0, 0.5);
		if(integratedHub != null)
			drawIntegratedInitiative(g, integratedHub.combatInitiativeCenter(true), fv.current.oip,
					true, integratedReveal);
		else
			renderOpponentIP(g, fv.current.oip, x, y);


//	    if(fv.lsrel.size() > 1)
//		curtgtfx = fxon(fv.current.gobid, tgtfx, curtgtfx);
		if (!OptWnd.refreshCurrentTargetSpriteColor)
			curtgtfx2 = fxon2(fv.current.gobid, tgtfx, curtgtfx2);
		else
			OptWnd.refreshCurrentTargetSpriteColor = false;
		if (OptWnd.showYourCombatRangeCirclesCheckBox.a) {
			if (!OptWnd.refreshMyUnarmedRange)
				unarmedRangeFx = unarmedRangeFx(unarmedRangeFx);
			else
				OptWnd.refreshMyUnarmedRange = false;
			if (!OptWnd.refreshMyWeaponRange)
				weaponRangeFx = weaponRangeFx(weaponRangeFx);
			else
				OptWnd.refreshMyWeaponRange = false;
		}
	}

	    Coord cdc = new Coord(x, y);
		Coord cdc2 = new Coord(x, y - UI.scale(40));
		Coord cdc3 = new Coord(x, y + UI.scale(34));
	    if(integratedHub != null) {
		drawIntegratedCooldown(g, cdc, integratedHub.combatCooldownDiameter(), fv.atkcs,
				fv.atkct, now, integratedReveal);
	    } else if(now < fv.atkct) {
		double a = (now - fv.atkcs) / (fv.atkct - fv.atkcs);
		g.chcolor(225, 0, 0, 220);
		g.fellipse(cdc, UI.scale(new Coord(24, 24)), Math.PI / 2 - (Math.PI * 2 * Math.min(1.0 - a, 1.0)), Math.PI / 2);
		g.chcolor();
		double atkctValue = fv.atkct - now;
		renderAttackTimer(g, atkctValue, cdc);
	    }
	    if(integratedHub == null)
		g.image(cdframe, new Coord(x, y).sub(cdframe.sz().div(2)));

	try {
	    Indir<Resource> lastact = fv.lastact;
	    if(lastact != this.lastact1) {
		this.lastact1 = lastact;
		this.lastacttip1 = null;
	    }
	    double lastuse = fv.lastuse;
	    if(lastact != null) {
		Resource lastResource = lastact.get();
		Tex ut = lastResource.flayer(Resource.imgc).tex();
		Coord useul = new Coord(x - UI.scale(69), y - UI.scale(80));
		if(integratedHub != null)
			drawRoundCombatIcon(g, lastResource, integratedHub.combatMoveCenter(false),
					integratedHub.combatMoveDiameter(), integratedReveal);
		else {
			g.image(ut, useul);
			g.image(useframe, useul.sub(useframeo));
		}
		double a = now - lastuse;
		if(a < 1 && integratedHub == null) {
		    Coord off = new Coord((int)(a * ut.sz().x / 2), (int)(a * ut.sz().y / 2));
		    g.chcolor(255, 255, 255, (int)(255 * (1 - a)));
		    g.image(ut, useul.sub(off), ut.sz().add(off.mul(2)));
		    g.chcolor();
		}
	    }
		if (!fv.currentChanged && lastact1 != null && (lastact1.get() != null && lastact1.get().name != null) && fv.current != null && fv.lastMoveUpdated) {
			Double lastMoveCooldown = fv.lastMoveCooldown;
			String lastMoveName = lastact1.get().name;
			if (Config.unarmedAttackMoves.keySet().stream().anyMatch(lastMoveName::matches)) {
				Double moveDefaultCooldown = Config.unarmedAttackMoves.get(lastMoveName);
				if (Config.attackCooldownNumbers.keySet().stream().anyMatch(moveDefaultCooldown::equals)){
					if (Config.attackCooldownNumbers.get(moveDefaultCooldown).keySet().stream().anyMatch(lastMoveCooldown::equals)){
						HashMap<Double, ArrayList<Double>> cooldowns = Config.attackCooldownNumbers.get(moveDefaultCooldown);
						if (cooldowns != null) {
							Double minAgi = cooldowns.get(lastMoveCooldown).get(0);
							Double maxAgi = cooldowns.get(lastMoveCooldown).get(1);
							if (minAgi != null && maxAgi != null) {
								fv.current.minAgi = Math.max(fv.current.minAgi, minAgi);
								fv.current.maxAgi = Math.min(fv.current.maxAgi, maxAgi);
							}
						}
					}
				}
			} else if (Config.meleeAttackMoves.keySet().stream().anyMatch(lastMoveName::matches)) {
				Double moveDefaultCooldown = Config.meleeAttackMoves.get(lastMoveName);
				Gob gob = ui.sess.glob.oc.getgob(ui.gui.map.plgob);
				boolean b12Equipped = false;
				boolean cutbladeEquipped = false;
				boolean pickaxeEquipped = false;
				if (gob != null) {
					if (gob.currentWeapon.equals("b12axe"))
						b12Equipped = true;
					else if (gob.currentWeapon.equals("cutblade"))
						cutbladeEquipped = true;
					else if (gob.currentWeapon.equals("pickaxe"))
						pickaxeEquipped = true;
					if (!b12Equipped && !cutbladeEquipped && !pickaxeEquipped) { // ND: Default cooldowns, weapon has 100% attack speed
						if (Config.attackCooldownNumbers.keySet().stream().anyMatch(moveDefaultCooldown::equals)){
							if (Config.attackCooldownNumbers.get(moveDefaultCooldown).keySet().stream().anyMatch(lastMoveCooldown::equals)){
								HashMap<Double, ArrayList<Double>> cooldowns = Config.attackCooldownNumbers.get(moveDefaultCooldown);
								if (cooldowns != null && cooldowns.get(lastMoveCooldown) != null) {
									Double minAgi = cooldowns.get(lastMoveCooldown).get(0);
									Double maxAgi = cooldowns.get(lastMoveCooldown).get(1);
									if (minAgi != null && maxAgi != null) {
										fv.current.minAgi = Math.max(fv.current.minAgi, minAgi);
										fv.current.maxAgi = Math.min(fv.current.maxAgi, maxAgi);
									}
								}
							}
						}
					} else if (b12Equipped) {
						if (Config.b12AttackCooldownNumbers.keySet().stream().anyMatch(lastMoveName::matches)) {
							HashMap<Double, ArrayList<Double>> cooldowns = Config.b12AttackCooldownNumbers.get(lastMoveName);
							if (cooldowns != null && cooldowns.get(lastMoveCooldown) != null) {
								Double minAgi = cooldowns.get(lastMoveCooldown).get(0);
								Double maxAgi = cooldowns.get(lastMoveCooldown).get(1);
								if (minAgi != null && maxAgi != null) {
									fv.current.minAgi = Math.max(fv.current.minAgi, minAgi);
									fv.current.maxAgi = Math.min(fv.current.maxAgi, maxAgi);
								}
							}
						}
					} else if (cutbladeEquipped) {
						if (Config.cutbladeAttackCooldownNumbers.keySet().stream().anyMatch(lastMoveName::matches)) {
							HashMap<Double, ArrayList<Double>> cooldowns = Config.cutbladeAttackCooldownNumbers.get(lastMoveName);
							if (cooldowns != null && cooldowns.get(lastMoveCooldown) != null) {
								Double minAgi = cooldowns.get(lastMoveCooldown).get(0);
								Double maxAgi = cooldowns.get(lastMoveCooldown).get(1);
								if (minAgi != null && maxAgi != null) {
									fv.current.minAgi = Math.max(fv.current.minAgi, minAgi);
									fv.current.maxAgi = Math.min(fv.current.maxAgi, maxAgi);
								}
							}
						}
					}
				}
			}
			fv.lastMoveUpdated = false;
		}
	} catch(Exception ignored) {
	}
	if(integratedHub == null || integratedReveal > 0.35)
		renderMoveCooldown(g, fv.lastMoveCooldownSeconds, integratedHub == null ? cdc2 :
				integratedHub.combatMoveCenter(true).add(0, integratedHub.combatMoveDiameter() / 2));
	if(fv.current != null) {
		if (integratedHub == null && OptWnd.showEstimatedAgilityTextCheckBox.a) {
			renderEstimatedAgilityLabel(g, cdc3);
			if (fv.current.minAgi != 0 && fv.current.maxAgi != 2D) {
				String agiText = "" + fv.current.minAgi + "x - " + fv.current.maxAgi + "x";
				renderAgilityRange(g, agiText, cdc3);
			} else if (fv.current.minAgi == 0 && fv.current.maxAgi != 2D) {
				renderAgilityMax(g, fv.current.maxAgi, cdc3);
			} else if (fv.current.minAgi != 0 && fv.current.maxAgi == 2D) {
				renderAgilityMin(g, fv.current.minAgi, cdc3);
			} else {
				renderUnknownText(g, cdc3);
			}
		}
	    try {
		Indir<Resource> lastact = fv.current.lastact;
		if(lastact != this.lastact2) {
		    this.lastact2 = lastact;
		    this.lastacttip2 = null;
		}
		double lastuse = fv.current.lastuse;
		if(lastact != null) {
		    Resource lastResource = lastact.get();
		    Tex ut = lastResource.flayer(Resource.imgc).tex();
		    Coord useul = new Coord(x + UI.scale(69) - ut.sz().x, y - UI.scale(80));
		    if(integratedHub != null)
			drawRoundCombatIcon(g, lastResource, integratedHub.combatMoveCenter(true),
					integratedHub.combatMoveDiameter(), integratedReveal);
		    else {
			g.image(ut, useul);
			g.image(useframe, useul.sub(useframeo));
		    }
		    double a = now - lastuse;
		    if(a < 1 && integratedHub == null) {
			Coord off = new Coord((int)(a * ut.sz().x / 2), (int)(a * ut.sz().y / 2));
			g.chcolor(255, 255, 255, (int)(255 * (1 - a)));
			g.image(ut, useul.sub(off), ut.sz().add(off.mul(2)));
			g.chcolor();
		    }
		}
	    } catch(Loading l) {
	    }
	}
	if(MoonFlowerHudTheme.active() && integratedHub == null && actions.length > 0) {
		Area deckArea = MoonFlowerCombatLayout.actionDeckArea(ui.gui,
				MoonFlowerHudSettings.combatDeckOffset(), actions.length);
		MoonFlowerHudTheme.drawCombatActionDeck(g, deckArea.ul, deckArea.sz());
		g.aimage(combatDeckTitle, deckArea.ul.add(deckArea.sz().x / 2, UI.scale(12)), 0.5, 0.5);
	}
	for(int i = 0; i < actions.length; i++) {
	    Action act = actions[i];
	    try {
		if(act != null) {
		    Resource res = act.res.get();
		    Tex img = integratedHub == null ? res.flayer(Resource.imgc).tex() :
				circularCombatIcon(res, Math.max(UI.scale(12), integratedHub.combatActionDiameter() - UI.scale(6)));
		    Coord ca = integratedHub == null ?
					new Coord(actionX - 16, bottom - UI.scale(150)).add(actc(i)) :
					integratedHub.combatActionCenter(i).sub(img.sz().div(2));
			Coord hsz = img.sz().div(2);
			if(integratedHub != null)
				MoonFlowerHudTheme.drawCombatActionSelection(g,
						integratedHub.combatActionCenter(i).sub(integratedHub.combatActionDiameter() / 2,
								integratedHub.combatActionDiameter() / 2),
						Coord.of(integratedHub.combatActionDiameter(), integratedHub.combatActionDiameter()),
						i == use, i == useb);
			else if(MoonFlowerHudTheme.active())
				MoonFlowerHudTheme.drawCombatActionSlot(g, ca.sub(UI.scale(3), UI.scale(3)),
						img.sz().add(UI.scale(6), UI.scale(6)), i == use, i == useb);
		    if(integratedHub != null)
			g.chcolor(255, 255, 255, (int)Math.round(255 * integratedReveal));
		    g.image(img, ca);
		    g.chcolor();
		    if(now < act.ct) {
			double a = (now - act.cs) / (act.ct - act.cs);
			if(integratedHub != null)
				drawIntegratedActionCooldown(g, integratedHub.combatActionCenter(i),
						integratedHub.combatActionDiameter(), a, integratedReveal);
			else {
				g.chcolor(0, 0, 0, 132);
				g.prect(ca.add(hsz), hsz.inv(), hsz, (1.0 - a) * Math.PI * 2);
				g.chcolor();
			}
		    }
			int infoY = 0;
			if (OptWnd.showCombatHotkeysUICheckBox.a) {
				String keybindString = kb_acts[i].key().name();
				infoY += 8;
				Tex keybindTex = getKeybindTexture(keybindString);
				if(integratedHub != null) {
					g.chcolor(255, 255, 255, (int)Math.round(255 * integratedReveal));
					Coord keyCenter = integratedHub.combatActionCenter(i).add(0,
							(integratedHub.combatActionDiameter() / 2) - UI.scale(1));
					g.aimage(keybindTex, keyCenter, 0.5, 1.0);
				} else {
					g.aimage(keybindTex, ca.add(img.sz().x/2, img.sz().y + UI.scale(infoY)), 0.5, 0.5);
				}
				g.chcolor();
			}
			if (OptWnd.showDamagePredictUICheckBox.a) {
				String name = act.res.get().basename();
				String damage = "";
				if(Config.MapAttInfo.containsKey(name)) {	//Exists?
					Config.AttackInfo attack = Config.MapAttInfo.get(name);
					double openingMul;
					double opening;
					if(attack.getColors().length>1) {
						opening = 1;
						for(Config.Color color : attack.getColors()) {
							opening *= 1.0 - ((double)openingArr[color.getOrder()] / 100);
						}
						opening = 1.0 - opening;
					}
					else {
						opening = ((double)openingArr[attack.getColors()[0].getOrder()] / 100);
					}
					openingMul = (opening*opening);

					if(attack.isMC()) {
						double weaponDamageCalc;
						weaponDamageCalc = basedmg * Math.sqrt( Math.sqrt(ql* myStrength) / 10);
						damage = Integer.toString((int)Math.ceil( //I need to cast this into Integer so it doesnt print "0.0", printing "0" is prettier.
						weaponDamageCalc //Full damage
						*attack.getDmgMul()
						*openingMul
					));
					}
					else {
						damage = Integer.toString((int)Math.ceil( //I need to cast this into Integer so it doesnt print "0.0", printing "0" is prettier.
							attack.getDmg()*Math.sqrt(myStrength/10) //Full damage
							*openingMul
						));

					}
				}
				else{
					damage = "";
				}
				if(!damage.isEmpty()) {
					infoY += 12;
					Tex damageTex = getDamagePredictionTexture(damage);
					if(integratedHub != null) {
						g.chcolor(255, 255, 255, (int)Math.round(255 * integratedReveal));
						Coord damageCenter = integratedHub.combatActionCenter(i).add(0,
								-(integratedHub.combatActionDiameter() / 2) + UI.scale(1));
						g.aimage(damageTex, damageCenter, 0.5, 0.0);
					} else {
						g.aimage(damageTex, ca.add((int)(img.sz().x/2), img.sz().y + UI.scale(infoY)), 0.5, 0.5);
					}
					g.chcolor();
				}
			}
		    if(!MoonFlowerHudTheme.active()) {
			if(i == use) {
			    g.image(indframe, ca.sub(indframeo));
			} else if(i == useb) {
			    g.image(indbframe, ca.sub(indbframeo));
			} else {
			    g.image(actframe, ca.sub(actframeo));
			}
		    }
		}
	    } catch(Loading l) {}
	}
	if (!MoonFlowerHudTheme.active() && !OptWnd.alwaysShowCombatUIStaminaBarCheckBox.a) { // ND: Check if we're already drawing it in the gui
		IMeter.Meter stam = ui.gui.getmeter("stam", 0);
		if (stam != null) {
			Coord msz = UI.scale(new Coord(234, 22));
			Coord sc = OptWnd.stamBarLocationIsTop ? new Coord(x - msz.x/2,  y + UI.scale(70)) : new Coord(x - msz.x/2,  bottom - UI.scale(68));
			drawStamMeterBar(g, stam, sc, msz);
		}
	}

	if (!MoonFlowerHudTheme.active() && !OptWnd.alwaysShowCombatUIHealthBarCheckBox.a) { // ND: Check if we're already drawing it in the gui
		IMeter.Meter hp = ui.gui.getmeter("hp", 0);
		if (hp != null) {
			Coord msz = UI.scale(new Coord(234, 22));
			Coord sc = new Coord(x - msz.x / 2, y + UI.scale(44));
			drawHealthMeterBar(g, hp, sc, msz);
		}
	}
    }

    /** Target HP is deliberately evidence-based: the server does not expose a
     * player opponent's exact HP, while catalogued animals can be estimated
     * from native soft-damage events accumulated during this encounter. */
    private OpponentHealth opponentHealth() {
	if(fv == null || fv.current == null || ui == null || ui.sess == null)
	    return new OpponentHealth(null, "NO TARGET");
	Gob gob = ui.sess.glob.oc.getgob(fv.current.gobid);
	String resourceName = null;
	try {
	    Resource resource = gob == null ? null : gob.getres();
	    resourceName = resource == null ? null : resource.name;
	} catch(Loading ignored) {
	}
	CombatDamageSnapshot damage = CombatDamageTracker.forGlob(ui.sess.glob).snapshot(
		fv.current.gobid, resourceName, System.currentTimeMillis());
	AnimalHealthCatalog.Entry animal = AnimalHealthCatalog.find(resourceName);
	if(animal != null) {
	    AnimalHealthEstimate estimate = AnimalHealthEstimator.estimate(animal, damage);
	    return new OpponentHealth(estimate.fraction(),
		    animal.displayName().toUpperCase(Locale.ROOT) + "  •  " + estimate.label());
	}
	String target = ((resourceName != null) && resourceName.equals("gfx/borka/body")) ?
		"PLAYER" : targetName(resourceName);
	if(damage.hasCombatSoftHpObservation())
	    return new OpponentHealth(null, target + "  •  DAMAGE SEEN " + damage.combatSoftHp());
	return new OpponentHealth(null, target + "  •  HEALTH NOT EXPOSED");
    }

    private static String targetName(String resourceName) {
	if(resourceName == null || resourceName.isBlank())
	    return "OPPONENT";
	int split = resourceName.lastIndexOf('/');
	String name = split < 0 ? resourceName : resourceName.substring(split + 1);
	return name.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static final class OpponentHealth {
	final Double fraction;
	final String label;

	OpponentHealth(Double fraction, String label) {
	    this.fraction = fraction;
	    this.label = label;
	}
    }

    private Widget prevtt = null;
    private Text acttip = null;
    public static final String[] keytips = {"1", "2", "3", "4", "5", "Shift+1", "Shift+2", "Shift+3", "Shift+4", "Shift+5"};
    public Object tooltip(Coord c, Widget prev) {
	Coord statusAnchor = MoonFlowerCombatLayout.statusCenter(ui.gui,
			MoonFlowerHudTheme.active() ? MoonFlowerHudSettings.combatStatusOffset() : Coord.z, actions.length);
	Coord deckAnchor = MoonFlowerCombatLayout.deckAnchor(ui.gui,
			MoonFlowerHudTheme.active() ? MoonFlowerHudSettings.combatDeckOffset() : Coord.z, actions.length);
	int x = statusAnchor.x;
	int y = statusAnchor.y;
	int actionX = deckAnchor.x;
	int bottom = deckAnchor.y;
	MoonFlowerPortraitHub integratedHub = (MoonFlowerHudTheme.active() && ui.gui.moonFlowerHud != null &&
			ui.gui.moonFlowerHud.visible()) ? ui.gui.moonFlowerHud : null;

	ArrayList<Buff> myOpenings = new ArrayList<>(fv.buffs.children(Buff.class));
	myOpenings.sort((o2, o1) -> Integer.compare(getOpeningValue(o1), getOpeningValue(o2)));
	Buff myManeuver = null;
	for (Buff buff : myOpenings) {
		try {
			if (buff.res != null && buff.res.get() != null) {
				String name = buff.res.get().name;
				if (Config.maneuvers.contains(name)) {
					myManeuver = buff;
					break;
				}
			}
		} catch (Loading ignored) {
		}
	}
	if(myManeuver != null) {
		myOpenings.remove(myManeuver);
		if(integratedHub == null)
			myOpenings.add(myManeuver);
	}
	int myLocation = - Buff.cframe.sz().x - UI.scale(80);
	for(Buff buff : myOpenings) {
	    Coord integratedCenter = integratedHub == null ? null :
			integratedHub.combatOpeningCenter(buff.res.get().name, false);
	    Coord hitSize = integratedCenter == null ? buff.sz :
			Coord.of(integratedHub.combatOpeningDiameter(), integratedHub.combatOpeningDiameter());
	    Coord dc = integratedCenter == null ? new Coord(x + myLocation, y - UI.scale(20)) :
			integratedCenter.sub(hitSize.div(2));
	    if(c.isect(dc, hitSize)) {
		Object ret = buff.tooltip(c.sub(dc), prevtt);
		if(ret != null) {
		    prevtt = buff;
		    return(ret);
		}
			myLocation -= UI.scale(40);
	    }
	}
	if(integratedHub != null && myManeuver != null) {
	    Coord hitSize = Coord.of(integratedHub.combatDefenseDiameter(), integratedHub.combatDefenseDiameter());
	    Coord dc = integratedHub.combatDefenseCenter(false).sub(hitSize.div(2));
	    if(c.isect(dc, hitSize)) {
		Object ret = myManeuver.tooltip(c.sub(dc), prevtt);
		if(ret != null) {
		    prevtt = myManeuver;
		    return ret;
		}
	    }
	}
	if(fv.current != null) {
		ArrayList<Buff> enemyOpenings = new ArrayList<>(fv.current.buffs.children(Buff.class));
		enemyOpenings.sort((o1, o2) -> Integer.compare(getOpeningValue(o2), getOpeningValue(o1)));
		Buff maneuver = null;
		for (Buff buff : enemyOpenings) {
			try {
				if (buff.res != null && buff.res.get() != null) {
					String name = buff.res.get().name;
					if (Config.maneuvers.contains(name)) {
						maneuver = buff;
						break;
					}
				}
			} catch (Loading ignored) {
			}
		}
		if(maneuver != null) {
			enemyOpenings.remove(maneuver);
			if(integratedHub == null)
				enemyOpenings.add(maneuver);
		}
		int location = UI.scale(80);
	    for(Buff buff : enemyOpenings) {
		Coord integratedCenter = integratedHub == null ? null :
				integratedHub.combatOpeningCenter(buff.res.get().name, true);
		Coord hitSize = integratedCenter == null ? buff.sz :
				Coord.of(integratedHub.combatOpeningDiameter(), integratedHub.combatOpeningDiameter());
		Coord dc = integratedCenter == null ? new Coord(x + location, y - UI.scale(20)) :
				integratedCenter.sub(hitSize.div(2));
		if(c.isect(dc, hitSize)) {
		    Object ret = buff.tooltip(c.sub(dc), prevtt);
		    if(ret != null) {
			prevtt = buff;
			return(ret);
		    }
		}
			location += UI.scale(40);
	    }
	    if(integratedHub != null && maneuver != null) {
		Coord hitSize = Coord.of(integratedHub.combatDefenseDiameter(), integratedHub.combatDefenseDiameter());
		Coord dc = integratedHub.combatDefenseCenter(true).sub(hitSize.div(2));
		if(c.isect(dc, hitSize)) {
		    Object ret = maneuver.tooltip(c.sub(dc), prevtt);
		    if(ret != null) {
			prevtt = maneuver;
			return ret;
		    }
		}
	    }
	}
	for(int i = 0; i < actions.length; i++) {
	    Indir<Resource> act = (actions[i] == null) ? null : actions[i].res;
	    if(act != null) {
		Tex img = act.get().flayer(Resource.imgc).tex();
		Coord hitSize = integratedHub == null ? img.sz() :
				Coord.of(integratedHub.combatActionDiameter(), integratedHub.combatActionDiameter());
		Coord ca = integratedHub == null ?
				new Coord(actionX - 16, bottom - UI.scale(150)).add(actc(i)) :
				integratedHub.combatActionCenter(i).sub(hitSize.div(2));
		if(c.isect(ca, hitSize)) {
		    String state = (i == use) ? "Selected action" : ((i == useb) ? "Queued backup action" : "Ready action");
            String tip = "$b{" + act.get().flayer(Resource.tooltip).t + "}\n" +
			    "$col[239,225,185]{Hotkey: " + kb_acts[i].key().name() + "}\n" +
			    "$col[73,174,178]{State: " + state + "}";
		    double remaining = actions[i].ct - Utils.rtime();
		    if(remaining > 0)
			tip += "\n$col[196,55,48]{Cooldown: " + fmt2DecPlaces(remaining) + " seconds}";
		    if((acttip == null) || !acttip.text.equals(tip))
			acttip = RichText.render(tip, -1);
		    return(acttip);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact1;
	    if(lastact != null) {
		Coord usesz = integratedHub == null ? lastact.get().flayer(Resource.imgc).sz :
				Coord.of(integratedHub.combatMoveDiameter(), integratedHub.combatMoveDiameter());
		Coord lac = integratedHub == null ?
				new Coord(x - UI.scale(69), y - UI.scale(80)).add(usesz.div(2)) :
				integratedHub.combatMoveCenter(false);
		if(c.isect(lac.sub(usesz.div(2)), usesz)) {
		    if(lastacttip1 == null)
			lastacttip1 = Text.render(lastact.get().flayer(Resource.tooltip).t);
		    return(lastacttip1);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact2;
	    if(lastact != null) {
		Coord usesz = integratedHub == null ? lastact.get().flayer(Resource.imgc).sz :
				Coord.of(integratedHub.combatMoveDiameter(), integratedHub.combatMoveDiameter());
		Coord lac = integratedHub == null ?
				new Coord(x + UI.scale(69) - usesz.x, y - UI.scale(80)).add(usesz.div(2)) :
				integratedHub.combatMoveCenter(true);
		if(c.isect(lac.sub(usesz.div(2)), usesz)) {
		    if(lastacttip2 == null)
			lastacttip2 = Text.render(lastact.get().flayer(Resource.tooltip).t);
		    return(lastacttip2);
		}
	    }
	}
	return(null);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "act") {
	    int n = Utils.iv(args[0]);
	    if(args.length > 1) {
		Indir<Resource> res = ui.sess.getresv(args[1]);
		actions[n] = new Action(res);
	    } else {
		actions[n] = null;
	    }
	} else if(msg == "acool") {
	    int n = Utils.iv(args[0]);
	    double now = Utils.rtime();
	    actions[n].cs = now;
	    actions[n].ct = now + (Utils.dv(args[1]) * 0.06);
	} else if(msg == "use") {
	    this.use = Utils.iv(args[0]);
	    this.useb = (args.length > 1) ? Utils.iv(args[1]) : -1;
	} else if(msg == "used") {
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final KeyBinding[] kb_acts = {
	KeyBinding.get("fgt/0", KeyMatch.forcode(KeyEvent.VK_1, 0)),
	KeyBinding.get("fgt/1", KeyMatch.forcode(KeyEvent.VK_2, 0)),
	KeyBinding.get("fgt/2", KeyMatch.forcode(KeyEvent.VK_3, 0)),
	KeyBinding.get("fgt/3", KeyMatch.forchar('R', 0)),
	KeyBinding.get("fgt/4", KeyMatch.forchar('F', 0)),
	KeyBinding.get("fgt/5", KeyMatch.forcode(KeyEvent.VK_1, KeyMatch.S)),
	KeyBinding.get("fgt/6", KeyMatch.forcode(KeyEvent.VK_2, KeyMatch.S)),
	KeyBinding.get("fgt/7", KeyMatch.forcode(KeyEvent.VK_3, KeyMatch.S)),
	KeyBinding.get("fgt/8", KeyMatch.forcode(KeyEvent.VK_F2, 0)),
	KeyBinding.get("fgt/9", KeyMatch.forcode(KeyEvent.VK_F1, 0)),
    };
    public static final KeyBinding kb_relcycle =  KeyBinding.get("fgt-cycle", KeyMatch.forcode(KeyEvent.VK_TAB, 0));


    /* XXX: This is a bit ugly, but release message do need to be
     * properly sequenced with use messages in some way. */
    private class Release implements Runnable {
	final int n;

	Release(int n) {
	    this.n = n;
	    Environment env = ui.getenv();
	    Render out = env.render();
	    out.fence(this);
	    env.submit(out);
	}


	public void run() {
	    wdgmsg("rel", n);
	}
    }

    private UI.Grab holdgrab = null;
    private int held = -1;
    public boolean globtype(GlobKeyEvent ev) {
	// ev = new KeyEvent((java.awt.Component)ev.getSource(), ev.getID(), ev.getWhen(), ev.getModifiersEx(), ev.getKeyCode(), ev.getKeyChar(), ev.getKeyLocation());
	{
	    int n = -1;
	    for(int i = 0; i < kb_acts.length; i++) {
		if(kb_acts[i].key().match(ev)) {
		    n = i;
		    break;
		}
	    }
	    int fn = n;
	    if((n >= 0) && (n < actions.length)) {
		MapView map = getparent(GameUI.class).map;
		Coord mvc = map.rootxlate(ui.mc);
		if(held >= 0) {
		    new Release(held);
		    held = -1;
		}
		if(mvc.isect(Coord.z, map.sz)) {
		    map.new Maptest(mvc) {
			    protected void hit(Coord pc, Coord2d mc) {
				if((ui != null) && (ui.widgetid(Fightsess.this) >= 0))
				    wdgmsg("use", fn, 1, ui.modflags(), mc.floor(OCache.posres));
			    }

			    protected void nohit(Coord pc) {
				if((ui != null) && (ui.widgetid(Fightsess.this) >= 0))
				    wdgmsg("use", fn, 1, ui.modflags());
			    }
			}.run();
		}
		if(holdgrab == null)
		    holdgrab = ui.grabkeys(this);
		held = n;
		return(true);
	    }
	}
	if(kb_relcycle.key().match(ev.awt, KeyMatch.S)) {
	    if((ev.mods & KeyMatch.S) == 0) {
		Fightview.Relation cur = fv.current;
		if(cur != null) {
		    fv.lsrel.remove(cur);
		    fv.lsrel.addLast(cur);
		}
	    } else {
		Fightview.Relation last = fv.lsrel.getLast();
		if(last != null) {
		    fv.lsrel.remove(last);
		    fv.lsrel.addFirst(last);
		}
	    }
	    fv.wdgmsg("bump", (int)fv.lsrel.get(0).gobid);
	    return(true);
	}
	return(super.globtype(ev));
    }

    public boolean keydown(KeyDownEvent ev) {
	return(false);
    }

    public boolean keyup(KeyUpEvent ev) {
	if(ev.grabbed && (kb_acts[held].key().match(ev.awt, KeyMatch.MODS))) {
	    MapView map = getparent(GameUI.class).map;
	    new Release(held);
	    holdgrab.remove();
	    holdgrab = null;
	    held = -1;
	    return(true);
	}
	return(false);
    }

	private Effect fxon2(long gobid, Resource fx, Effect cur) {
		MapView map = getparent(GameUI.class).map;
		Gob gob = ui.sess.glob.oc.getgob(gobid);
		if((map == null) || (gob == null))
			return(null);
		Pipe.Op place;
		try {
			place = gob.placed.curplace();
		} catch(Loading l) {
			return(null);
		}
		if((cur == null) || (cur.slot == null)) {
			try {
				cur = new Effect(new CurrentAggroSprite(null));
				cur.slot = map.basic.add(cur.spr, place);
			} catch(Loading l) {
				return(null);
			}
			curfx.add(cur);
		} else {
			cur.slot.cstate(place);
		}
		cur.used = true;
		return(cur);
	}

	private Effect unarmedRangeFx(Effect currentMeleeRangeFx) {
		MapView map = getparent(GameUI.class).map;
		if (map == null)
			return null;
		Gob player = ui.gui.map.player();
		if (player == null || player.occupiedGobID != null)
			return null;
		Pipe.Op place;
		try {
			place = player.placed.curplace();
		} catch(Loading l) {
			return(null);
		}
		if((currentMeleeRangeFx == null) || (currentMeleeRangeFx.slot == null)) {
			try {
				currentMeleeRangeFx = new Effect(new CombatRangeSprite(null, 13.7f, OptWnd.unarmedCombatRangeColorOptionWidget.currentColor));
				currentMeleeRangeFx.slot = map.basic.add(currentMeleeRangeFx.spr, place);
			} catch(Loading l) {
				return(null);
			}
			curfx.add(currentMeleeRangeFx);
		} else {
			currentMeleeRangeFx.slot.cstate(place);
		}
		currentMeleeRangeFx.used = true;
		return(currentMeleeRangeFx);
	}

	private Effect weaponRangeFx(Effect currentWeaponRangeFx) {
		MapView map = getparent(GameUI.class).map;
		if (map == null)
			return null;
		Gob player = ui.gui.map.player();
		if (player == null || player.occupiedGobID != null || player.currentWeapon.equals("") || Config.WEAPON_NAMES_AND_RANGES.get(player.currentWeapon) == null)
			return null;
		Pipe.Op place;
		try {
			place = player.placed.curplace();
		} catch(Loading l) {
			return(null);
		}
		if((currentWeaponRangeFx == null) || (currentWeaponRangeFx.slot == null)) {
			try {
				currentWeaponRangeFx = new Effect(new CombatRangeSprite(null, Config.WEAPON_NAMES_AND_RANGES.get(player.currentWeapon), OptWnd.weaponCombatRangeColorOptionWidget.currentColor));
				currentWeaponRangeFx.slot = map.basic.add(currentWeaponRangeFx.spr, place);
			} catch(Loading l) {
				return(null);
			}
			curfx.add(currentWeaponRangeFx);
		} else {
			currentWeaponRangeFx.slot.cstate(place);
		}
		currentWeaponRangeFx.used = true;
		return(currentWeaponRangeFx);
	}


	private void drawSelfCombatOpenings(GOut g) {
		Coord3f rawc = ui.gui.map.player().placed.getc();
		rawc.z += 15;
		Coord sc = getparent(GameUI.class).map.screenxf(rawc).round2();
		int scaledY = sc.y - UI.scale(86);
		Coord topLeft = new Coord(sc.x - UI.scale(32), scaledY);

		ArrayList<Buff> myOpenings = new ArrayList<>(fv.buffs.children(Buff.class));
		myOpenings.sort((o2, o1) -> Integer.compare(getOpeningValue(o1), getOpeningValue(o2)));
		Buff myManeuver = null;
		for (Buff buff : myOpenings) {
			try {
				if (buff.res != null && buff.res.get() != null) {
					String name = buff.res.get().name;
					if (Config.maneuvers.contains(name)) {
						myManeuver = buff;
						break;
					}
				}
			} catch (Loading ignored) {
			}
		}
		if (myManeuver != null && myOpenings.size() > 1) {
			myOpenings.remove(myManeuver);
		}
		topLeft.x -= UI.scale(3) * myOpenings.size();

		List<TemporaryOpening> openingList = new ArrayList<>();
		for (Buff buff : fv.buffs.children(Buff.class)) {
			try {
				if (buff.res != null && buff.res.get() != null) {
					Tex img = buff.res.get().flayer(Resource.imgc).tex();
					String name = buff.res.get().name;
					if (OptWnd.improvedOpeningsImageColor.containsKey(name)) {
						int meterValue = getOpeningValue(buff);
						openingList.add(new TemporaryOpening(meterValue, name, OptWnd.improvedOpeningsImageColor.get(name), img));
					}
				}
			} catch (Loading ignored) {
			}
		}
		openingList.sort((o2, o1) -> Integer.compare(o1.value, o2.value));
		int openingOffsetX = 4;
		for (TemporaryOpening opening : openingList) {
			g.chcolor(0, 0, 0, 255);
			g.frect(new Coord(topLeft.x + UI.scale(openingOffsetX) - UI.scale(1), topLeft.y + UI.scale(30) - UI.scale(1)), UI.scale(new Coord(20, 20)));
			g.chcolor(opening.color);
			if (OptWnd.showCombatOpeningsAsLettersCheckBox.a)
				g.image(opening.img, new Coord(topLeft.x + UI.scale(openingOffsetX), topLeft.y + UI.scale(30)), UI.scale(new Coord(18, 18)));
			else
				g.frect(new Coord(topLeft.x + UI.scale(openingOffsetX), topLeft.y + UI.scale(30)), UI.scale(new Coord(18, 18)));
			g.chcolor(255, 255, 255, 255);

			renderRelationOpeningValue(g, opening.value, topLeft, openingOffsetX);
			openingOffsetX += 19;
		}
		g.chcolor(255, 255, 255, 255);
	}

	private int getOpeningValue(Buff buff) {
		Double meterDouble = buff.ameteri.get();
		if (meterDouble != null) {
			return (int) (100 * meterDouble);
		}
		return 0;
	}

	public static class TemporaryOpening{
		public int value;
		public String name;
		public Color color;
		public Tex img;

		public TemporaryOpening(int value, String name, Color color, Tex img) {
			this.value = value;
			this.name = name;
			this.color = color;
			this.img = img;
		}
	}

	public static String fmt1DecPlace(double value) {
		double rvalue = (double) Math.round(value * 10) / 10;
		return (rvalue % 1 == 0) ? Integer.toString((int) rvalue) : Double.toString(rvalue);
	}

	public static String fmt2DecPlaces(double value) {
		double rvalue = (double) Math.round(value * 100) / 100;
		return (rvalue % 1 == 0) ? Integer.toString((int) rvalue) : Double.toString(rvalue);
	}

	private void drawHealthMeterBar(GOut g, IMeter.Meter m, Coord sc, Coord msz) {
		int w = msz.x;
		int w1 = (int) Math.ceil(w * m.a);
		int w2 = (int) Math.ceil(w * (IMeter.characterSoftHealthPercent / 100));

		if (IMeter.sparring) {
			g.chcolor(Fightsess.hpBarGray);
			g.frect(sc, new Coord(w, msz.y));
			g.chcolor(Fightsess.hpBarGreen);
			g.frect(sc, new Coord(w2, msz.y));
			g.chcolor(Color.BLACK);
			g.line(new Coord(sc.x + w, sc.y), new Coord(sc.x + w, sc.y + msz.y), 2);
			g.rect(sc, new Coord(msz.x, msz.y));

			g.chcolor(Color.WHITE);
			String currentText = IMeter.characterCurrentHealth;
			if (!currentText.equals(myLastHealthBarText)) {
				if (myHealthBarTex != null)
					myHealthBarTex.dispose();
				myHealthBarTex = Text.renderstroked(currentText, Text.num12boldFnd).tex();
				myLastHealthBarText = currentText;
			}
			g.aimage(myHealthBarTex, new Coord(sc.x + msz.x / 2, sc.y + msz.y / 2), 0.5, 0.5);
		} else {
			g.chcolor(Fightsess.hpBarYellow);
			g.frect(sc, new Coord(w1, msz.y));
			g.chcolor(Fightsess.hpBarRed);
			g.frect(sc, new Coord(w2, msz.y));
			g.chcolor(Color.BLACK);
			g.line(new Coord(sc.x + w1, sc.y), new Coord(sc.x + w1, sc.y + msz.y), 2);
			g.rect(sc, new Coord(msz.x, msz.y));

			g.chcolor(Color.WHITE);
			String HHPpercentage = OptWnd.includeHHPTextHealthBarCheckBox.a
					? " (" + (Fightsess.fmt1DecPlace((int)(m.a * 100))) + "% HHP)"
					: "";
			String currentText = IMeter.characterCurrentHealth + HHPpercentage;
			if (!currentText.equals(myLastHealthBarText)) {
				if (myHealthBarTex != null)
					myHealthBarTex.dispose();
				myHealthBarTex = Text.renderstroked(currentText, Text.num12boldFnd).tex();
				myLastHealthBarText = currentText;
			}
			g.aimage(myHealthBarTex, new Coord(sc.x + msz.x / 2, sc.y + msz.y / 2), 0.5, 0.5);
		}
	}

	private void drawStamMeterBar(GOut g, IMeter.Meter m, Coord sc, Coord msz) {
		int w = msz.x;
		int w1 = (int) Math.ceil(w * m.a);
		g.chcolor(Fightsess.stamBarBlue);
		g.frect(sc, new Coord(w1, msz.y));
		g.chcolor(Color.BLACK);
		g.line(new Coord(sc.x + w1, sc.y), new Coord(sc.x + w1, sc.y + msz.y), 2);
		g.rect(sc, new Coord(msz.x, msz.y));
		g.chcolor(Color.WHITE);

		String staminaBarText = Fightsess.fmt1DecPlace((int)(m.a * 100));
		Gob myself = ui.gui.map.player();
		if (myself != null && myself.imDrinking) {
			g.chcolor(new Color(0, 222, 0));
			staminaBarText += " (Drinking)";
		}

		if (!staminaBarText.equals(myLastStaminaBarText)) {
			if (myStaminaBarTex != null)
				myStaminaBarTex.dispose();
			myStaminaBarTex = Text.renderstroked(staminaBarText, Text.num12boldFnd).tex();
			myLastStaminaBarText = staminaBarText;
		}

		g.aimage(myStaminaBarTex, new Coord(sc.x + msz.x / 2, sc.y + msz.y / 2), 0.5, 0.5);
	}

	private void setupOpeningArr(ArrayList<Buff> buffs){
		try {
			openingArr = new int[] {0,0,0,0};
			for(Buff buff : buffs) {
				switch(buff.res.get().name)	{
					case "paginae/atk/offbalance":
						openingArr[0] = getOpeningValue(buff);
						break;
					case "paginae/atk/reeling":
						openingArr[1] = getOpeningValue(buff);
						break;
					case "paginae/atk/cornered":
						openingArr[2] = getOpeningValue(buff);
						break;
					case "paginae/atk/dizzy":
						openingArr[3] = getOpeningValue(buff);
						break;
				}
			}
		} catch (Exception ignored){} // ND: Maybe it should be Loading rather than Exception, idk, idc.
	}
	private static void setupWepDmg(GameUI gui) {
		GItem wep = gui.getequipory().getWeapon();
		wepdmg = ItemInfo.getDamage(wep.info);
		//ui.gui.msg("wepdmg: "+wepdmg,Color.white);
		ql = wep.getQBuff().q;
		//ui.gui.msg("ql: "+ql,Color.white);
		basedmg = (int)(Math.ceil(wepdmg/Math.sqrt(ql/10)));
		//ui.gui.msg("basedmg: "+basedmg,Color.white);
	}

	private void combatMedAlphaShift(){
		int fps = UILoop.fps > 0 ? UILoop.fps : 1;
		int alphaShiftSpeed = 2400/fps;
		if (combatMedAlphaShiftUp) {
			if (combatMedColorShift + alphaShiftSpeed <= 255) {
				combatMedColorShift += alphaShiftSpeed;
			} else {
				combatMedAlphaShiftUp = false;
				combatMedColorShift = 255;
			}
		} else {
			if (combatMedColorShift - alphaShiftSpeed >= 0){
				combatMedColorShift -= alphaShiftSpeed;
			} else {
				combatMedAlphaShiftUp = true;
				combatMedColorShift = 0;
			}
		}
	}
}
