package dev.denismasterherobrine.arenaworldmanager;

import dev.denismasterherobrine.arenaworldmanager.command.ArenaCommand;
import dev.denismasterherobrine.arenaworldmanager.config.MapConfigRegistry;
import dev.denismasterherobrine.arenaworldmanager.service.EntityCleanerService;
import dev.denismasterherobrine.arenaworldmanager.service.FileSystemService;
import dev.denismasterherobrine.arenaworldmanager.service.SchematicService;
import dev.denismasterherobrine.arenaworldmanager.service.WorldGuardService;
import dev.denismasterherobrine.arenaworldmanager.util.OperationLimiter;
import org.bukkit.plugin.java.JavaPlugin;

public class ArenaWorldManagerPlugin extends JavaPlugin {

    private ArenaWorldManager api;

    @Override
    public void onEnable() {
        // Инициализация инфраструктуры
        OperationLimiter limiter = new OperationLimiter(3);
        SchematicService schematicService = new SchematicService(getDataFolder());
        WorldGuardService wgService = new WorldGuardService();
        FileSystemService fsService = new FileSystemService();
        EntityCleanerService cleaner = new EntityCleanerService(this);
        MapConfigRegistry registry = new MapConfigRegistry(this);
        registry.reload();

        registry.discoverAndSaveSchematics(schematicService);

        this.api = new ArenaWorldManager(
                limiter, schematicService, wgService, fsService, cleaner,
                getServer().getWorldContainer().toPath()
        );

        ArenaCommand arenaCommand = new ArenaCommand(this.api, registry);

        var command = getCommand("awm");
        if (command != null) {
            command.setExecutor(arenaCommand);
            command.setTabCompleter(arenaCommand);
        } else {
            getLogger().severe("Команда /awm не найдена в plugin.yml! Регистрация невозможна.");
        }

        getLogger().info("ArenaWorldManager enabled with FAWE and WorldGuard support.");
    }

    public ArenaWorldManager getApi() {
        return api;
    }
}