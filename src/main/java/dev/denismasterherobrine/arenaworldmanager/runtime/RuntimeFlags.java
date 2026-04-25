package dev.denismasterherobrine.arenaworldmanager.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide runtime flags observed by {@link dev.denismasterherobrine.arenaworldmanager.ArenaWorldManager}
 * (resetArena / prepareArena / instantCleanup). All fields are volatile / thread-safe so
 * external chaos engines can flip them from any thread.
 *
 * <p>This class lives in a non-chaos package so that chaos plugins can manipulate it without
 * the source plugin having any compile-time dependency on chaos infrastructure.
 */
public final class RuntimeFlags {

    private RuntimeFlags() {}

    /** If the arena id is in this set, the next resetArena() fails once and the id is removed. */
    public static final Set<String> FAIL_NEXT_RESET = ConcurrentHashMap.newKeySet();
    /** If the arena id is in this set, the next prepareArena() fails once and the id is removed. */
    public static final Set<String> FAIL_NEXT_PREPARE = ConcurrentHashMap.newKeySet();
    /** Extra delay (ms) applied to every resetArena() invocation while &gt; 0. */
    public static volatile long extraResetDelayMs = 0L;
    /** Extra delay (ms) applied to every prepareArena() invocation while &gt; 0. */
    public static volatile long extraPrepareDelayMs = 0L;
    /** If true, all prepareArena() calls fail fast with "preparation paused". */
    public static volatile boolean prepareGateClosed = false;
    /** Arenas whose cleanup should be silently dropped once (to simulate stuck cleanup). */
    public static final Set<String> SKIP_NEXT_CLEANUP = ConcurrentHashMap.newKeySet();

    public static void reset() {
        FAIL_NEXT_RESET.clear();
        FAIL_NEXT_PREPARE.clear();
        SKIP_NEXT_CLEANUP.clear();
        extraResetDelayMs = 0L;
        extraPrepareDelayMs = 0L;
        prepareGateClosed = false;
    }
}
