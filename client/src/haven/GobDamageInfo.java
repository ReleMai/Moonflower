package haven;

import haven.combat.CombatDamageEvent;
import haven.combat.CombatDamageSnapshot;
import haven.combat.CombatDamageTracker;
import haven.combat.CombatEncounterLog;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class GobDamageInfo extends GobInfo {
    private static final int PAD = UI.scale(3);
    private static final Color SHP_C = Utils.col16(CombatDamageEvent.SOFT_HP_COLOR);
    private static final Color HHP_C = Utils.col16(CombatDamageEvent.HARD_HP_COLOR);
    private static final Color ARM_C = Utils.col16(CombatDamageEvent.ARMOR_COLOR);

    private final CombatDamageTracker tracker;

    public GobDamageInfo(Gob owner) {
        super(owner);
        tracker = CombatDamageTracker.forGlob(owner.glob);
        up(15);
        center = new Pair<>(0.5, 1.0);
    }

    @Override
    protected boolean enabled() {
        if(OptWnd.toggleGobDamageInfoCheckBox.a) {
            if(gob.isMe != null && gob.isMe)
                return(OptWnd.yourselfDamageInfoCheckBox.a);
            if(gob.isPartyMember())
                return(OptWnd.partyMembersDamageInfoCheckBox.a);
            return(true);
        }
        return(false);
    }

    @Override
    protected Tex render() {
        CombatDamageSnapshot damage = tracker.snapshot(gob.id, resourceName(), System.currentTimeMillis());
        if(!damage.hasAnyEvent())
            return(null);

        BufferedImage soft = Text.std.renderstroked(String.format("%d", damage.totalSoftHp()), SHP_C, Color.BLACK).img;
        BufferedImage hard = null;
        BufferedImage armor = null;
        if(OptWnd.toggleGobDamageWoundInfoCheckBox.a && damage.totalHardHp() > 0)
            hard = Text.std.renderstroked(String.format("%d", damage.totalHardHp()), HHP_C, Color.BLACK).img;
        if(OptWnd.toggleGobDamageArmorInfoCheckBox.a && damage.totalArmor() > 0)
            armor = Text.std.renderstroked(String.format("%d", damage.totalArmor()), ARM_C, Color.BLACK).img;
        return(new TexI(Utils.outline2(ItemInfo.catimgsh(PAD, PAD, null, soft, hard, armor), Color.BLACK, true)));
    }

    public void update(CombatDamageEvent event, long eventKey) {
        long now = System.currentTimeMillis();
        String resourceName = resourceName();
        if(tracker.record(gob.id, resourceName, eventKey, event, now)) {
            GameUI gui = (gob.glob == null || gob.glob.sess == null || gob.glob.sess.ui == null) ?
                    null : gob.glob.sess.ui.gui;
            CombatEncounterLog.forGlob(gob.glob).damage(gui, gob.id, resourceName, event,
                    tracker.snapshot(gob.id, resourceName, now), now);
            clear();
        }
    }

    public static void clearAllDamage(GameUI gui) {
        if(gui == null || gui.ui == null || gui.ui.sess == null)
            return;
        CombatDamageTracker.forGlob(gui.ui.sess.glob).clear();
        gui.ui.sess.glob.oc.gobAction(Gob::clearDmg);
    }

    private String resourceName() {
        try {
            Resource resource = gob.getres();
            return(resource == null ? null : resource.name);
        } catch(Loading ignored) {
            return(null);
        }
    }
}
