package ua.zefir.zefiroptimizations;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;
import java.util.concurrent.*;

public class BrainModified<E extends LivingEntity> {
    private final Multimap<Activity, Task<? super E>> taskMap = HashMultimap.create();
    private final Set<Activity> possibleActivities = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public void startTasks(ServerWorld world, E entity) {
        long l = world.getTime();

        taskMap.asMap().entrySet().parallelStream()
                .filter(entry -> possibleActivities.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().parallelStream())
                .filter(task -> task.getStatus() == MultiTickTask.Status.STOPPED)
                .forEach(task -> executorService.submit(() -> task.tryStarting(world, entity, l)));
    }

    // Ensure to shut down the executor service when it is no longer needed
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
