package haven.feasting;

/** Narrow UI-thread scope used only while a confirmed helper bite is dispatched. */
public final class FeastingActionContext {
    private static final ThreadLocal<Integer> BREAKAGE_OVERRIDE_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private FeastingActionContext() {
    }

    public static boolean allowsTablewareBreakage() {
        return(BREAKAGE_OVERRIDE_DEPTH.get() > 0);
    }

    public static Scope allowTablewareBreakage() {
        BREAKAGE_OVERRIDE_DEPTH.set(BREAKAGE_OVERRIDE_DEPTH.get() + 1);
        return(new Scope());
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if(closed)
                return;
            int depth = BREAKAGE_OVERRIDE_DEPTH.get() - 1;
            if(depth <= 0)
                BREAKAGE_OVERRIDE_DEPTH.remove();
            else
                BREAKAGE_OVERRIDE_DEPTH.set(depth);
            closed = true;
        }
    }
}
