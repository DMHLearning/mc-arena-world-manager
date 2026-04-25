package dev.denismasterherobrine.arenaworldmanager;

import dev.denismasterherobrine.arenaworldmanager.command.ArenaCommand;
import dev.denismasterherobrine.arenaworldmanager.config.MapConfigRegistry;
import dev.denismasterherobrine.arenaworldmanager.runtime.RuntimeFlags;
import dev.denismasterherobrine.arenaworldmanager.service.EntityCleanerService;
import dev.denismasterherobrine.arenaworldmanager.service.FileSystemService;
import dev.denismasterherobrine.arenaworldmanager.service.SchematicService;
import dev.denismasterherobrine.arenaworldmanager.service.WorldGuardService;
import dev.denismasterherobrine.arenaworldmanager.supervisor.SupervisorRelayServer;
import dev.denismasterherobrine.arenaworldmanager.util.OperationLimiter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class ArenaWorldManagerPlugin extends JavaPlugin {

    private ArenaWorldManager api;
    private MapConfigRegistry mapRegistry;
    private SupervisorRelayServer relayServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Инициализация инфраструктуры
        OperationLimiter limiter = new OperationLimiter(3);
        SchematicService schematicService = new SchematicService(getDataFolder());
        WorldGuardService wgService = new WorldGuardService();
        FileSystemService fsService = new FileSystemService();
        EntityCleanerService cleaner = new EntityCleanerService(this);
        mapRegistry = new MapConfigRegistry(this);
        mapRegistry.reload();

        mapRegistry.discoverAndSaveSchematics(schematicService);

        this.api = new ArenaWorldManager(
                limiter, schematicService, wgService, fsService, cleaner,
                getServer().getWorldContainer().toPath(), mapRegistry
        );

        ArenaCommand arenaCommand = new ArenaCommand(this.api, mapRegistry);

        var command = getCommand("awm");
        if (command != null) {
            command.setExecutor(arenaCommand);
            command.setTabCompleter(arenaCommand);
        } else {
            getLogger().severe("Команда /awm не найдена в plugin.yml! Регистрация невозможна.");
        }

        FileConfiguration cfg = getConfig();

        if (cfg.getBoolean("supervisor-relay.enabled", false)) {
            String bindHost = cfg.getString("supervisor-relay.bind-host", "127.0.0.1");
            int port = cfg.getInt("supervisor-relay.port", 9850);
            String token = cfg.getString("supervisor-relay.bearer-token", "");
            relayServer = new SupervisorRelayServer(this, api, mapRegistry, bindHost, port, token);
            try {
                relayServer.start();
            } catch (IOException e) {
                getLogger().severe("Failed to start ArenaWorldManager supervisor relay: " + e.getMessage());
                relayServer = null;
            }
        } else {
            getLogger().info("ArenaWorldManager supervisor relay disabled in config.yml");
        }

        getLogger().info("ArenaWorldManager enabled with FAWE and WorldGuard support.");
    }

    @Override
    public void onDisable() {
        if (relayServer != null) {
            relayServer.stop();
        }
        RuntimeFlags.reset();
    }

    public ArenaWorldManager getApi() {
        return api;
    }

    public MapConfigRegistry getMapRegistry() {
        return mapRegistry;
    }
}
