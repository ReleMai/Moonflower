package haven.automated;

import haven.GItem;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import haven.res.ui.tt.q.qbuff.QBuff;

/** Reads stable identity and quality fields without allowing tooltip loading to escape. */
final class FishingItemMetadata {
    private FishingItemMetadata() {
    }

    static FishingEquipment.ItemData describe(WItem item) {
        return(item == null ? FishingEquipment.ItemData.EMPTY : describe(item.item));
    }

    static FishingEquipment.ItemData describe(GItem item) {
        if(item == null)
            return(FishingEquipment.ItemData.EMPTY);
        String resource = "";
        String name = "";
        try {
            Resource res = item.getres();
            resource = res == null ? "" : res.name;
        } catch(Loading ignored) {
        }
        try {
            name = item.getname();
            if("it's null".equals(name) || "exception".equals(name))
                name = "";
        } catch(RuntimeException ignored) {
        }
        return(new FishingEquipment.ItemData(resource, name, quality(item)));
    }

    static FishingEquipment.ItemData describe(ItemInfo.Contents contents) {
        String name = "";
        Double quality = null;
        for(ItemInfo info : contents.sub) {
            if(info instanceof ItemInfo.Name)
                name = ((ItemInfo.Name)info).str.text;
            if(info instanceof QBuff)
                quality = ((QBuff)info).q;
        }
        return(new FishingEquipment.ItemData("", name, quality));
    }

    static String name(WItem item) {
        return(describe(item).displayName);
    }

    static String resource(WItem item) {
        return(describe(item).resourceName);
    }

    static Double quality(WItem item) {
        return(item == null ? null : quality(item.item));
    }

    private static Double quality(GItem item) {
        if(item == null)
            return(null);
        try {
            QBuff quality = item.getQBuff();
            return(quality == null ? null : quality.q);
        } catch(RuntimeException ignored) {
            return(null);
        }
    }
}
