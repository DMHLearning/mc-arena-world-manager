package dev.denismasterherobrine.arenaworldmanager.api.model;

import org.bukkit.util.BoundingBox;

import java.util.Map;

/**
 * Определение региона WorldGuard, который нужно создать при подготовке.
 */
public record RegionDefinition(
        String id,
        BoundingBox bounds,
        int priority,
        Map<String, Object> flags // Флаги в формате "pvp: deny", "mob-spawning: allow"
) {}
