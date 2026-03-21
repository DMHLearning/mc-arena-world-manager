package dev.denismasterherobrine.arenaworldmanager.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ArenaResetStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String arenaId;

    public ArenaResetStartedEvent(String arenaId) {
        this.arenaId = arenaId;
    }

    public String getArenaId() { return arenaId; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
