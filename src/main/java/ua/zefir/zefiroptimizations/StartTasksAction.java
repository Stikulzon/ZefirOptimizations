package ua.zefir.zefiroptimizations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StartTasksAction<E extends LivingEntity> extends RecursiveAction {
    private static final int THRESHOLD = 10;  // Adjust this threshold as necessary
    private final Map<Activity, Set<Task<? super E>>> map;
    private final long time;
    private final ServerWorld world;
    private final E entity;
    private final Set<Activity> possibleActivities;

    public StartTasksAction(Map<Activity, Set<Task<? super E>>> map, long time, ServerWorld world, E entity, Set<Activity> possibleActivities) {
        this.map = map;
        this.time = time;
        this.world = world;
        this.entity = entity;
        this.possibleActivities = possibleActivities;
    }

    @Override
    protected void compute() {
        if (map.size() <= THRESHOLD) {
            processTasks();
        } else {
            int mid = map.size() / 2;
            Map<Activity, Set<Task<? super E>>> firstHalf = map.entrySet().stream()
                    .limit(mid)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Map<Activity, Set<Task<? super E>>> secondHalf = map.entrySet().stream()
                    .skip(mid)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            invokeAll(
                    new StartTasksAction<>(firstHalf, time, world, entity, possibleActivities),
                    new StartTasksAction<>(secondHalf, time, world, entity, possibleActivities)
            );
        }
    }

    private void processTasks() {
        for (Map.Entry<Activity, Set<Task<? super E>>> entry : map.entrySet()) {
            Activity activity = entry.getKey();
            if (possibleActivities.contains(activity)) {
                for (Task<? super E> task : entry.getValue()) {
                    if (task.getStatus() == MultiTickTask.Status.STOPPED) {
                        task.tryStarting(world, entity, time);
                    }
                }
            }
        }
    }
}
