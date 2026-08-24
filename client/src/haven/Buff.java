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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.*;
import haven.ItemInfo.AttrCache;

public class Buff extends Widget implements ItemInfo.ResOwner, Bufflist.Managed {
    public static final Text.Foundry nfnd = new Text.Foundry(Text.dfont, 10);
    public static final Tex frame = Resource.loadtex("gfx/hud/buffs/frame");
    public static final Tex cframe = Resource.loadtex("gfx/hud/buffs/cframe");
    public static final Tex ameter = Resource.loadtex("gfx/hud/buffs/cframe-m");
    public static final Coord imgoff = UI.scale(3, 3);
    public static final int ameterx1 = UI.scale(3), ameterx2 = UI.scale(35); /* XXX: Detect? */
    public static final Coord ametersz = UI.scale(new Coord(32, 3)); // ND: old variable that loftar removed, but I still use in Fightsess for the combat UI
    public static final int textw = UI.scale(200);
    public Indir<Resource> res;
    protected int a = 255;
    protected boolean dest = false;
    private ItemInfo.Raw rawinfo = null;
    private List<ItemInfo> info = Collections.emptyList();
    private double displayScale = 1.0;
    private boolean circularDisplay = false;
    private Resource circularIconResource;
    private int circularIconSide;
    private Tex circularIcon;

    @RName("buff")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Indir<Resource> res = ui.sess.getresv(args[0]);
	    return(new Buff(res));
	}
    }

    public Buff(Indir<Resource> res) {
	super(cframe.sz());
	this.res = res;
    }

    public void setDisplayScale(double scale) {
	displayScale = Utils.clip(scale, 0.5, 1.0);
	resize(dscale(cframe.sz()));
    }

    public void setCircularDisplay(boolean circular) {
	circularDisplay = circular;
    }

    static BufferedImage circularIcon(BufferedImage source, int side) {
	BufferedImage target = TexI.mkbuf(Coord.of(side, side));
	Graphics2D graphics = target.createGraphics();
	graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	graphics.setClip(new Ellipse2D.Double(0, 0, side, side));
	int crop = Math.min(source.getWidth(), source.getHeight());
	int sx = (source.getWidth() - crop) / 2;
	int sy = (source.getHeight() - crop) / 2;
	graphics.drawImage(source, 0, 0, side, side, sx, sy, sx + crop, sy + crop, null);
	graphics.dispose();
	return target;
    }

    private Tex circularIcon(Resource resource, int side) {
	if(circularIcon == null || circularIconResource != resource || circularIconSide != side) {
	    disposeCircularIcon();
	    circularIconResource = resource;
	    circularIconSide = side;
	    circularIcon = new TexI(circularIcon(resource.flayer(Resource.imgc).img, side));
	}
	return circularIcon;
    }

    private void disposeCircularIcon() {
	if(circularIcon != null)
	    circularIcon.dispose();
	circularIcon = null;
	circularIconResource = null;
	circularIconSide = 0;
    }

    private int dscale(int value) {
	return(Math.max(1, (int)Math.round(value * displayScale)));
    }

    private Coord dscale(Coord value) {
	return(Coord.of(dscale(value.x), dscale(value.y)));
    }

    public Resource resource() {
	return(res.get());
    }
    private static final OwnerContext.ClassResolver<Buff> ctxr = new OwnerContext.ClassResolver<Buff>()
	.add(Buff.class, wdg ->wdg)
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    public <T> T context(Class<T> cl) {return(ctxr.context(cl, this));}

    public List<ItemInfo> info() {
	if(info == null) {
	    info = ItemInfo.buildinfo(this, rawinfo);
	    Resource.Pagina pag = res.get().layer(Resource.pagina);
	    if(pag != null)
		info.add(new ItemInfo.Pagina(this, pag.text));
	}
	return(info);
    }

    public interface AMeterInfo {
	public double ameter();
    }

    public static abstract class AMeterTip extends ItemInfo.Tip implements AMeterInfo {
	public AMeterTip(Owner owner) {
	    super(owner);
	}

	public void layout(Layout l) {
	    int n = (int)Math.floor(ameter() * 100);
	    l.cmp.add(Text.render(" (" + n + "%)").img, new Coord(l.cmp.sz.x, 0));
	}

	public int order() {return(10);}
	public Tip shortvar() {return(this);}
    }

    public final AttrCache<Double> ameteri = new AttrCache<>(this::info, AttrCache.map1(AMeterInfo.class, minf -> minf::ameter));
    public final AttrCache<Tex> nmeteri = new AttrCache<>(this::info, AttrCache.map1s(GItem.NumberInfo.class, ninf -> new TexI(GItem.NumberInfo.numrender(ninf.itemnum(), ninf.numcolor()))));
    private final AttrCache<Double> cmeteri = new AttrCache<>(this::info, AttrCache.map1(GItem.MeterInfo.class, minf -> minf::meter));

    public void draw(GOut g) {
	if(circularDisplay) {
	    drawCircular(g);
	    return;
	}
	g.chcolor(255, 255, 255, a);
	Double ameterv = ameteri.get();
	if(ameterv != null) {
	    g.image(cframe, Coord.z, sz);
	    int w = (int)Math.floor((ameterx2 - ameterx1) * ameterv);
	    int x1 = dscale(ameterx1);
	    g.image(ameter, Coord.z, Coord.of(x1, 0), Coord.of(x1 + dscale(w), sz.y), sz);
	} else {
	    g.image(frame, Coord.z, sz);
	}
	try {
	    Tex img = res.get().flayer(Resource.imgc).tex();
	    Coord iconOrigin = dscale(imgoff);
	    Coord iconSize = dscale(img.sz());
	    g.image(img, iconOrigin, iconSize);
	    Tex nmeter = nmeteri.get();
	    if(nmeter != null)
		g.aimage(nmeter, iconOrigin.add(iconSize).sub(dscale(1), dscale(1)), 1, 1, dscale(nmeter.sz()));
	    Double cmeter = cmeteri.get();
	    if(cmeter != null) {
		double m = Utils.clip(cmeter, 0.0, 1.0);
		g.chcolor(255, 255, 255, a / 2);
		Coord ccc = iconSize.div(2);
		g.prect(iconOrigin.add(ccc), ccc.inv(), iconSize.sub(ccc), Math.PI * 2 * m);
		g.chcolor(255, 255, 255, a);
	    }
	} catch(Loading e) {}
    }

    private void drawCircular(GOut g) {
	Coord center = sz.div(2);
	int radius = Math.max(2, (Math.min(sz.x, sz.y) / 2) - UI.scale(1));
	g.chcolor(255, 255, 255, a);
	MoonFlowerHudTheme.drawCircularSlot(g, center, radius, false);
	try {
	    Resource resource = res.get();
	    int iconSide = Math.max(UI.scale(12), (int)Math.round(Math.min(sz.x, sz.y) * 0.62));
	    Coord iconSize = Coord.of(iconSide, iconSide);
	    Coord iconOrigin = center.sub(iconSide / 2, iconSide / 2);
	    g.chcolor(255, 255, 255, a);
	    Tex img = circularIcon(resource, iconSide);
	    g.image(img, iconOrigin, iconSize);
	    Tex nmeter = nmeteri.get();
	    if(nmeter != null)
		g.aimage(nmeter, center.add(radius - UI.scale(2), radius - UI.scale(2)), 1, 1,
			dscale(nmeter.sz()));
	    Double cmeter = cmeteri.get();
	    if(cmeter != null) {
		double m = Utils.clip(cmeter, 0.0, 1.0);
		g.chcolor(255, 255, 255, a / 2);
		g.prect(center, Coord.of(-radius, -radius), Coord.of(radius, radius), Math.PI * 2 * m);
	    }
	} catch(Loading e) {
	}
	g.chcolor();
    }

    private BufferedImage shorttip() {
	if(rawinfo != null)
	    return(ItemInfo.shorttip(info()));
	String ret = res.get().flayer(Resource.tooltip).t;
	return(Text.render(ret).img);
    }

    private BufferedImage longtip() {
	BufferedImage img;
	if(rawinfo != null) {
	    img = ItemInfo.longtip(info());
	} else {
	    img = shorttip();
	    Resource.Pagina pag = res.get().layer(Resource.pagina);
	    if(pag != null)
		img = ItemInfo.catimgs(0, img, RichText.render("\n" + pag.text, textw).img);
	}
	return(img);
    }

    private double hoverstart;
    private Tex shorttip, longtip;
    private List<ItemInfo> ttinfo = null;
    public Object tooltip(Coord c, Widget prev) {
	double now = Utils.rtime();
	if(prev != this)
	    hoverstart = now;
	if(now - hoverstart < 1.0) {
	    if(shorttip == null)
		shorttip = new TexI(shorttip());
	    return(shorttip);
	} else {
	    if(longtip == null)
		longtip = new TexI(longtip());
	    return(longtip);
	}
    }

    public void reqdestroy() {
	anims.clear();
	final Coord o = this.c;
	dest = true;
	new NormAnim(0.35) {
	    public void ntick(double a) {
		Buff.this.a = 255 - (int)(255 * a);
		Buff.this.c = o.add(0, (int)(a * a * Buff.this.sz.y));
		if(a == 1.0)
		    destroy();
	    }
	};
    }

    public void move(Coord c, double off) {
	if(dest)
	    return;
	double ival = 0.8;
	double foff = off * (1.0 - 0.8);
	final Coord o = this.c;
	final Coord d = c.sub(o);
	new NormAnim(0.5) {
	    public void ntick(double a) {
		a = Utils.clip((a - foff) * (1.0 / ival), 0, 1);
		Buff.this.c = o.add(d.mul(Utils.smoothstep(a)));
	    }
	};
    }

    public void move(Coord c) {
	move(c, 0);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "ch") {
	    disposeCircularIcon();
	    this.res = ui.sess.getresv(args[0]);
	} else if(msg == "tt") {
	    info = null;
	    rawinfo = new ItemInfo.Raw(args);
	    shorttip = longtip = null;
	} else {
	    super.uimsg(msg, args);
	}
    }

    public boolean mousedown(MouseDownEvent ev) {
	Coord nativeCoord = Coord.of((int)Math.round(ev.c.x / displayScale), (int)Math.round(ev.c.y / displayScale));
	wdgmsg("cl", nativeCoord.sub(imgoff), ev.b, ui.modflags());
	return(true);
    }

    @Override
    public void destroy() {
	disposeCircularIcon();
	super.destroy();
    }
}
