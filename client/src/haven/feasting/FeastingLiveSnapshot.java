package haven.feasting;

import haven.BAttrWnd;
import haven.GameUI;
import haven.GItem;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.cookbook.CookbookAttribute;
import haven.res.ui.tt.q.qbuff.QBuff;
import haven.resutil.FoodInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** UI-thread adapter that converts live widgets into immutable planning data. */
public final class FeastingLiveSnapshot {
    private FeastingLiveSnapshot() {
    }

    public static FeastingSnapshot capture(GameUI gui, Window table) {
        if(gui == null || gui.ui == null || gui.chrwdg == null ||
                gui.chrwdg.battr == null)
            return(empty(TablewareProtection.inspect(table)));

        BAttrWnd attributes = gui.chrwdg.battr;
        EnumMap<CookbookAttribute, Double> currentFeps = new EnumMap<>(CookbookAttribute.class);
        double currentTotal = 0;
        int pending = 0;
        boolean meterReadable = true;
        try {
            for(BAttrWnd.FoodMeter.El element : attributes.feps.els) {
                String eventName = element.ev().nm;
                CookbookAttribute attribute = CookbookAttribute.forEvent(eventName);
                if(attribute != null)
                    currentFeps.merge(attribute, element.a, Double::sum);
                currentTotal += element.a;
            }
        } catch(Loading loading) {
            pending++;
            meterReadable = false;
        }

        EnumMap<CookbookAttribute, Integer> bases = new EnumMap<>(CookbookAttribute.class);
        for(CookbookAttribute attribute : foodAttributes()) {
            String code = attributeCode(attribute);
            if(code != null) {
                int base = gui.ui.sess.glob.getcattr(code).base;
                if(base > 0)
                    bases.put(attribute, base);
            }
        }

        List<FeastingCandidate> candidates = new ArrayList<>();
        Set<Inventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        collectFoodInventories(table, inventories);
        if(gui.maininv != null)
            inventories.add(gui.maininv);
        for(Inventory inventory : inventories) {
            FeastingCandidate.Source source = inventory == gui.maininv ?
                    FeastingCandidate.Source.INVENTORY : FeastingCandidate.Source.TABLE;
            for(WItem widget : new ArrayList<>(inventory.wmap.values())) {
                try {
                    FeastingCandidate candidate = candidate(gui, widget.item, source);
                    if(candidate != null)
                        candidates.add(candidate);
                } catch(Loading loading) {
                    pending++;
                } catch(RuntimeException ignored) {
                    pending++;
                }
            }
        }
        candidates.sort((left, right) -> Integer.compare(left.widgetId, right.widgetId));

        TablewareProtection.Inspection tableware = TablewareProtection.inspect(table);
        double hunger = attributes.glut == null ? Double.NaN : attributes.glut.glut;
        return(new FeastingSnapshot(meterReadable ? attributes.feps.cap : 0,
                currentTotal, hunger, pending,
                tableware.state, tableware.atRisk, currentFeps, bases, candidates));
    }

    private static FeastingCandidate candidate(GameUI gui, GItem item,
                                                FeastingCandidate.Source source) {
        List<ItemInfo> info = item.cookbookInfo();
        FoodInfo food = ItemInfo.find(FoodInfo.class, info);
        if(food == null)
            return(null);
        FoodInfo.Efficiency efficiency = food.currentEfficiency(isSalted(info));
        if(!efficiency.available)
            throw(new Loading("Food efficiency is not available yet"));

        double fepMultiplier = efficiency.fepPercent / 100d;
        double hungerMultiplier = efficiency.hungerPercent / 100d;
        EnumMap<CookbookAttribute, Double> feps = new EnumMap<>(CookbookAttribute.class);
        double total = 0;
        for(FoodInfo.Event event : food.evs) {
            double amount = event.a * fepMultiplier;
            total += amount;
            CookbookAttribute attribute = CookbookAttribute.forEvent(event.ev.nm);
            if(attribute != null)
                feps.merge(attribute, amount, Double::sum);
        }
        Resource resource = item.getres();
        ItemInfo.Name nameInfo = ItemInfo.find(ItemInfo.Name.class, info);
        QBuff quality = ItemInfo.find(QBuff.class, info);
        String name = nameInfo == null ? resource.basename() : nameInfo.str.text;
        int widgetId = gui.ui.widgetid(item);
        if(widgetId < 0)
            return(null);
        return(new FeastingCandidate(widgetId, item.num > 0 ? item.num : 1,
                item.infoseq, source, name, resource.name,
                quality == null ? 10d : quality.q,
                food.glut * 1000d * hungerMultiplier, food.end * 100d, total, feps));
    }

    private static boolean isSalted(List<ItemInfo> info) {
        for(ItemInfo value : info) {
            if(value instanceof ItemInfo.AdHoc adHoc &&
                    "Salted".equalsIgnoreCase(adHoc.str.text))
                return(true);
        }
        return(false);
    }

    private static void collectFoodInventories(Widget widget, Set<Inventory> result) {
        if(widget == null)
            return;
        Inventory inventory = Inventory.fromWidget(widget);
        if(inventory != null && !TablewareProtection.isTablewareInventory(inventory))
            result.add(inventory);
        for(Widget child = widget.child; child != null; child = child.next)
            collectFoodInventories(child, result);
    }

    private static FeastingSnapshot empty(TablewareProtection.Inspection tableware) {
        return(new FeastingSnapshot(0, 0, Double.NaN, 0, tableware.state,
                tableware.atRisk, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList()));
    }

    public static List<CookbookAttribute> foodAttributes() {
        List<CookbookAttribute> result = new ArrayList<>();
        for(CookbookAttribute attribute : CookbookAttribute.values()) {
            if(!attribute.allRecipes())
                result.add(attribute);
        }
        return(Collections.unmodifiableList(result));
    }

    private static String attributeCode(CookbookAttribute attribute) {
        return(switch(attribute) {
            case STRENGTH -> "str";
            case AGILITY -> "agi";
            case INTELLIGENCE -> "int";
            case CONSTITUTION -> "con";
            case PERCEPTION -> "prc";
            case CHARISMA -> "csm";
            case DEXTERITY -> "dex";
            case WILL -> "wil";
            case PSYCHE -> "psy";
            default -> null;
        });
    }
}
