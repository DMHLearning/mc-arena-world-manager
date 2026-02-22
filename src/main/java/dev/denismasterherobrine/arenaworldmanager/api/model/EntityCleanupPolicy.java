package dev.denismasterherobrine.arenaworldmanager.api.model;

/**
 * Политика очистки сущностей на арене.
 */
public record EntityCleanupPolicy(
        boolean removeItems,
        boolean removeMonsters,
        boolean removeProjectiles,
        boolean removeVehicles
) {
    public static EntityCleanupPolicy defaultPolicy() {
        return new EntityCleanupPolicy(true, true, true, true);
    }
}