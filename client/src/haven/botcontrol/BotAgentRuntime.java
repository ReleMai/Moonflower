package haven.botcontrol;

import haven.Charlist;
import haven.Config;
import haven.GameUI;
import haven.LoginScreen;
import haven.UI;
import haven.Widget;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotAgentRuntime implements WebSocket.Listener {
    private static final String ENV_BOT_ID = "HAVEN_BOT_ID";
    private static final String ENV_BOT_TOKEN = "HAVEN_BOT_TOKEN";
    private static final String ENV_SERVER_URL = "HAVEN_BOT_SERVER_URL";
    private static final String SCREEN_GAME = "GAME";
    private static final String SCREEN_CHAR_SELECT = "CHAR_SELECT";
    private static final String SCREEN_LOGIN = "LOGIN";
    private static final String SCREEN_UNKNOWN = "UNKNOWN";
    private static final Object RUNTIME_LOCK = new Object();
    private static volatile BotAgentRuntime sharedRuntime;

    private final UI ui;
    private final String botId;
    private final String registrationToken;
    private final String serverUrl;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final ClientStateCollector stateCollector = new ClientStateCollector();
    private final RemoteInputExecutor remoteInputExecutor = new RemoteInputExecutor();
    private final BotActionRegistry actionRegistry = new BotActionRegistry();
    private final Object actionLock = new Object();
    private volatile GameUI gui;
    private WebSocket socket;
    private String currentTaskId;
    private String currentActionType;
    private BotAction currentAction;
    private volatile boolean actionAbortRequested;
    private volatile boolean automationPaused;
    private volatile int liveFeedIntervalMillis;
    private volatile long nextLiveFrameAt;
    private volatile boolean shuttingDown;
    private volatile boolean resumeRequested;
    private volatile JSONObject lastActivityState;
    private volatile long lastMovementActivityAt;
    private volatile String lastSnapshotSignature;
    private volatile String lastKnownCharacter;

    public static BotAgentRuntime attachIfConfigured(GameUI gui) {
        String botId = read(ENV_BOT_ID);
        String token = read(ENV_BOT_TOKEN);
        String serverUrl = read(ENV_SERVER_URL);
        if (botId == null || token == null || serverUrl == null) {
            return null;
        }
        synchronized (RUNTIME_LOCK) {
            BotAgentRuntime runtime = sharedRuntime;
            if (runtime == null || runtime.shuttingDown) {
                runtime = new BotAgentRuntime(gui.ui, botId, token, serverUrl);
                sharedRuntime = runtime;
                runtime.start();
            }
            runtime.bindGui(gui);
            return runtime;
        }
    }

    private BotAgentRuntime(UI ui, String botId, String registrationToken, String serverUrl) {
        this.ui = ui;
        this.botId = botId;
        this.registrationToken = registrationToken;
        this.serverUrl = serverUrl;
    }

    public void bindGui(GameUI gui) {
        this.gui = gui;
        String characterName = normalize(gui.chrid);
        if (characterName != null) {
            lastKnownCharacter = characterName;
        }
        sendSessionChanged("game-attached");
    }

    public void detachGui(GameUI gui) {
        if (this.gui == gui) {
            this.gui = null;
        }
        sendSessionChanged("game-detached");
    }

    public void shutdown() {
        shuttingDown = true;
        synchronized (RUNTIME_LOCK) {
            if (sharedRuntime == this) {
                sharedRuntime = null;
            }
        }
        executor.shutdownNow();
        actionExecutor.shutdownNow();
        WebSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            currentSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bot runtime shutdown");
        }
    }

    public GameUI gui() {
        return currentGameUi();
    }

    private void start() {
        connect();
        executor.scheduleAtFixedRate(() -> safeSend("HEARTBEAT", new JSONObject().put("status", automationPaused ? "IDLE" : "RUNNING")), 5, 5, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::publishFastStateUpdate, 0, 250, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::publishStateSnapshot, 2, 2, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::captureLiveFeedFrame, 0, 33, TimeUnit.MILLISECONDS);
    }

    private void connect() {
        if (shuttingDown) {
            return;
        }
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(serverUrl + "?botId=" + botId + "&token=" + registrationToken), this)
                .thenAccept(webSocket -> this.socket = webSocket);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.socket = webSocket;
        JSONObject state = collectSnapshotState();
        lastActivityState = snapshotCopy(state);
        lastSnapshotSignature = snapshotSignature(state);
        safeSend("BOT_REGISTERED", new JSONObject().put("state", state));
        sendActivity("session", "Bot connected to the operator session.", new JSONObject()
                .put("sessionStatus", state.optString("sessionStatus", "CONNECTED"))
                .put("screen", state.optString("screen", SCREEN_UNKNOWN)));
        webSocket.request(1);
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        handleCommand(new JSONObject(data.toString()));
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        return CompletableFuture.completedFuture(null);
    }

    private void handleCommand(JSONObject envelope) {
        String type = envelope.getString("type");
        JSONObject payload = envelope.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        final JSONObject commandPayload = payload;
        try {
            switch (type) {
                case "RUN_ACTION" -> actionExecutor.execute(() -> runAction(commandPayload));
                case "PAUSE_BOT" -> pauseBot();
                case "RESUME_BOT" -> resumeBot();
                case "ABORT_TASK" -> abortCurrentAutomation(commandPayload);
                case "BEGIN_TAKEOVER" -> {
                    automationPaused = true;
                    safeSend("TAKEOVER_CHANGED", new JSONObject().put("active", true));
                }
                case "END_TAKEOVER" -> {
                    automationPaused = false;
                    safeSend("TAKEOVER_CHANGED", new JSONObject().put("active", false));
                }
                case "REQUEST_SCREENSHOT" -> safeSend("LIVE_FRAME_READY", captureScreenshot());
                case "START_LIVE_FEED", "START_SCREENSHOT_STREAM" -> {
                    int intervalMillis = commandPayload.optInt("intervalMillis", 0);
                    if (intervalMillis <= 0) {
                        intervalMillis = Math.max(1, commandPayload.optInt("intervalSeconds", 3)) * 1000;
                    }
                    liveFeedIntervalMillis = Math.max(50, intervalMillis);
                    nextLiveFrameAt = 0L;
                }
                case "STOP_LIVE_FEED", "STOP_SCREENSHOT_STREAM" -> liveFeedIntervalMillis = 0;
                case "REMOTE_INPUT" -> remoteInputExecutor.execute(commandPayload);
                case "FOCUS_CLIENT" -> remoteInputExecutor.focusClientWindow();
                case "STOP_BOT" -> stopBot();
                default -> safeSend("CLIENT_ERROR", new JSONObject().put("message", "Unsupported command " + type));
            }
        } catch (Exception ex) {
            safeSend("CLIENT_ERROR", new JSONObject().put("message", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    private void runAction(JSONObject payload) {
        GameUI gameUi = currentGameUi();
        if (gameUi == null) {
            safeSend("TASK_FAILED", new JSONObject()
                    .put("taskId", payload.optString("taskId", UUID.randomUUID().toString()))
                    .put("actionType", payload.optString("actionType", "unknown"))
                    .put("message", "Bot is not currently in the game world."));
            return;
        }

        JSONObject params = payload.optJSONObject("params");
        if (params == null) {
            params = new JSONObject();
        }
        String taskId = payload.optString("taskId", UUID.randomUUID().toString());
        String actionType = payload.getString("actionType");
        BotAction action = actionRegistry.get(actionType);
        synchronized(actionLock) {
            currentTaskId = taskId;
            currentActionType = actionType;
            currentAction = action;
            actionAbortRequested = false;
        }
        BotActionContext context = new BotActionContext(gameUi, this);
        try {
            action.validate(params, context);
            safeSend("TASK_STARTED", new JSONObject().put("taskId", taskId).put("actionType", actionType));
            JSONObject result = action.start(params, context);
            synchronized(actionLock) {
                if (actionAbortRequested || !taskId.equals(currentTaskId)) {
                    return;
                }
            }
            safeSend("TASK_COMPLETED", new JSONObject().put("taskId", taskId).put("actionType", actionType).put("result", result));
        } catch (Exception ex) {
            synchronized(actionLock) {
                if (actionAbortRequested || !taskId.equals(currentTaskId)) {
                    return;
                }
            }
            safeSend("TASK_FAILED", new JSONObject().put("taskId", taskId).put("actionType", actionType).put("message", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        } finally {
            synchronized(actionLock) {
                if (taskId.equals(currentTaskId)) {
                    currentTaskId = null;
                    currentActionType = null;
                    currentAction = null;
                    actionAbortRequested = false;
                }
            }
        }
    }

    private void pauseBot() {
        automationPaused = true;
        resumeRequested = false;
        String characterName = resolveCharacterName();
        if (characterName != null) {
            lastKnownCharacter = characterName;
        }
        BotLaunchConfig.pauseAutomation(characterName);
        cancelActiveAutomationSilently();
        initiateLogoutToCharacterSelect();
        sendSessionChanged("paused");
    }

    private void resumeBot() {
        String characterName = resolveCharacterName();
        if (characterName != null) {
            lastKnownCharacter = characterName;
        }
        BotLaunchConfig.resumeAutomation(characterName);
        automationPaused = false;
        resumeRequested = true;
        if (tryResumeIntoGame(characterName)) {
            sendSessionChanged("resume-started");
        } else {
            sendSessionChanged("resume-waiting");
        }
    }

    private void stopBot() {
        String characterName = resolveCharacterName();
        if (characterName != null) {
            lastKnownCharacter = characterName;
        }
        sendActivity("session", "Operator requested logout.", new JSONObject().put("command", "STOP_BOT"));
        automationPaused = true;
        BotLaunchConfig.stopAutomation(characterName);
        cancelActiveAutomationSilently();
        initiateLogoutToCharacterSelect();
        shutdown();
    }

    private JSONObject collectSnapshotState() {
        GameUI gameUi = currentGameUi();
        if (gameUi == null) {
            return collectSessionState();
        }
        synchronized(actionLock) {
            JSONObject state = stateCollector.collectSnapshot(gameUi, botId, currentTaskId, currentActionType, automationPaused);
            updateLastKnownCharacter(state.optString("characterName", ""));
            state.put("resumeCharacter", defaultString(BotLaunchConfig.desiredCharacter()));
            return state;
        }
    }

    private JSONObject collectFastState() {
        GameUI gameUi = currentGameUi();
        if (gameUi == null) {
            return collectSessionState();
        }
        synchronized(actionLock) {
            JSONObject state = stateCollector.collectFastUpdate(gameUi, botId, currentTaskId, currentActionType, automationPaused);
            updateLastKnownCharacter(state.optString("characterName", ""));
            state.put("resumeCharacter", defaultString(BotLaunchConfig.desiredCharacter()));
            return state;
        }
    }

    private JSONObject collectSessionState() {
        JSONObject state = new JSONObject();
        state.put("botId", botId);
        state.put("sessionStatus", ui.sess != null ? "CONNECTED" : "DISCONNECTED");
        state.put("screen", currentScreen());
        state.put("automationPaused", automationPaused);
        state.put("capturedAt", Instant.now().toString());
        state.put("currentTask", currentTaskState());
        state.put("characterName", defaultString(lastKnownCharacter));
        state.put("resumeCharacter", defaultString(BotLaunchConfig.desiredCharacter()));
        return state;
    }

    private JSONObject currentTaskState() {
        JSONObject currentTask = new JSONObject();
        currentTask.put("taskId", currentTaskId == null ? "" : currentTaskId);
        currentTask.put("actionType", currentActionType == null ? "" : currentActionType);
        return currentTask;
    }

    private void publishStateSnapshot() {
        JSONObject state = collectSnapshotState();
        emitStateActivities(state);
        String signature = snapshotSignature(state);
        if (signature.equals(lastSnapshotSignature)) {
            return;
        }
        lastSnapshotSignature = signature;
        safeSend("STATE_SNAPSHOT", state);
    }

    private void publishFastStateUpdate() {
        safeSend("FAST_STATE_UPDATE", collectFastState());
    }

    private void safeSend(String type, JSONObject payload) {
        WebSocket currentSocket = socket;
        if (currentSocket == null) {
            return;
        }
        JSONObject envelope = new JSONObject();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("botId", botId);
        envelope.put("type", type);
        envelope.put("payload", payload);
        envelope.put("createdAt", Instant.now().toString());
        currentSocket.sendText(envelope.toString(), true);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        socket = null;
        if (!shuttingDown) {
            executor.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void abortCurrentAutomation(JSONObject payload) {
        BotActionContext context = contextIfReady();
        BotAction action;
        String taskId;
        String actionType;
        synchronized(actionLock) {
            actionAbortRequested = true;
            action = currentAction;
            taskId = currentTaskId;
            actionType = currentActionType;
        }
        if (action != null && context != null) {
            try {
                action.cancel(context);
            } catch (RuntimeException ignored) {
            }
        }
        if (context != null) {
            actionRegistry.abortAll(context);
        }
        if (taskId != null && actionType != null) {
            safeSend("TASK_FAILED", new JSONObject()
                    .put("taskId", taskId)
                    .put("actionType", actionType)
                    .put("message", payload.optString("message", "Aborted by operator")));
            synchronized(actionLock) {
                if (taskId.equals(currentTaskId)) {
                    currentTaskId = null;
                    currentActionType = null;
                    currentAction = null;
                }
            }
        }
    }

    private void cancelActiveAutomationSilently() {
        BotActionContext context = contextIfReady();
        BotAction action;
        synchronized(actionLock) {
            actionAbortRequested = true;
            action = currentAction;
        }
        if (action != null && context != null) {
            try {
                action.cancel(context);
            } catch (RuntimeException ignored) {
            }
        }
        if (context != null) {
            actionRegistry.abortAll(context);
        }
        synchronized(actionLock) {
            currentTaskId = null;
            currentActionType = null;
            currentAction = null;
            actionAbortRequested = false;
        }
    }

    private void captureLiveFeedFrame() {
        if (liveFeedIntervalMillis <= 0) {
            return;
        }
        if (currentGameUi() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextLiveFrameAt > now) {
            return;
        }
        nextLiveFrameAt = now + liveFeedIntervalMillis;
        try {
            safeSend("LIVE_FRAME_READY", captureScreenshot());
        } catch (Exception ex) {
            safeSend("CLIENT_ERROR", new JSONObject().put("message", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    private JSONObject captureScreenshot() throws Exception {
        GameUI gameUi = currentGameUi();
        if (gameUi == null) {
            throw new IllegalStateException("The game client is not currently in the game world.");
        }
        return new ScreenshotCaptureService(gameUi).capture();
    }

    private void initiateLogoutToCharacterSelect() {
        GameUI gameUi = currentGameUi();
        if (gameUi != null) {
            gameUi.act("lo");
        }
    }

    private boolean tryResumeIntoGame(String characterName) {
        if (currentGameUi() != null) {
            resumeRequested = false;
            return true;
        }
        Charlist charlist = findWidget(Charlist.class);
        if (charlist != null && characterName != null) {
            Config.setPlayerName(characterName);
            Config.initAutomapper(ui);
            charlist.wdgmsg("play", characterName);
            resumeRequested = false;
            return true;
        }
        LoginScreen loginScreen = findWidget(LoginScreen.class);
        if (loginScreen != null) {
            loginScreen.requestServerAutologin();
            return true;
        }
        return false;
    }

    private void sendSessionChanged(String reason) {
        safeSend("SESSION_CHANGED", new JSONObject()
                .put("reason", reason)
                .put("screen", currentScreen())
                .put("automationPaused", automationPaused)
                .put("characterName", defaultString(lastKnownCharacter))
                .put("resumeCharacter", defaultString(BotLaunchConfig.desiredCharacter())));
    }

    private GameUI currentGameUi() {
        GameUI activeUi = ui.gui;
        return activeUi != null ? activeUi : gui;
    }

    private BotActionContext contextIfReady() {
        GameUI gameUi = currentGameUi();
        return gameUi == null ? null : new BotActionContext(gameUi, this);
    }

    private String resolveCharacterName() {
        GameUI gameUi = currentGameUi();
        if (gameUi != null) {
            String characterName = normalize(gameUi.chrid);
            if (characterName != null) {
                return characterName;
            }
        }
        String configuredCharacter = normalize(BotLaunchConfig.desiredCharacter());
        if (configuredCharacter != null) {
            return configuredCharacter;
        }
        return normalize(lastKnownCharacter);
    }

    private String currentScreen() {
        if (currentGameUi() != null) {
            return SCREEN_GAME;
        }
        if (findWidget(Charlist.class) != null) {
            return SCREEN_CHAR_SELECT;
        }
        if (findWidget(LoginScreen.class) != null) {
            return SCREEN_LOGIN;
        }
        return SCREEN_UNKNOWN;
    }

    private <T extends Widget> T findWidget(Class<T> type) {
        return findWidget(type, ui.root);
    }

    private <T extends Widget> T findWidget(Class<T> type, Widget widget) {
        if (widget == null) {
            return null;
        }
        if (type.isInstance(widget)) {
            return type.cast(widget);
        }
        for (Widget child : widget.children()) {
            T nested = findWidget(type, child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private void updateLastKnownCharacter(String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            lastKnownCharacter = normalized;
        }
    }

    private static String read(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value == null || value.isBlank() ? null : value;
    }

    private void emitStateActivities(JSONObject state) {
        JSONObject previous = lastActivityState;
        lastActivityState = snapshotCopy(state);
        if (previous == null) {
            return;
        }

        emitSessionActivity(previous, state);
        emitMeterActivity(previous, state, "health", "Health");
        emitMeterActivity(previous, state, "stamina", "Stamina");
        emitMeterActivity(previous, state, "energy", "Energy");
        emitInventoryActivity(previous, state);
        emitRouteActivity(previous, state);
        emitMovementActivity(previous, state);
    }

    private void emitSessionActivity(JSONObject previous, JSONObject current) {
        String previousStatus = previous.optString("sessionStatus", "");
        String currentStatus = current.optString("sessionStatus", "");
        if (!previousStatus.equals(currentStatus) && !currentStatus.isBlank()) {
            sendActivity("session", "Session changed to " + currentStatus + ".", new JSONObject()
                    .put("previous", previousStatus)
                    .put("current", currentStatus));
        }

        String previousScreen = previous.optString("screen", "");
        String currentScreen = current.optString("screen", "");
        if (!previousScreen.equals(currentScreen) && !currentScreen.isBlank()) {
            sendActivity("session", "Screen changed to " + currentScreen + ".", new JSONObject()
                    .put("previousScreen", previousScreen)
                    .put("currentScreen", currentScreen));
        }
    }

    private void emitMeterActivity(JSONObject previous, JSONObject current, String key, String label) {
        double previousValue = readMeterPercent(previous, key);
        double currentValue = readMeterPercent(current, key);
        if (Double.isNaN(previousValue) || Double.isNaN(currentValue)) {
            return;
        }
        double delta = currentValue - previousValue;
        double threshold = "stamina".equals(key) ? 0.15 : 0.08;
        if (Math.abs(delta) < threshold) {
            return;
        }
        int percentage = (int) Math.round(currentValue * 100.0);
        String direction = delta < 0 ? "dropped" : "recovered";
        sendActivity("meter", label + " " + direction + " to " + percentage + "%.", new JSONObject()
                .put("meter", key)
                .put("previous", previousValue)
                .put("current", currentValue));
    }

    private void emitInventoryActivity(JSONObject previous, JSONObject current) {
        JSONObject previousInventory = previous.optJSONObject("inventory");
        JSONObject currentInventory = current.optJSONObject("inventory");
        if (previousInventory == null || currentInventory == null) {
            return;
        }

        int previousCount = previousInventory.optInt("itemCount", 0);
        int currentCount = currentInventory.optInt("itemCount", 0);
        if (previousCount != currentCount) {
            int delta = currentCount - previousCount;
            String direction = delta > 0 ? "picked up" : "dropped";
            sendActivity("inventory", "Inventory " + direction + " " + Math.abs(delta) + " item(s).", new JSONObject()
                    .put("previousCount", previousCount)
                    .put("currentCount", currentCount));
        }

        String previousHand = previousInventory.optString("handItem", "");
        String currentHand = currentInventory.optString("handItem", "");
        if (!previousHand.equals(currentHand)) {
            String message;
            if (previousHand.isBlank() && !currentHand.isBlank()) {
                message = "Picked up " + currentHand + " into hand.";
            } else if (!previousHand.isBlank() && currentHand.isBlank()) {
                message = "Cleared " + previousHand + " from hand.";
            } else {
                message = "Hand item changed from " + previousHand + " to " + currentHand + ".";
            }
            sendActivity("inventory", message, new JSONObject()
                    .put("previousHandItem", previousHand)
                    .put("currentHandItem", currentHand));
        }

        Map<String, Integer> previousItems = inventoryCounts(previousInventory);
        Map<String, Integer> currentItems = inventoryCounts(currentInventory);
        String changeSummary = summarizeInventoryChanges(previousItems, currentItems);
        if (!changeSummary.isBlank()) {
            sendActivity("inventory", changeSummary, new JSONObject()
                    .put("previousItems", previousItems)
                    .put("currentItems", currentItems));
        }
    }

    private void emitRouteActivity(JSONObject previous, JSONObject current) {
        JSONObject previousRoute = previous.optJSONObject("routeInfo");
        JSONObject currentRoute = current.optJSONObject("routeInfo");
        if (previousRoute == null || currentRoute == null) {
            return;
        }

        boolean wasActive = previousRoute.optBoolean("active", false);
        boolean isActive = currentRoute.optBoolean("active", false);
        if (wasActive != isActive) {
            sendActivity("route", isActive ? "Route started." : "Route stopped.", new JSONObject()
                    .put("previousActive", wasActive)
                    .put("currentActive", isActive));
        }

        int previousCheckpoints = previousRoute.optInt("checkpointCount", 0);
        int currentCheckpoints = currentRoute.optInt("checkpointCount", 0);
        if (isActive && previousCheckpoints != currentCheckpoints) {
            sendActivity("route", "Route checkpoints remaining: " + currentCheckpoints + ".", new JSONObject()
                    .put("previousCheckpointCount", previousCheckpoints)
                    .put("currentCheckpointCount", currentCheckpoints));
        }
    }

    private void emitMovementActivity(JSONObject previous, JSONObject current) {
        JSONObject previousPosition = previous.optJSONObject("position");
        JSONObject currentPosition = current.optJSONObject("position");
        if (previousPosition == null || currentPosition == null) {
            return;
        }

        double previousX = previousPosition.optDouble("x", Double.NaN);
        double previousY = previousPosition.optDouble("y", Double.NaN);
        double currentX = currentPosition.optDouble("x", Double.NaN);
        double currentY = currentPosition.optDouble("y", Double.NaN);
        if (Double.isNaN(previousX) || Double.isNaN(previousY) || Double.isNaN(currentX) || Double.isNaN(currentY)) {
            return;
        }

        double distance = Math.hypot(currentX - previousX, currentY - previousY);
        long now = System.currentTimeMillis();
        if (distance < 180.0 || (now - lastMovementActivityAt) < 15000L) {
            return;
        }
        lastMovementActivityAt = now;
        sendActivity("movement", "Moved to (" + Math.round(currentX) + ", " + Math.round(currentY) + ").", new JSONObject()
                .put("x", currentX)
                .put("y", currentY)
                .put("distance", distance));
    }

    private void sendActivity(String category, String message, JSONObject details) {
        safeSend("ACTIVITY_EVENT", new JSONObject()
                .put("source", "client")
                .put("category", category)
                .put("message", message)
                .put("details", details == null ? new JSONObject() : details));
    }

    private JSONObject snapshotCopy(JSONObject state) {
        return new JSONObject(state.toString());
    }

    private double readMeterPercent(JSONObject state, String key) {
        JSONObject meter = state.optJSONObject(key);
        if (meter == null) {
            return Double.NaN;
        }
        return meter.optDouble("percentage", Double.NaN);
    }

    private Map<String, Integer> inventoryCounts(JSONObject inventory) {
        Map<String, Integer> counts = new HashMap<>();
        JSONArray items = inventory.optJSONArray("items");
        if (items == null) {
            return counts;
        }
        for (int index = 0; index < items.length(); index++) {
            String name = String.valueOf(items.opt(index));
            counts.merge(name, 1, Integer::sum);
        }
        return counts;
    }

    private String summarizeInventoryChanges(Map<String, Integer> previousItems, Map<String, Integer> currentItems) {
        StringBuilder summary = new StringBuilder();
        appendItemChanges(summary, currentItems, previousItems, "+");
        appendItemChanges(summary, previousItems, currentItems, "-");
        return summary.toString().trim();
    }

    private void appendItemChanges(StringBuilder summary, Map<String, Integer> primary, Map<String, Integer> secondary, String prefix) {
        int added = 0;
        for (Map.Entry<String, Integer> entry : primary.entrySet()) {
            int delta = entry.getValue() - secondary.getOrDefault(entry.getKey(), 0);
            if (delta <= 0) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(prefix).append(delta).append(' ').append(entry.getKey()).append('.');
            added += 1;
            if (added >= 2) {
                break;
            }
        }
    }

    private String canonicalize(Object value) {
        if (value instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                keys.add(it.next());
            }
            Collections.sort(keys);
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(JSONObject.quote(key)).append(':').append(canonicalize(jsonObject.opt(key)));
            }
            return builder.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalize(array.opt(index)));
            }
            return builder.append(']').toString();
        }
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        return JSONObject.valueToString(value);
    }

    private String snapshotSignature(JSONObject state) {
        JSONObject copy = snapshotCopy(state);
        copy.remove("capturedAt");
        return canonicalize(copy);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
