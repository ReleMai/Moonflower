package haven;

import java.util.ArrayList;
import java.util.List;

/** Opens the native server-provided Alchemy Book action when it is available. */
public final class AlchemyBookAction {
    public static final String RESOURCE_NAME = "paginae/act/alchbook";

    private AlchemyBookAction() {
    }

    public static boolean isBookActionResource(String resourceName) {
        return(RESOURCE_NAME.equals(resourceName));
    }

    /**
     * Uses the native action page so the resource-owned BookButton can open
     * or toggle its own BookWindow. This deliberately does not construct a
     * second copy of the resource-provided alchemy book.
     */
    public static boolean open(GameUI gui) {
        if(gui == null || gui.menu == null)
            return(false);

        MenuGrid menu = gui.menu;
        List<MenuGrid.Pagina> pages;
        synchronized(menu.paginae) {
            pages = new ArrayList<>(menu.paginae);
        }
        for(MenuGrid.Pagina page : pages) {
            try {
                Resource resource = page.res();
                if(resource != null && isBookActionResource(resource.name)) {
                    menu.use(page.button(), new MenuGrid.Interaction(), true);
                    return(true);
                }
            } catch(Loading loading) {
                // Resource pages are populated asynchronously; a later click
                // can retry without sending an invalid action to the server.
            } catch(RuntimeException unavailable) {
                // Dynamic resource code must fail closed if it is unavailable.
            }
        }
        return(false);
    }
}
