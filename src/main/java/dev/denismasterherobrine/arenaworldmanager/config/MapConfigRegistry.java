package dev.denismasterherobrine.arenaworldmanager.config;

import com.sk89q.worldedit.math.BlockVector3;
import dev.denismasterherobrine.arenaworldmanager.api.model.ArenaMapConfig;
import dev.denismasterherobrine.arenaworldmanager.api.model.RestorationMethod;
import dev.denismasterherobrine.arenaworldmanager.service.SchematicService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapConfigRegistry {

    private final File mapsFolder;
    private final Map<String, ArenaMapConfig> cache = new ConcurrentHashMap<>();

    public MapConfigRegistry(JavaPlugin plugin) {
        this.mapsFolder = new File(plugin.getDataFolder(), "maps");
        if (!mapsFolder.exists()) mapsFolder.mkdirs();
    }

    public void reload() {
        cache.clear();
        File[] files = mapsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            try {
                cache.put(id, ArenaMapConfig.fromYaml(id, yaml));
            } catch (Exception e) {
                System.err.println("Ошибка загрузки конфига карты " + id + ": " + e.getMessage());
            }
        }
    }

    public Optional<ArenaMapConfig> getConfig(String mapId) {
        return Optional.ofNullable(cache.get(mapId));
    }

    public Map<String, ArenaMapConfig> getAvailableConfigs() {
        return cache;
    }

    public void discoverAndSaveSchematics(SchematicService schematicService) {
        File schemFolder = schematicService.getSchematicsFolder();
        File mapsFolder = new File(schemFolder.getParentFile(), "maps");
        File[] files = schemFolder.listFiles((dir, name) -> name.endsWith(".schem"));

        if (files == null) return;

        BlockVector3 targetCenter = BlockVector3.at(0, 100, 0);

        for (File schemFile : files) {
            String mapName = schemFile.getName().replace(".schem", "");
            String configId = "auto_" + mapName;
            File configFile = new File(mapsFolder, configId + ".yml");

            // Если файл уже существует, просто загружаем его (не перезаписываем)
            if (configFile.exists()) continue;

            try {
                // Вычисляем границы автоматически
                BoundingBox bounds = schematicService.calculateBoundsCentered(schemFile.getName(), targetCenter);

                // Создаем структуру YAML
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("source-world", mapName); // Имя схематики без расширения
                yaml.set("method", RestorationMethod.SCHEMATIC_PASTE.name());

                var cb = yaml.createSection("cleanup-bounds");
                cb.set("x1", bounds.getMinX()); cb.set("y1", bounds.getMinY()); cb.set("z1", bounds.getMinZ());
                cb.set("x2", bounds.getMaxX()); cb.set("y2", bounds.getMaxY()); cb.set("z2", bounds.getMaxZ());

                yaml.set("cleanup-policy.items", true);
                yaml.set("cleanup-policy.mobs", true);

                List<Map<String, Object>> defaultRegions = new ArrayList<>();
                Map<String, Object> globalRegion = new LinkedHashMap<>();

                globalRegion.put("id", "arena_global");
                globalRegion.put("priority", 100);

                Map<String, String> flags = new LinkedHashMap<>();
                flags.put("build", "deny");
                flags.put("pvp", "deny");
                flags.put("mob-spawning", "allow");
                flags.put("item-pickup", "allow");
                flags.put("block-break", "deny");
                flags.put("block-place", "deny");
                flags.put("creeper-explosion", "deny");
                flags.put("other-explosion", "deny");
                flags.put("tnt", "deny");
                flags.put("fire-spread", "deny");
                flags.put("lava-flow", "deny");
                flags.put("water-flow", "deny");
                flags.put("interact", "deny");
                flags.put("ride", "deny");

                globalRegion.put("flags", flags);
                defaultRegions.add(globalRegion);

                yaml.set("regions", defaultRegions);

                // Сохраняем файл на диск
                yaml.save(configFile);

                // Добавляем в кэш
                cache.put(configId, ArenaMapConfig.fromYaml(configId, yaml));
                System.out.println("[AWM] Generated auto-config: " + configFile.getName());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
