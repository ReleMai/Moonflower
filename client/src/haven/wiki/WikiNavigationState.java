package haven.wiki;

import java.util.ArrayDeque;
import java.util.Deque;

/** Browser-like record navigation without any UI or network responsibilities. */
public final class WikiNavigationState {
    private final Deque<WikiReference> back = new ArrayDeque<>();
    private final Deque<WikiReference> forward = new ArrayDeque<>();
    private WikiReference current;

    public WikiReference current() {
        return(current);
    }

    public boolean canBack() {
        return(!back.isEmpty());
    }

    public boolean canForward() {
        return(!forward.isEmpty());
    }

    public WikiReference open(WikiReference reference) {
        if(reference == null)
            return(current);
        if(current != null && !current.equals(reference))
            back.push(current);
        current = reference;
        forward.clear();
        return(current);
    }

    public WikiReference back() {
        if(back.isEmpty())
            return(current);
        if(current != null)
            forward.push(current);
        current = back.pop();
        return(current);
    }

    public WikiReference forward() {
        if(forward.isEmpty())
            return(current);
        if(current != null)
            back.push(current);
        current = forward.pop();
        return(current);
    }

    public void clear() {
        back.clear();
        forward.clear();
        current = null;
    }
}
