package dev.denismasterherobrine.arenaworldmanager.service;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CompletableFuture;

public class SchematicService {

    private final File schematicsFolder;

    public SchematicService(File dataFolder) {
        this.schematicsFolder = new File(dataFolder, "schematics");
        if (!schematicsFolder.exists()) schematicsFolder.mkdirs();
    }

    /**
     * Асинхронно вставляет схематику так, чтобы её геометрический центр
     * оказался ровно в заданных координатах (0, 100, 0).
     */
    public CompletableFuture<Void> pasteCentered(String fileName, World world, BlockVector3 targetCenter, boolean ignoreAir) {
        return CompletableFuture.runAsync(() -> {
            File file = new File(schematicsFolder, fileName);
            if (!file.exists()) throw new RuntimeException("Schematic not found: " + fileName);

            var format = ClipboardFormats.findByFile(file);
            if (format == null) throw new RuntimeException("Unknown schematic format");

            try (var reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();
                Region region = clipboard.getRegion();

                // Вычисляем смещение для центрирования
                BlockVector3 clipboardCenter = region.getCenter().toBlockPoint();
                BlockVector3 origin = clipboard.getOrigin();

                // Целевая точка вставки (коррекция оригинальной точки копирования)
                BlockVector3 pasteLocation = targetCenter.subtract(clipboardCenter).add(origin);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(pasteLocation)
                            .ignoreAirBlocks(ignoreAir)
                            .build();
                    Operations.complete(operation);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to paste schematic centered", e);
            }
        });
    }

    /**
     * Читает размеры схематики и возвращает BoundingBox, отцентрированный вокруг заданной точки.
     * Используется для авто-генерации конфигов.
     */
    public BoundingBox calculateBoundsCentered(String fileName, BlockVector3 targetCenter) {
        File file = new File(schematicsFolder, fileName);
        if (!file.exists()) throw new IllegalArgumentException("Schematic not found");

        var format = ClipboardFormats.findByFile(file);
        try (var reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            BlockVector3 dimensions = clipboard.getDimensions();

            // Половина размера по каждой оси
            double halfX = dimensions.x() / 2.0;
            double halfY = dimensions.y() / 2.0;
            double halfZ = dimensions.z() / 2.0;

            return new BoundingBox(
                    targetCenter.x() - halfX, targetCenter.y() - halfY, targetCenter.z() - halfZ,
                    targetCenter.x() + halfX, targetCenter.y() + halfY, targetCenter.z() + halfZ
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to read schematic dimensions", e);
        }
    }

    public File getSchematicsFolder() {
        return schematicsFolder;
    }
}
