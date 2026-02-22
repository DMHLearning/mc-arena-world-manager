package dev.denismasterherobrine.arenaworldmanager.api.model;

/**
 * Метод восстановления арены к исходному состоянию.
 */
public enum RestorationMethod {
    /** Полное копирование папки мира. Самый надежный для больших изменений. */
    WORLD_CLONE,
    /** Вставка структуры через WorldEdit/FAWE. Быстрее для маленьких арен. */
    SCHEMATIC_PASTE
}