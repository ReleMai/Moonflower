package haven.multisession;

import haven.Coord;
import haven.UI;
import haven.Area;

/** Resource-free responsive geometry shared by the window and offline checks. */
public final class SessionConservatoryLayout {
    public static final Coord PREFERRED = UI.scale(720, 430);
    public static final Coord MINIMUM = UI.scale(640, 360);

    private SessionConservatoryLayout() {
    }

    public static Coord fittedSize(Coord available) {
        Coord margin = UI.scale(42, 86);
        Coord maximum = Coord.of(Math.max(MINIMUM.x, available.x - margin.x),
                Math.max(MINIMUM.y, available.y - margin.y));
        return(clampSize(PREFERRED, maximum));
    }

    public static Coord clampSize(Coord requested, Coord maximum) {
        Coord value = (requested == null) ? PREFERRED : requested;
        Coord limit = (maximum == null) ? PREFERRED : maximum;
        return(Coord.of(Math.max(MINIMUM.x, Math.min(value.x, Math.max(MINIMUM.x, limit.x))),
                Math.max(MINIMUM.y, Math.min(value.y, Math.max(MINIMUM.y, limit.y)))));
    }

    public static Area previewArea(Coord aperture, Coord nativeSize) {
        if(aperture == null || nativeSize == null || nativeSize.x < 1 || nativeSize.y < 1)
            return(Area.sized(aperture == null ? Coord.z : aperture));
        double scale = Math.min((double)aperture.x / nativeSize.x, (double)aperture.y / nativeSize.y);
        int width = Math.max(1, (int)Math.round(nativeSize.x * scale));
        int height = Math.max(1, (int)Math.round(nativeSize.y * scale));
        Coord offset = Coord.of((aperture.x - width) / 2, (aperture.y - height) / 2);
        return(Area.sized(offset, Coord.of(width, height)));
    }
}
