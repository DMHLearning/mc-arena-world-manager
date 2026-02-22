package dev.denismasterherobrine.arenaworldmanager.api;

import dev.denismasterherobrine.arenaworldmanager.api.model.ArenaMapConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Публичный интерфейс ArenaWorldManagerPlugin.
 */
public interface ArenaWorldAPI {

    /**
     * Получает конфигурацию карты по её ID.
     * @param mapId Уникальный идентификатор карты
     * @return Optional с конфигурацией карты, или пустой, если карта не найдена
     */
    Optional<ArenaMapConfig> getMapConfig(String mapId);

    /**
     * Полностью готовит арену к игре: восстанавливает ландшафт,
     * чистит мусор и расставляет регионы.
     * * @param arenaId Уникальный ID запущенного инстанса
     * @param config Настройки карты
     * @return Future, завершающееся при полной готовности
     */
    CompletableFuture<Void> prepareArena(String arenaId, ArenaMapConfig config);

    /**
     * Сбрасывает арену после игры.
     * * @param arenaId ID инстанса
     * @return Future, завершающееся после успешного сброса
     */
    CompletableFuture<Void> resetArena(String arenaId);

    /**
     * Удаляет только сущности в заданных границах (например, для очистки между волнами).
     * * @param arenaId ID инстанса
     * @return Future завершения очистки
     */
    CompletableFuture<Void> instantCleanup(String arenaId);
}