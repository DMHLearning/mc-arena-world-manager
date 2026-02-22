package dev.denismasterherobrine.arenaworldmanager.service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

public class FileSystemService {

    /**
     * Асинхронно копирует папку мира.
     */
    public CompletableFuture<Void> copyFolder(Path source, Path target) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(target)) {
                    deleteFolder(target);
                }

                Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Files.createDirectories(target.resolve(source.relativize(dir)));
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                // Пропускаем лишние файлы сессий/локов
                                if (file.getFileName().toString().equals("session.lock") ||
                                        file.getFileName().toString().equals("uid.dat")) {
                                    return FileVisitResult.CONTINUE;
                                }
                                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to clone world folder", e);
            }
        });
    }

    public void deleteFolder(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
    }
}
