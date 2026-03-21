package dev.denismasterherobrine.arenaworldmanager.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ArenaResetFailedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final String reason;

    public ArenaResetFailedEvent(String arenaId, String reason) {
        this.arenaId = arenaId;
        this.reason = reason;
    }

    public String getArenaId() { return arenaId; }
    public String getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
