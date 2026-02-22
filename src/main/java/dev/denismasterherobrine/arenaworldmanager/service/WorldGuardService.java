package dev.denismasterherobrine.arenaworldmanager.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.denismasterherobrine.arenaworldmanager.api.model.RegionDefinition;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public class WorldGuardService {
    public void createRegion(World world, RegionDefinition def) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        var manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) return;

        BoundingBox box = def.bounds();
        BlockVector3 min = BlockVector3.at(box.getMinX(), box.getMinY(), box.getMinZ());
        BlockVector3 max = BlockVector3.at(box.getMaxX(), box.getMaxY(), box.getMaxZ());

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(def.id(), min, max);
        region.setPriority(def.priority());

        // Применяем флаги из конфига
        def.flags().forEach((flagName, value) -> applyFlag(region, flagName, value));

        manager.addRegion(region);
    }

    @SuppressWarnings("unchecked")
    private void applyFlag(ProtectedCuboidRegion region, String name, Object value) {
        Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), name);
        if (flag != null) {
            // Если флаг типа State (allow/deny)
            if (flag instanceof StateFlag stateFlag) {
                region.setFlag(stateFlag, value.toString().equalsIgnoreCase("allow") ? StateFlag.State.ALLOW : StateFlag.State.DENY);
            } else {
                region.setFlag((Flag<Object>) flag, value);
            }
        }
    }
}