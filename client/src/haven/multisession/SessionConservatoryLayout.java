package haven.multisession;

import haven.Coord;
import haven.UI;

/** Resource-free responsive geometry shared by the window and offline checks. */
public final class SessionConservatoryLayout {
    public static final Coord PREFERRED = UI.scale(720, 430);

    private SessionConservatoryLayout() {
    }

    public static Coord fittedSize(Coord available) {
        Coord margin = UI.scale(42, 86);
        return(Coord.of(Math.min(PREFERRED.x, Math.max(UI.scale(390), available.x - margin.x)),
                Math.min(PREFERRED.y, Math.max(UI.scale(270), available.y - margin.y))));
    }
}
