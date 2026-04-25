package dev.denismasterherobrine.arenaworldmanager;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.arenaworldmanager.api.model.ArenaMapConfig;
import dev.denismasterherobrine.arenaworldmanager.api.model.EntityCleanupPolicy;
import dev.denismasterherobrine.arenaworldmanager.api.model.RegionDefinition;
import dev.denismasterherobrine.arenaworldmanager.api.model.RestorationMethod;
import dev.denismasterherobrine.arenaworldmanager.runtime.RuntimeFlags;
import dev.denismasterherobrine.arenaworldmanager.config.MapConfigRegistry;
import dev.denismasterherobrine.arenaworldmanager.event.ArenaResetCompletedEvent;
import dev.denismasterherobrine.arenaworldmanager.event.ArenaResetFailedEvent;
import dev.denismasterherobrine.arenaworldmanager.event.ArenaResetStartedEvent;
import dev.denismasterherobrine.arenaworldmanager.service.EntityCleanerService;
import dev.denismasterherobrine.arenaworldmanager.service.FileSystemService;
import dev.denismasterherobrine.arenaworldmanager.service.SchematicService;
import dev.denismasterherobrine.arenaworldmanager.service.WorldGuardService;
import dev.denismasterherobrine.arenaworldmanager.util.OperationLimiter;
import dev.denismasterherobrine.arenaworldmanager.util.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaWorldManager implements ArenaWorldAPI {

    private final OperationLimiter limiter;
    private final SchematicService schematicService;
    private final WorldGuardService worldGuardService;
    private final FileSystemService fileSystemService;
    private final EntityCleanerService entityCleaner;
    private final Path containerPath;

    private final Map<String, ArenaMapConfig> activeArenas = new ConcurrentHashMap<>();
    private final MapConfigRegistry registry;

    public ArenaWorldManager(OperationLimiter limiter, SchematicService schematicService,
                             WorldGuardService worldGuardService, FileSystemService fileSystemService,
                             EntityCleanerService entityCleaner, Path containerPath, MapConfigRegistry registry) {
        this.limiter = limiter;
        this.schematicService = schematicService;
        this.worldGuardService = worldGuardService;
        this.fileSystemService = fileSystemService;
        this.entityCleaner = entityCleaner;
        this.containerPath = containerPath;
        this.registry = registry;
    }

    @Override
    public Optional<ArenaMapConfig> getMapConfig(String mapId) {
        return registry.getConfig(mapId);
    }

    @Override
    public CompletableFuture<Void> prepareArena(String arenaId, ArenaMapConfig config) {
        if (RuntimeFlags.prepareGateClosed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("[chaos] prepareArena paused (prepareGateClosed=true)"));
        }
        if (RuntimeFlags.FAIL_NEXT_PREPARE.remove(arenaId)) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("[chaos] injected prepareArena failure for arena_id=" + arenaId));
        }
        long extraDelay = RuntimeFlags.extraPrepareDelayMs;
        CompletableFuture<Void> prep = limiter.submit(() -> {
            activeArenas.put(arenaId, config);
            if (config.restorationMethod() == RestorationMethod.WORLD_CLONE) {
                return restoreViaClone(arenaId, config);
            } else {
                return restoreViaSchematic(arenaId, config);
            }
        }).thenCompose(v -> applyPostRestore(arenaId, config));
        if (extraDelay > 0) {
            return prep.thenCompose(v -> sleepAsync(extraDelay));
        }
        return prep;
    }

    private static CompletableFuture<Void> sleepAsync(long ms) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
            f.complete(null);
        }, "awm-chaos-delay");
        t.setDaemon(true);
        t.start();
        return f;
    }

    private CompletableFuture<Void> restoreViaClone(String arenaId, ArenaMapConfig config) {
        Path source = containerPath.resolve(config.sourceWorldName());
        Path target = containerPath.resolve(arenaId);

        return fileSystemService.copyFolder(source, target).thenRun(() -> {
            // Регистрация мира в Bukkit после копирования файлов
            Bukkit.getGlobalRegionScheduler().execute(ArenaWorldManagerPlugin.getPlugin(ArenaWorldManagerPlugin.class), () -> {
                Bukkit.createWorld(new WorldCreator(arenaId));
            });
        });
    }

    private CompletableFuture<Void> restoreViaSchematic(String arenaId, ArenaMapConfig config) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getGlobalRegionScheduler().execute(ArenaWorldManagerPlugin.getPlugin(ArenaWorldManagerPlugin.class), () -> {
            WorldCreator creator = new WorldCreator(arenaId);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            World world = creator.createWorld();

            if (world == null) {
                future.completeExceptionally(new RuntimeException("Не удалось создать мир " + arenaId));
                return;
            }

            // Вставляем схематику центрированно
            String fileName = config.mapId().startsWith("auto_")
                    ? config.mapId().replace("auto_", "") + ".schem"
                    : config.sourceWorldName() + ".schem";

            schematicService.pasteCentered(fileName, world, com.sk89q.worldedit.math.BlockVector3.at(0, 100, 0), true)
                    .thenAccept(v -> future.complete(null))
                    .exceptionally(ex -> {
                        future.completeExceptionally(ex);
                        return null;
                    });
        });

        return future;
    }

    private CompletableFuture<Void> applyPostRestore(String arenaId, ArenaMapConfig config) {
        World world = Bukkit.getWorld(arenaId);
        if (world == null) return CompletableFuture.completedFuture(null);

        return entityCleaner.clean(world, config.cleanupBounds(), config.cleanupPolicy())
                .thenRun(() -> {
                    for (RegionDefinition def : config.regions()) {
                        String uniqueRegionId = arenaId + "_" + def.id();

                        // Пересоздаем определение региона с уникальным ID и границами из конфига
                        // Если границы региона в конфиге не заданы, используем cleanupBounds всей арены
                        BoundingBox box = def.bounds() != null ? def.bounds() : config.cleanupBounds();

                        RegionDefinition instanceDef = new RegionDefinition(
                                uniqueRegionId,
                                box,
                                def.priority(),
                                def.flags()
                        );

                        worldGuardService.createRegion(world, instanceDef);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> resetArena(String arenaId) {
        long startMs = System.currentTimeMillis();
        Bukkit.getPluginManager().callEvent(new ArenaResetStartedEvent(arenaId));

        if (RuntimeFlags.FAIL_NEXT_RESET.remove(arenaId)) {
            RuntimeException err = new RuntimeException(
                    "[chaos] injected resetArena failure for arena_id=" + arenaId);
            Bukkit.getPluginManager().callEvent(new ArenaResetFailedEvent(arenaId, err.getMessage()));
            return CompletableFuture.failedFuture(err);
        }

        return limiter.submit(() -> {
            activeArenas.remove(arenaId);
            CompletableFuture<Void> future = new CompletableFuture<>();

            Bukkit.getGlobalRegionScheduler().execute(ArenaWorldManagerPlugin.getPlugin(ArenaWorldManagerPlugin.class), () -> {
                World world = Bukkit.getWorld(arenaId);
                if (world != null) {
                    world.getPlayers().forEach(p -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));
                    Bukkit.unloadWorld(world, false);
                }

                try {
                    fileSystemService.deleteFolder(containerPath.resolve(arenaId));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                    return;
                }

                future.complete(null);
            });

            return future;
        }).whenComplete((v, ex) -> {
            long durationMs = System.currentTimeMillis() - startMs;
            if (ex != null) {
                Bukkit.getPluginManager().callEvent(new ArenaResetFailedEvent(arenaId, ex.getMessage()));
            } else {
                long extraDelay = RuntimeFlags.extraResetDelayMs;
                if (extraDelay > 0) {
                    try { Thread.sleep(extraDelay); } catch (InterruptedException ignored) {}
                }
                Bukkit.getPluginManager().callEvent(new ArenaResetCompletedEvent(arenaId, durationMs));
            }
        });
    }

    @Override
    public CompletableFuture<Void> instantCleanup(String arenaId) {
        if (RuntimeFlags.SKIP_NEXT_CLEANUP.remove(arenaId)) {
            return CompletableFuture.completedFuture(null);
        }

        World world = Bukkit.getWorld(arenaId);
        if (world == null) return CompletableFuture.completedFuture(null);

        ArenaMapConfig config = activeArenas.get(arenaId);

        if (config != null) {
            return entityCleaner.clean(world, config.cleanupBounds(), config.cleanupPolicy());
        }

        BoundingBox safeFallback = BoundingBox.of(world.getSpawnLocation().toVector(), 2, 2, 2);
        return entityCleaner.clean(world, safeFallback, EntityCleanupPolicy.defaultPolicy());
    }
}
