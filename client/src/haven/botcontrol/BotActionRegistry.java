package haven.botcontrol;

import haven.Button;
import haven.CheckBox;
import haven.CheckpointManager;
import haven.Coord2d;
import haven.GameUI;
import haven.Widget;
import haven.Window;
import haven.automated.CellarDiggingBot;
import haven.automated.CleanupBot;
import haven.automated.FishingBot;
import haven.automated.GrubGrubBot;
import haven.automated.InventorySorter;
import haven.automated.OceanScoutBot;
import haven.automated.RoastingSpitBot;
import haven.automated.TarKilnCleanerBot;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class BotActionRegistry {
    private final Map<String, BotAction> actions = new HashMap<>();

    public BotActionRegistry() {
        register(new BotAction() {
            @Override
            public String name() {
                return "cleanup.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) throws Exception {
                GameUI gui = context.gui();
                ensureCleanupStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopCleanup(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "cleanup.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                if (gui.cleanupBot != null) {
                    gui.cleanupBot.stop();
                    gui.cleanupBot = null;
                    gui.cleanupThread = null;
                }
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "fishing.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) throws Exception {
                GameUI gui = context.gui();
                ensureFishingStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopFishing(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "fishing.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                gui.fishingBot.stop();
                gui.fishingThread = null;
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "route.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
                if (!params.has("checkpoints")) {
                    throw new IllegalArgumentException("route.start requires checkpoints");
                }
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) throws Exception {
                GameUI gui = context.gui();
                JSONArray checkpoints = params.getJSONArray("checkpoints");
                if (gui.map.checkpointManager == null || gui.map.checkpointManagerThread == null) {
                    gui.map.checkpointManager = new CheckpointManager(gui);
                    gui.add(gui.map.checkpointManager, haven.Utils.getprefc("wndc-queuedMovementWindow", new haven.Coord(100, 100)));
                    gui.map.checkpointManagerThread = new Thread(gui.map.checkpointManager, "CheckpointManager");
                    gui.map.checkpointManagerThread.start();
                }
                gui.map.checkpointManager.checkpointList.removeAllItems();
                for (int i = 0; i < checkpoints.length(); i++) {
                    JSONObject checkpoint = checkpoints.getJSONObject(i);
                    gui.map.checkpointManager.addCoord(new Coord2d(checkpoint.getDouble("x"), checkpoint.getDouble("y")));
                }
                clickPrivateButton(gui.map.checkpointManager, "pause");
                return new JSONObject().put("status", "started").put("count", checkpoints.length());
            }

            @Override
            public void cancel(BotActionContext context) {
                pauseRoute(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "route.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                if (gui.map.checkpointManager != null) {
                    gui.map.checkpointManager.pauseIt();
                }
                return new JSONObject().put("status", "paused");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "inventory.sort";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                InventorySorter.sortAll(context.gui());
                return new JSONObject().put("status", "scheduled");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "auto-repeat-flower";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
                if (!params.has("option")) {
                    throw new IllegalArgumentException("auto-repeat-flower requires option");
                }
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                haven.automated.AutoRepeatFlowerMenuScript.option = params.getString("option");
                return new JSONObject().put("status", "armed");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "auto-repeat-flower.clear";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                haven.automated.AutoRepeatFlowerMenuScript.option = null;
                if (context.gui().autoRepeatFlowerMenuScriptThread != null) {
                    context.gui().autoRepeatFlowerMenuScriptThread.interrupt();
                    context.gui().autoRepeatFlowerMenuScriptThread = null;
                }
                return new JSONObject().put("status", "cleared");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "grubgrub.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                ensureGrubGrubStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopGrubGrub(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "grubgrub.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                stopGrubGrub(context.gui());
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "tar-kiln.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) throws Exception {
                GameUI gui = context.gui();
                ensureTarKilnStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopTarKiln(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "tar-kiln.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                stopTarKiln(context.gui());
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "roasting.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                ensureRoastingStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopRoasting(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "roasting.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                stopRoasting(context.gui());
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "cellar.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) throws Exception {
                GameUI gui = context.gui();
                ensureCellarStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopCellar(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "cellar.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                stopCellar(context.gui());
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "ocean-scout.start";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                GameUI gui = context.gui();
                ensureOceanScoutStarted(gui);
                return new JSONObject().put("status", "started");
            }

            @Override
            public void cancel(BotActionContext context) {
                stopOceanScout(context.gui());
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "ocean-scout.stop";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                stopOceanScout(context.gui());
                return new JSONObject().put("status", "stopped");
            }
        });
        register(new BotAction() {
            @Override
            public String name() {
                return "safe-logout";
            }

            @Override
            public void validate(JSONObject params, BotActionContext context) {
            }

            @Override
            public JSONObject start(JSONObject params, BotActionContext context) {
                context.gui().act("lo");
                return new JSONObject().put("status", "logging-out");
            }
        });
    }

    public BotAction get(String actionType) {
        BotAction action = actions.get(actionType);
        if (action == null) {
            throw new IllegalArgumentException("Unsupported action: " + actionType);
        }
        return action;
    }

    private void register(BotAction action) {
        actions.put(action.name(), action);
    }

    public void abortAll(BotActionContext context) {
        GameUI gui = context.gui();
        stopCleanup(gui);
        stopFishing(gui);
        stopGrubGrub(gui);
        stopTarKiln(gui);
        stopRoasting(gui);
        stopCellar(gui);
        stopOceanScout(gui);
        pauseRoute(gui);
        haven.automated.AutoRepeatFlowerMenuScript.option = null;
        if (gui.autoRepeatFlowerMenuScriptThread != null) {
            gui.autoRepeatFlowerMenuScriptThread.interrupt();
            gui.autoRepeatFlowerMenuScriptThread = null;
        }
    }

    private void clickPrivateButton(Window window, String fieldName) throws Exception {
        Field field = window.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(window);
        if (value instanceof Button) {
            Button button = (Button) value;
            button.click();
            return;
        }
        throw new IllegalStateException("Expected button field " + fieldName);
    }

    private void ensureCleanupStarted(GameUI gui) throws Exception {
        if (gui.cleanupBot == null) {
            gui.cleanupBot = new CleanupBot(gui);
            gui.add(gui.cleanupBot, haven.Utils.getprefc("wndc-cleanupBotWindow", new haven.Coord(120, 120)));
            gui.cleanupThread = new Thread(gui.cleanupBot, "CleanupBot");
            gui.cleanupThread.start();
        }
        clickPrivateButton(gui.cleanupBot, "activeButton");
    }

    private void stopCleanup(GameUI gui) {
        if (gui.cleanupBot != null) {
            gui.cleanupBot.stop();
            gui.cleanupBot = null;
            gui.cleanupThread = null;
        }
    }

    private void ensureFishingStarted(GameUI gui) {
        gui.openFishingHelper();
        gui.fishingBot.startAutomation();
    }

    private void stopFishing(GameUI gui) {
        gui.fishingBot.stop();
        gui.fishingThread = null;
    }

    private void ensureGrubGrubStarted(GameUI gui) {
        if (gui.grubGrubBot == null) {
            gui.grubGrubBot = new GrubGrubBot(gui);
            gui.add(gui.grubGrubBot, haven.Utils.getprefc("wndc-grubGrubBotWindow", new haven.Coord(200, 200)));
            gui.grubGrubThread = new Thread(gui.grubGrubBot, "GrubGrubBot");
            gui.grubGrubThread.start();
        }
        clickFirstButton(gui.grubGrubBot);
    }

    private void stopGrubGrub(GameUI gui) {
        if (gui.grubGrubBot != null) {
            gui.grubGrubBot.stop();
            gui.grubGrubBot = null;
            gui.grubGrubThread = null;
        }
    }

    private void ensureTarKilnStarted(GameUI gui) throws Exception {
        if (gui.tarKilnCleanerBot == null) {
            gui.tarKilnCleanerBot = new TarKilnCleanerBot(gui);
            gui.add(gui.tarKilnCleanerBot, haven.Utils.getprefc("wndc-tarKilnCleanerBotWindow", new haven.Coord(240, 240)));
            gui.tarKilnCleanerThread = new Thread(gui.tarKilnCleanerBot, "TarKilnCleanerBot");
            gui.tarKilnCleanerThread.start();
        }
        setPrivateCheckBox(gui.tarKilnCleanerBot, "activeBox", true);
    }

    private void stopTarKiln(GameUI gui) {
        if (gui.tarKilnCleanerBot != null) {
            gui.tarKilnCleanerBot.stop();
            gui.tarKilnCleanerBot = null;
            gui.tarKilnCleanerThread = null;
        }
    }

    private void ensureRoastingStarted(GameUI gui) {
        if (gui.roastingSpitBot == null) {
            gui.roastingSpitBot = new RoastingSpitBot(gui);
            gui.add(gui.roastingSpitBot, haven.Utils.getprefc("wndc-roastingSpitBotWindow", new haven.Coord(280, 280)));
            gui.roastingSpitThread = new Thread(gui.roastingSpitBot, "RoastingSpitBot");
            gui.roastingSpitThread.start();
        }
        clickFirstButton(gui.roastingSpitBot);
    }

    private void stopRoasting(GameUI gui) {
        if (gui.roastingSpitBot != null) {
            gui.roastingSpitBot.stop();
            gui.roastingSpitBot = null;
            gui.roastingSpitThread = null;
        }
    }

    private void ensureCellarStarted(GameUI gui) throws Exception {
        if (gui.cellarDiggingBot == null) {
            gui.cellarDiggingBot = new CellarDiggingBot(gui);
            gui.add(gui.cellarDiggingBot, haven.Utils.getprefc("wndc-cellarDiggingBotWindow", new haven.Coord(320, 320)));
            gui.cellarDiggingThread = new Thread(gui.cellarDiggingBot, "CellarDiggingBot");
            gui.cellarDiggingThread.start();
        }
        clickPrivateButton(gui.cellarDiggingBot, "activeButton");
    }

    private void stopCellar(GameUI gui) {
        if (gui.cellarDiggingBot != null) {
            gui.cellarDiggingBot.stop();
            gui.cellarDiggingBot = null;
            gui.cellarDiggingThread = null;
        }
    }

    private void ensureOceanScoutStarted(GameUI gui) {
        if (gui.OceanScoutBot == null) {
            gui.OceanScoutBot = new OceanScoutBot(gui);
            gui.add(gui.OceanScoutBot, haven.Utils.getprefc("wndc-oceanScoutBotWindow", new haven.Coord(360, 360)));
            gui.oceanScoutBotThread = new Thread(gui.OceanScoutBot, "OceanScoutBot");
            gui.oceanScoutBotThread.start();
        }
        clickFirstButton(gui.OceanScoutBot);
    }

    private void stopOceanScout(GameUI gui) {
        if (gui.OceanScoutBot != null) {
            gui.OceanScoutBot.stop();
            gui.OceanScoutBot = null;
            gui.oceanScoutBotThread = null;
        }
    }

    private void pauseRoute(GameUI gui) {
        if (gui.map != null && gui.map.checkpointManager != null) {
            gui.map.checkpointManager.pauseIt();
        }
    }

    private void clickFirstButton(Widget root) {
        Button button = findFirstButton(root);
        if (button == null) {
            throw new IllegalStateException("No clickable button found.");
        }
        button.click();
    }

    private Button findFirstButton(Widget root) {
        for (Widget child : root.children()) {
            if (child instanceof Button) {
                return (Button) child;
            }
            Button nested = findFirstButton(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private void setPrivateCheckBox(Window window, String fieldName, boolean value) throws Exception {
        Field field = window.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object fieldValue = field.get(window);
        if (fieldValue instanceof CheckBox) {
            ((CheckBox) fieldValue).set(value);
            return;
        }
        throw new IllegalStateException("Expected checkbox field " + fieldName);
    }
}
