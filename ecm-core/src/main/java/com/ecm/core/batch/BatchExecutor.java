package com.ecm.core.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Lightweight generic batch executor for controller/service level batch operations.
 */
public final class BatchExecutor {

    private BatchExecutor() {
    }

    public static <I, R> RunResult<R> run(
        List<I> inputItems,
        Function<I, ItemResult<R>> handler,
        BiFunction<I, Exception, R> errorMapper
    ) {
        List<I> items = inputItems != null ? inputItems : List.of();
        List<R> results = new ArrayList<>(items.size());
        int succeeded = 0;
        int skipped = 0;
        int failed = 0;

        for (I item : items) {
            try {
                ItemResult<R> result = handler.apply(item);
                if (result == null) {
                    failed += 1;
                    results.add(errorMapper.apply(item, new IllegalStateException("Handler returned null")));
                    continue;
                }
                if (result.outcome() == Outcome.SUCCEEDED) {
                    succeeded += 1;
                } else if (result.outcome() == Outcome.SKIPPED) {
                    skipped += 1;
                } else {
                    failed += 1;
                }
                results.add(result.payload());
            } catch (Exception ex) {
                failed += 1;
                results.add(errorMapper.apply(item, ex));
            }
        }

        return new RunResult<>(
            items.size(),
            items.size(),
            succeeded,
            skipped,
            failed,
            results
        );
    }

    public static <I, R> RunResult<R> runParallel(
        List<I> inputItems,
        int workerCount,
        Function<I, ItemResult<R>> handler,
        BiFunction<I, Exception, R> errorMapper
    ) {
        List<I> items = inputItems != null ? inputItems : List.of();
        if (items.isEmpty()) {
            return new RunResult<>(0, 0, 0, 0, 0, List.of());
        }

        int boundedWorkers = Math.max(1, Math.min(workerCount, items.size()));
        if (boundedWorkers == 1) {
            return run(items, handler, errorMapper);
        }

        List<R> orderedResults = new ArrayList<>(Collections.nCopies(items.size(), null));
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(boundedWorkers);
        List<Future<Void>> futures = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            final int itemIndex = index;
            final I item = items.get(index);
            Callable<Void> task = () -> {
                R payload = executeItem(item, handler, errorMapper, succeeded, skipped, failed);
                orderedResults.set(itemIndex, payload);
                return null;
            };
            futures.add(executor.submit(task));
        }

        try {
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel batch execution interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Parallel batch execution failed", ex.getCause());
        } finally {
            executor.shutdownNow();
        }

        return new RunResult<>(
            items.size(),
            items.size(),
            succeeded.get(),
            skipped.get(),
            failed.get(),
            orderedResults
        );
    }

    private static <I, R> R executeItem(
        I item,
        Function<I, ItemResult<R>> handler,
        BiFunction<I, Exception, R> errorMapper,
        AtomicInteger succeeded,
        AtomicInteger skipped,
        AtomicInteger failed
    ) {
        try {
            ItemResult<R> result = handler.apply(item);
            if (result == null) {
                failed.incrementAndGet();
                return errorMapper.apply(item, new IllegalStateException("Handler returned null"));
            }

            if (result.outcome() == Outcome.SUCCEEDED) {
                succeeded.incrementAndGet();
            } else if (result.outcome() == Outcome.SKIPPED) {
                skipped.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }
            return result.payload();
        } catch (Exception ex) {
            failed.incrementAndGet();
            return errorMapper.apply(item, ex);
        }
    }

    public enum Outcome {
        SUCCEEDED,
        SKIPPED,
        FAILED
    }

    public record ItemResult<R>(
        Outcome outcome,
        R payload
    ) {
        public static <R> ItemResult<R> succeeded(R payload) {
            return new ItemResult<>(Outcome.SUCCEEDED, payload);
        }

        public static <R> ItemResult<R> skipped(R payload) {
            return new ItemResult<>(Outcome.SKIPPED, payload);
        }

        public static <R> ItemResult<R> failed(R payload) {
            return new ItemResult<>(Outcome.FAILED, payload);
        }
    }

    public record RunResult<R>(
        int requested,
        int processed,
        int succeeded,
        int skipped,
        int failed,
        List<R> results
    ) {
    }
}
