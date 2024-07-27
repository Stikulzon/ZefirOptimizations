package ua.zefir.zefiroptimizations;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolManager {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private ThreadPoolManager() {
        // Private constructor to prevent instantiation
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static void shutdown() {
        executorService.shutdown();
    }
}

