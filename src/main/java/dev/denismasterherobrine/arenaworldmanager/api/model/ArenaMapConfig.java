package dev.denismasterherobrine.arenaworldmanager.api.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация конкретной карты.
 */
public record ArenaMapConfig(
        String mapId,
        String sourceWorldName,
        RestorationMethod restorationMethod,
        BoundingBox cleanupBounds,
        EntityCleanupPolicy cleanupPolicy,
        List<RegionDefinition> regions
) {
    public static ArenaMapConfig fromYaml(String id, ConfigurationSection section) {
        return new ArenaMapConfig(
                id,
                section.getString("source-world"),
                RestorationMethod.valueOf(section.getString("method", "WORLD_CLONE")),
                parseBox(section.getConfigurationSection("cleanup-bounds")),
                parsePolicy(section.getConfigurationSection("cleanup-policy")),
                parseRegions(section.getList("regions"))
        );
    }

    private static BoundingBox parseBox(ConfigurationSection s) {
        if (s == null) return new BoundingBox();
        return new BoundingBox(s.getDouble("x1"), s.getDouble("y1"), s.getDouble("z1"),
                s.getDouble("x2"), s.getDouble("y2"), s.getDouble("z2"));
    }

    private static EntityCleanupPolicy parsePolicy(ConfigurationSection s) {
        if (s == null) return EntityCleanupPolicy.defaultPolicy();
        return new EntityCleanupPolicy(
                s.getBoolean("items", true), s.getBoolean("mobs", true),
                s.getBoolean("projectiles", true), s.getBoolean("vehicles", true)
        );
    }

    private static List<RegionDefinition> parseRegions(List<?> list) {
        // Упрощенная логика парсинга списка регионов
        return new ArrayList<>();
    }
}