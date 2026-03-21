package dev.denismasterherobrine.arenaworldmanager.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ArenaResetCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;
    private final long durationMs;

    public ArenaResetCompletedEvent(String arenaId, long durationMs) {
        this.arenaId = arenaId;
        this.durationMs = durationMs;
    }

    public String getArenaId() { return arenaId; }
    public long getDurationMs() { return durationMs; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
