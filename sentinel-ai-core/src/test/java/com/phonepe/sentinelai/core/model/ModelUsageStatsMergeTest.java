package com.phonepe.sentinelai.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ModelUsageStatsMergeTest {

    @Test
    void mergeWithNullDoesNotChangeState() {
        var base = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(5)
                .incrementToolCallsForRunUnsafe(2)
                .incrementRequestTokensUnsafe(30)
                .incrementResponseTokensUnsafe(40)
                .incrementTotalTokensUnsafe(70)
                .addDetailsUnsafe(Map.of("a", 1, "b", 2))
                .incrementRequestCachedTokensUnsafe(7)
                .incrementRequestAudioTokensUnsafe(3)
                .incrementResponseReasoningTokensUnsafe(4)
                .incrementResponseAcceptedPredictionTokensUnsafe(5)
                .incrementResponseRejectedPredictionTokensUnsafe(6)
                .incrementResponseAudioTokensUnsafe(2);

        var detailsBefore = new HashMap<>(base.getDetails());
        var ret = base.merge(null);

        assertAll(
                () -> assertSame(base, ret),
                () -> assertEquals(5, base.getRequestsForRun()),
                () -> assertEquals(2, base.getToolCallsForRun()),
                () -> assertEquals(30, base.getRequestTokens()),
                () -> assertEquals(40, base.getResponseTokens()),
                () -> assertEquals(70, base.getTotalTokens()),
                () -> assertEquals(7, base.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(3, base.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(4, base.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(5, base.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(6, base.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(2, base.getResponseTokenDetails().getAudioTokens()),
                () -> assertEquals(detailsBefore, base.getDetails())
                 );
    }

    @Test
    void mergeWithDefaultOtherDoesNotChangeState() {
        var base = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(1)
                .incrementToolCallsForRunUnsafe(1)
                .incrementRequestTokensUnsafe(10)
                .incrementResponseTokensUnsafe(20)
                .incrementTotalTokensUnsafe(30)
                .addDetailsUnsafe(Map.of("k", 100))
                .incrementRequestCachedTokensUnsafe(2)
                .incrementRequestAudioTokensUnsafe(3)
                .incrementResponseReasoningTokensUnsafe(4)
                .incrementResponseAcceptedPredictionTokensUnsafe(5)
                .incrementResponseRejectedPredictionTokensUnsafe(6)
                .incrementResponseAudioTokensUnsafe(7);

        var other = new ModelUsageStats(); // all zeros, details null

        var mapBefore = base.getDetails();
        var ret = base.merge(other);

        assertAll(
                () -> assertSame(base, ret),
                () -> assertEquals(1, base.getRequestsForRun()),
                () -> assertEquals(1, base.getToolCallsForRun()),
                () -> assertEquals(10, base.getRequestTokens()),
                () -> assertEquals(20, base.getResponseTokens()),
                () -> assertEquals(30, base.getTotalTokens()),
                () -> assertEquals(2, base.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(3, base.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(4, base.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(5, base.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(6, base.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(7, base.getResponseTokenDetails().getAudioTokens()),
                () -> assertEquals(mapBefore, base.getDetails()),
                () -> assertEquals(1, base.getDetails().size()),
                () -> assertEquals(100, base.getDetails().get("k"))
                 );
    }

    @Test
    void mergeAccumulatesAllScalarAndNestedFields() {
        var base = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(1)
                .incrementToolCallsForRunUnsafe(2)
                .incrementRequestTokensUnsafe(3)
                .incrementResponseTokensUnsafe(4)
                .incrementTotalTokensUnsafe(5)
                .addDetailsUnsafe(Map.of("a", 1, "b", 2))
                .incrementRequestCachedTokensUnsafe(6)
                .incrementRequestAudioTokensUnsafe(7)
                .incrementResponseReasoningTokensUnsafe(8)
                .incrementResponseAcceptedPredictionTokensUnsafe(9)
                .incrementResponseRejectedPredictionTokensUnsafe(10)
                .incrementResponseAudioTokensUnsafe(11);

        var other = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(10)
                .incrementToolCallsForRunUnsafe(20)
                .incrementRequestTokensUnsafe(30)
                .incrementResponseTokensUnsafe(40)
                .incrementTotalTokensUnsafe(50)
                .addDetailsUnsafe(Map.of("b", 200, "c", 3))
                .incrementRequestCachedTokensUnsafe(60)
                .incrementRequestAudioTokensUnsafe(70)
                .incrementResponseReasoningTokensUnsafe(80)
                .incrementResponseAcceptedPredictionTokensUnsafe(90)
                .incrementResponseRejectedPredictionTokensUnsafe(100)
                .incrementResponseAudioTokensUnsafe(110);

        final var outputMap = Map.of("a", 1, "b", 200, "c", 3);

        base.merge(other);

        assertAll(
                () -> assertEquals(11, base.getRequestsForRun()),
                () -> assertEquals(22, base.getToolCallsForRun()),
                () -> assertEquals(33, base.getRequestTokens()),
                () -> assertEquals(44, base.getResponseTokens()),
                () -> assertEquals(55, base.getTotalTokens()),
                () -> assertEquals(66, base.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(77, base.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(88, base.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(99, base.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(110, base.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(121, base.getResponseTokenDetails().getAudioTokens()),
                () -> assertEquals(outputMap, base.getDetails()),
                () -> assertEquals(3, base.getDetails().size()),
                () -> assertEquals(1, base.getDetails().get("a")),
                () -> assertEquals(200, base.getDetails().get("b")),
                () -> assertEquals(3, base.getDetails().get("c"))
                 );
    }

    @Test
    void mergeInitializesDetailsWhenNull() {
        var base = new ModelUsageStats(); // details is null initially
        var other = new ModelUsageStats().addDetailsUnsafe(Map.of("x", 1, "y", 2));

        assertTrue(base.getDetails().isEmpty());
        base.merge(other);

        assertFalse(base.getDetails().isEmpty());
        assertAll(
                () -> assertEquals(2, base.getDetails().size()),
                () -> assertEquals(1, base.getDetails().get("x")),
                () -> assertEquals(2, base.getDetails().get("y"))
                 );
    }

    @Test
    void mergeDoesNotMutateOther() {
        var other = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(3)
                .incrementToolCallsForRunUnsafe(4)
                .incrementRequestTokensUnsafe(50)
                .incrementResponseTokensUnsafe(60)
                .incrementTotalTokensUnsafe(110)
                .addDetailsUnsafe(Map.of("m", 7, "n", 8))
                .incrementRequestCachedTokensUnsafe(9)
                .incrementRequestAudioTokensUnsafe(10)
                .incrementResponseReasoningTokensUnsafe(11)
                .incrementResponseAcceptedPredictionTokensUnsafe(12)
                .incrementResponseRejectedPredictionTokensUnsafe(13)
                .incrementResponseAudioTokensUnsafe(14);

        var otherSnapshot = snapshot(other);

        var base = new ModelUsageStats();
        base.merge(other);

        assertAll(
                () -> assertEquals(otherSnapshot.requestsForRun, other.getRequestsForRun()),
                () -> assertEquals(otherSnapshot.toolCallsForRun, other.getToolCallsForRun()),
                () -> assertEquals(otherSnapshot.requestTokens, other.getRequestTokens()),
                () -> assertEquals(otherSnapshot.responseTokens, other.getResponseTokens()),
                () -> assertEquals(otherSnapshot.totalTokens, other.getTotalTokens()),
                () -> assertEquals(otherSnapshot.promptCachedTokens, other.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(otherSnapshot.promptAudioTokens, other.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(otherSnapshot.responseReasoningTokens,
                                   other.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(otherSnapshot.responseAcceptedPredictionTokens,
                                   other.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(otherSnapshot.responseRejectedPredictionTokens,
                                   other.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(otherSnapshot.responseAudioTokens, other.getResponseTokenDetails().getAudioTokens()),
                () -> assertEquals(otherSnapshot.details, other.getDetails())
                 );
    }

    @Test
    void mergeReturnsThisAndSupportsChaining() {
        var base = new ModelUsageStats();
        var a = new ModelUsageStats().incrementRequestsForRunUnsafe(1);
        var b = new ModelUsageStats().incrementRequestsForRunUnsafe(2);

        var ret = base.merge(a).merge(b);

        assertAll(
                () -> assertSame(base, ret),
                () -> assertEquals(3, base.getRequestsForRun())
                 );
    }

    @Test
    void mergeIsSynchronizedForConcurrentMerges() throws InterruptedException {
        var base = new ModelUsageStats();
        int threads = 20;

        final var pool = Executors.newFixedThreadPool(Math.min(threads,
                                                               Runtime.getRuntime().availableProcessors()));
        final var start = new CountDownLatch(1);
        final var done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    var other = new ModelUsageStats()
                            .safeUpdate(obj -> obj
                                    .incrementRequestsForRunUnsafe(1)
                                    .incrementToolCallsForRunUnsafe(2)
                                    .incrementRequestTokensUnsafe(3)
                                    .incrementResponseTokensUnsafe(4)
                                    .incrementTotalTokensUnsafe(7)
                                    .addDetailsUnsafe(Map.of("k" + index, index))
                                    .incrementRequestCachedTokensUnsafe(5)
                                    .incrementRequestAudioTokensUnsafe(6)
                                    .incrementResponseReasoningTokensUnsafe(8)
                                    .incrementResponseAcceptedPredictionTokensUnsafe(9)
                                    .incrementResponseRejectedPredictionTokensUnsafe(10)
                                    .incrementResponseAudioTokensUnsafe(11));

                    start.await();
                    base.merge(other);
                }
                catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertAll(
                () -> assertEquals(threads * 1, base.getRequestsForRun()),
                () -> assertEquals(threads * 2, base.getToolCallsForRun()),
                () -> assertEquals(threads * 3, base.getRequestTokens()),
                () -> assertEquals(threads * 4, base.getResponseTokens()),
                () -> assertEquals(threads * 7, base.getTotalTokens()),
                () -> assertEquals(threads * 5, base.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(threads * 6, base.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(threads * 8, base.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(threads * 9, base.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(threads * 10, base.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(threads * 11, base.getResponseTokenDetails().getAudioTokens()),
                () -> assertNotNull(base.getDetails()),
                () -> assertEquals(threads, base.getDetails().size())
                 );

        for (int i = 0; i < threads; i++) {
            assertEquals(i, base.getDetails().get("k" + i));
        }
    }

    @Test
    void mergeSupportsNegativeValuesAsAdditiveBehavior() {
        var base = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(10)
                .incrementToolCallsForRunUnsafe(10)
                .incrementRequestTokensUnsafe(100)
                .incrementResponseTokensUnsafe(100)
                .incrementTotalTokensUnsafe(200)
                .addDetailsUnsafe(Map.of("x", 1))
                .incrementRequestCachedTokensUnsafe(10)
                .incrementRequestAudioTokensUnsafe(10)
                .incrementResponseReasoningTokensUnsafe(10)
                .incrementResponseAcceptedPredictionTokensUnsafe(10)
                .incrementResponseRejectedPredictionTokensUnsafe(10)
                .incrementResponseAudioTokensUnsafe(10);

        var other = new ModelUsageStats()
                .incrementRequestsForRunUnsafe(-3)
                .incrementToolCallsForRunUnsafe(-2)
                .incrementRequestTokensUnsafe(-10)
                .incrementResponseTokensUnsafe(-20)
                .incrementTotalTokensUnsafe(-30)
                .incrementRequestCachedTokensUnsafe(-4)
                .incrementRequestAudioTokensUnsafe(-5)
                .incrementResponseReasoningTokensUnsafe(-6)
                .incrementResponseAcceptedPredictionTokensUnsafe(-7)
                .incrementResponseRejectedPredictionTokensUnsafe(-8)
                .incrementResponseAudioTokensUnsafe(-9);

        base.merge(other);

        assertAll(
                () -> assertEquals(7, base.getRequestsForRun()),
                () -> assertEquals(8, base.getToolCallsForRun()),
                () -> assertEquals(90, base.getRequestTokens()),
                () -> assertEquals(80, base.getResponseTokens()),
                () -> assertEquals(170, base.getTotalTokens()),
                () -> assertEquals(6, base.getRequestTokenDetails().getCachedTokens()),
                () -> assertEquals(5, base.getRequestTokenDetails().getAudioTokens()),
                () -> assertEquals(4, base.getResponseTokenDetails().getReasoningTokens()),
                () -> assertEquals(3, base.getResponseTokenDetails().getAcceptedPredictionTokens()),
                () -> assertEquals(2, base.getResponseTokenDetails().getRejectedPredictionTokens()),
                () -> assertEquals(1, base.getResponseTokenDetails().getAudioTokens())
                 );
    }

    private static Snapshot snapshot(ModelUsageStats s) {
        var snap = new Snapshot();
        snap.requestsForRun = s.getRequestsForRun();
        snap.toolCallsForRun = s.getToolCallsForRun();
        snap.requestTokens = s.getRequestTokens();
        snap.responseTokens = s.getResponseTokens();
        snap.totalTokens = s.getTotalTokens();
        snap.promptCachedTokens = s.getRequestTokenDetails().getCachedTokens();
        snap.promptAudioTokens = s.getRequestTokenDetails().getAudioTokens();
        snap.responseReasoningTokens = s.getResponseTokenDetails().getReasoningTokens();
        snap.responseAcceptedPredictionTokens = s.getResponseTokenDetails().getAcceptedPredictionTokens();
        snap.responseRejectedPredictionTokens = s.getResponseTokenDetails().getRejectedPredictionTokens();
        snap.responseAudioTokens = s.getResponseTokenDetails().getAudioTokens();
        snap.details = s.getDetails() == null ? null : new HashMap<>(s.getDetails());
        return snap;
    }

    private static class Snapshot {
        int requestsForRun;
        int toolCallsForRun;
        int requestTokens;
        int responseTokens;
        int totalTokens;
        int promptCachedTokens;
        int promptAudioTokens;
        int responseReasoningTokens;
        int responseAcceptedPredictionTokens;
        int responseRejectedPredictionTokens;
        int responseAudioTokens;
        Map<String, Integer> details;
    }


}