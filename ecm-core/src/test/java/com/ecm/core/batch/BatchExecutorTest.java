package com.ecm.core.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchExecutorTest {

    @Test
    @DisplayName("Batch executor aggregates succeeded skipped and failed outcomes")
    void runShouldAggregateOutcomes() {
        BatchExecutor.RunResult<String> result = BatchExecutor.run(
            List.of("ok", "skip", "bad"),
            item -> {
                if ("ok".equals(item)) {
                    return BatchExecutor.ItemResult.succeeded("OK");
                }
                if ("skip".equals(item)) {
                    return BatchExecutor.ItemResult.skipped("SKIP");
                }
                return BatchExecutor.ItemResult.failed("FAIL");
            },
            (item, ex) -> "ERROR"
        );

        assertEquals(3, result.requested());
        assertEquals(3, result.processed());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(List.of("OK", "SKIP", "FAIL"), result.results());
    }

    @Test
    @DisplayName("Batch executor maps exceptions to failed payload")
    void runShouldMapExceptions() {
        BatchExecutor.RunResult<String> result = BatchExecutor.run(
            List.of("boom"),
            item -> {
                throw new IllegalStateException("broken");
            },
            (item, ex) -> item + ":" + ex.getClass().getSimpleName()
        );

        assertEquals(0, result.succeeded());
        assertEquals(0, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(List.of("boom:IllegalStateException"), result.results());
    }

    @Test
    @DisplayName("Parallel batch executor keeps output order while aggregating outcomes")
    void runParallelShouldKeepOrderAndAggregate() {
        List<Integer> input = List.of(1, 2, 3, 4, 5, 6);
        BatchExecutor.RunResult<String> result = BatchExecutor.runParallel(
            input,
            4,
            item -> {
                try {
                    TimeUnit.MILLISECONDS.sleep((7 - item) * 10L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return BatchExecutor.ItemResult.failed("INTERRUPTED:" + item);
                }
                if (item == 3) {
                    return BatchExecutor.ItemResult.skipped("SKIP:" + item);
                }
                if (item == 5) {
                    return BatchExecutor.ItemResult.failed("FAIL:" + item);
                }
                return BatchExecutor.ItemResult.succeeded("OK:" + item);
            },
            (item, ex) -> "ERROR:" + item
        );

        assertEquals(6, result.requested());
        assertEquals(6, result.processed());
        assertEquals(4, result.succeeded());
        assertEquals(1, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(
            List.of("OK:1", "OK:2", "SKIP:3", "OK:4", "FAIL:5", "OK:6"),
            result.results()
        );
    }

    @Test
    @DisplayName("Parallel executor falls back correctly for single worker")
    void runParallelSingleWorkerShouldBehaveLikeSequential() {
        List<String> inputs = List.of("ok", "skip", "fail");
        BatchExecutor.RunResult<String> sequential = BatchExecutor.run(
            inputs,
            item -> mapItem(item),
            (item, ex) -> "ERROR:" + item
        );
        BatchExecutor.RunResult<String> parallel = BatchExecutor.runParallel(
            inputs,
            1,
            item -> mapItem(item),
            (item, ex) -> "ERROR:" + item
        );

        assertEquals(sequential.requested(), parallel.requested());
        assertEquals(sequential.processed(), parallel.processed());
        assertEquals(sequential.succeeded(), parallel.succeeded());
        assertEquals(sequential.skipped(), parallel.skipped());
        assertEquals(sequential.failed(), parallel.failed());
        assertEquals(sequential.results(), parallel.results());
    }

    @Test
    @DisplayName("Parallel batch executor maps handler exceptions")
    void runParallelShouldMapExceptions() {
        List<String> items = List.of("ok", "boom", "ok2");
        BatchExecutor.RunResult<String> result = BatchExecutor.runParallel(
            items,
            3,
            item -> {
                if ("boom".equals(item)) {
                    throw new IllegalArgumentException("bad");
                }
                return BatchExecutor.ItemResult.succeeded("OK:" + item);
            },
            (item, ex) -> "ERROR:" + item + ":" + ex.getClass().getSimpleName()
        );

        assertEquals(2, result.succeeded());
        assertEquals(0, result.skipped());
        assertEquals(1, result.failed());
        assertTrue(result.results().contains("ERROR:boom:IllegalArgumentException"));
    }

    private BatchExecutor.ItemResult<String> mapItem(String item) {
        if ("ok".equals(item)) {
            return BatchExecutor.ItemResult.succeeded("OK");
        }
        if ("skip".equals(item)) {
            return BatchExecutor.ItemResult.skipped("SKIP");
        }
        return BatchExecutor.ItemResult.failed("FAIL");
    }
}
