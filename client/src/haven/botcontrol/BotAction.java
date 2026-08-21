package haven.botcontrol;

import org.json.JSONObject;

public interface BotAction {
    String name();

    void validate(JSONObject params, BotActionContext context);

    JSONObject start(JSONObject params, BotActionContext context) throws Exception;

    default JSONObject tick(BotActionContext context) {
        return new JSONObject();
    }

    default void cancel(BotActionContext context) {
    }

    default JSONObject snapshot(BotActionContext context) {
        return new JSONObject().put("name", name());
    }
}

