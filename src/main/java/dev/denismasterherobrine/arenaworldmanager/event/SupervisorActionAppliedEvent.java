package dev.denismasterherobrine.arenaworldmanager.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Fired by SupervisorRelayServer after a supervisor action is applied on this
 * plugin. Used by ChaosEngine to short-circuit the safety TTL.
 */
public final class SupervisorActionAppliedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String commandId;
    private final String actionType;
    private final String target;
    private final boolean success;
    private final String message;
    private final Map<String, String> parameters;

    public SupervisorActionAppliedEvent(String commandId, String actionType, String target,
                                        boolean success, String message, Map<String, String> parameters) {
        this.commandId = commandId == null ? "" : commandId;
        this.actionType = actionType == null ? "" : actionType;
        this.target = target == null ? "" : target;
        this.success = success;
        this.message = message == null ? "" : message;
        this.parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(parameters);
    }

    public String getCommandId() { return commandId; }
    public String getActionType() { return actionType; }
    public String getTarget() { return target; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Map<String, String> getParameters() { return parameters; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
