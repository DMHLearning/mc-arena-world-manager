package dev.denismasterherobrine.arenaworldmanager.service;


import dev.denismasterherobrine.arenaworldmanager.api.model.EntityCleanupPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.concurrent.CompletableFuture;

public class EntityCleanerService {

    private final JavaPlugin plugin;

    public EntityCleanerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> clean(World world, BoundingBox bounds, EntityCleanupPolicy policy) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Используем глобальный планировщик Bukkit напрямую
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            int minX = (int) bounds.getMinX() >> 4;
            int maxX = (int) bounds.getMaxX() >> 4;
            int minZ = (int) bounds.getMinZ() >> 4;
            int maxZ = (int) bounds.getMaxZ() >> 4;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.isChunkLoaded(x, z)) continue;

                    Chunk chunk = world.getChunkAt(x, z);
                    for (Entity entity : chunk.getEntities()) {
                        if (bounds.contains(entity.getLocation().toVector()) && shouldRemove(entity, policy)) {
                            entity.remove();
                        }
                    }
                }
            }
            future.complete(null);
        });

        return future;
    }

    private boolean shouldRemove(Entity entity, EntityCleanupPolicy policy) {
        if (entity instanceof Player) return false;
        return (policy.removeItems() && entity instanceof Item) ||
                (policy.removeMonsters() && entity instanceof Monster && entity instanceof Animals && entity instanceof Bat) ||
                (policy.removeProjectiles() && entity instanceof Projectile) ||
                (policy.removeVehicles() && entity instanceof Vehicle);
    }
}