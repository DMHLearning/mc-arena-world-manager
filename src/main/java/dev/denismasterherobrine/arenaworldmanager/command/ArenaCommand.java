package dev.denismasterherobrine.arenaworldmanager.command;

import dev.denismasterherobrine.arenaworldmanager.api.ArenaWorldAPI;
import dev.denismasterherobrine.arenaworldmanager.config.MapConfigRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final ArenaWorldAPI api;
    private final MapConfigRegistry registry;

    public ArenaCommand(ArenaWorldAPI api, MapConfigRegistry registry) {
        this.api = api;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arena.worldmanager.admin")) {
            sender.sendMessage(Component.text("У вас нет прав!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "prepare" -> handlePrepare(sender, args);
            case "reset" -> handleReset(sender, args);
            case "cleanup" -> handleCleanup(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handlePrepare(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /awm prepare <arenaId> <templateId>", NamedTextColor.RED));
            return;
        }

        String arenaId = args[1];
        String templateId = args[2];

        registry.getConfig(templateId).ifPresentOrElse(config -> {
            sender.sendMessage(Component.text("Начата подготовка арены " + arenaId + "...", NamedTextColor.YELLOW));

            api.prepareArena(arenaId, config)
                    .thenRun(() -> sender.sendMessage(Component.text("Арена " + arenaId + " успешно подготовлена!", NamedTextColor.GREEN)))
                    .exceptionally(ex -> {
                        sender.sendMessage(Component.text("Ошибка при подготовке: " + ex.getMessage(), NamedTextColor.RED));
                        return null;
                    });
        }, () -> sender.sendMessage(Component.text("Шаблон карты '" + templateId + "' не найден!", NamedTextColor.RED)));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /awm reset <arenaId>", NamedTextColor.RED));
            return;
        }

        String arenaId = args[1];
        sender.sendMessage(Component.text("Сброс арены " + arenaId + "...", NamedTextColor.YELLOW));

        api.resetArena(arenaId)
                .thenRun(() -> sender.sendMessage(Component.text("Арена " + arenaId + " была успешно сброшена.", NamedTextColor.GREEN)))
                .exceptionally(ex -> {
                    sender.sendMessage(Component.text("Ошибка при сбросе: " + ex.getMessage(), NamedTextColor.RED));
                    return null;
                });
    }

    private void handleCleanup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /awm cleanup <arenaId>", NamedTextColor.RED));
            return;
        }

        String arenaId = args[1];
        api.instantCleanup(arenaId).thenRun(() ->
                sender.sendMessage(Component.text("Быстрая очистка арены " + arenaId + " завершена.", NamedTextColor.GREEN))
        );
    }

    private void handleReload(CommandSender sender) {
        registry.reload();
        sender.sendMessage(Component.text("Конфигурации карт успешно перезагружены!", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- ArenaWorldManager Help ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/awm prepare <id> <template> - Создать арену", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/awm reset <id> - Удалить/сбросить арену", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/awm cleanup <id> - Очистить сущности", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/awm reload - Перезагрузить конфиги карт", NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of("prepare", "reset", "cleanup", "reload")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("prepare")) {
            return registry.getAvailableConfigs().keySet().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}