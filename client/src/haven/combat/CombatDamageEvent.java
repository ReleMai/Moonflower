package haven.combat;

import java.util.Optional;

/** A validated integer combat number decoded from the float-image resource. */
public final class CombatDamageEvent {
    public static final int SOFT_HP_COLOR = 61455;
    public static final int HARD_HP_COLOR = 64527;
    public static final int ARMOR_COLOR = 36751;

    public enum Type {
        SOFT_HP,
        HARD_HP,
        ARMOR
    }

    private final Type type;
    private final int amount;

    public CombatDamageEvent(Type type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public Type type() {
        return(type);
    }

    public int amount() {
        return(amount);
    }

    public static Optional<CombatDamageEvent> fromFloatImage(int rawValue, int flags, int color) {
        // Score.java uses bits 1-2 for decimal/time/float encodings. Combat
        // damage colors are expected to be plain integers; reject anything else.
        if((flags & 6) != 0)
            return(Optional.empty());

        Type type = switch(color) {
            case SOFT_HP_COLOR -> Type.SOFT_HP;
            case HARD_HP_COLOR -> Type.HARD_HP;
            case ARMOR_COLOR -> Type.ARMOR;
            default -> null;
        };
        return(type == null ? Optional.empty() : Optional.of(new CombatDamageEvent(type, rawValue)));
    }
}
