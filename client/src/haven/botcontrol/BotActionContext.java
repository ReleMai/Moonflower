package haven.botcontrol;

import haven.GameUI;

public class BotActionContext {
    private final GameUI gui;
    private final BotAgentRuntime runtime;

    public BotActionContext(GameUI gui, BotAgentRuntime runtime) {
        this.gui = gui;
        this.runtime = runtime;
    }

    public GameUI gui() {
        return gui;
    }

    public BotAgentRuntime runtime() {
        return runtime;
    }
}
