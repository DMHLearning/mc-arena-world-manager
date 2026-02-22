package dev.denismasterherobrine.arenaworldmanager.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Ограничитель параллельных операций, чтобы избежать перегрузки I/O.
 */
public class OperationLimiter {
    private final Semaphore semaphore;

    public OperationLimiter(int maxParallelTasks) {
        this.semaphore = new Semaphore(maxParallelTasks);
    }

    public <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> task) {
        return CompletableFuture.runAsync(() -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        }).thenCompose(v -> task.get()).whenComplete((res, ex) -> semaphore.release());
    }
}
