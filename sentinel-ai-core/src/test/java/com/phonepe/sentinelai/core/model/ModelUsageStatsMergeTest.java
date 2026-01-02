package com.phonepe.sentinelai.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ModelUsageStatsMergeTest {

    @Test
    void mergeWithNullDoesNotChangeState() {
        var base = new ModelUsageStats()
                .incrementRequestsForRun(5)
                .incrementToolCallsForRun(2)
                .incrementRequestTokens(30)
                .incrementResponseTokens(40)
                .incrementTotalTokens(70)
                .addDetails(Map.of("a", 1, "b", 2));
        base.getRequestTokenDetails()
                .incrementCachedTokens(7)
                .incrementAudioTokens(3);
        base.getResponseTokenDetails()
                .incrementReasoningTokens(4)
                .incrementAcceptedPredictionTokens(5)
                .incrementRejectedPredictionTokens(6)
                .incrementAudioTokens(2);

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
                .incrementRequestsForRun(1)
                .incrementToolCallsForRun(1)
                .incrementRequestTokens(10)
                .incrementResponseTokens(20)
                .incrementTotalTokens(30)
                .addDetails(Map.of("k", 100));
        base.getRequestTokenDetails().incrementCachedTokens(2).incrementAudioTokens(3);
        base.getResponseTokenDetails().incrementReasoningTokens(4).incrementAcceptedPredictionTokens(5)
                .incrementRejectedPredictionTokens(6).incrementAudioTokens(7);

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
                () -> assertSame(mapBefore, base.getDetails()),
                () -> assertEquals(1, base.getDetails().size()),
                () -> assertEquals(100, base.getDetails().get("k"))
                 );
    }

    @Test
    void mergeAccumulatesAllScalarAndNestedFields() {
        var base = new ModelUsageStats()
                .incrementRequestsForRun(1)
                .incrementToolCallsForRun(2)
                .incrementRequestTokens(3)
                .incrementResponseTokens(4)
                .incrementTotalTokens(5)
                .addDetails(Map.of("a", 1, "b", 2));
        base.getRequestTokenDetails().incrementCachedTokens(6).incrementAudioTokens(7);
        base.getResponseTokenDetails().incrementReasoningTokens(8).incrementAcceptedPredictionTokens(9)
                .incrementRejectedPredictionTokens(10).incrementAudioTokens(11);

        var other = new ModelUsageStats()
                .incrementRequestsForRun(10)
                .incrementToolCallsForRun(20)
                .incrementRequestTokens(30)
                .incrementResponseTokens(40)
                .incrementTotalTokens(50)
                .addDetails(Map.of("b", 200, "c", 3));
        other.getRequestTokenDetails().incrementCachedTokens(60).incrementAudioTokens(70);
        other.getResponseTokenDetails().incrementReasoningTokens(80).incrementAcceptedPredictionTokens(90)
                .incrementRejectedPredictionTokens(100).incrementAudioTokens(110);

        var mapIdentityBefore = base.getDetails();

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
                () -> assertSame(mapIdentityBefore, base.getDetails()),
                () -> assertEquals(3, base.getDetails().size()),
                () -> assertEquals(1, base.getDetails().get("a")),
                () -> assertEquals(200, base.getDetails().get("b")),
                () -> assertEquals(3, base.getDetails().get("c"))
                 );
    }

    @Test
    void mergeInitializesDetailsWhenNull() {
        var base = new ModelUsageStats(); // details is null initially
        var other = new ModelUsageStats().addDetails(Map.of("x", 1, "y", 2));

        assertNull(base.getDetails());
        base.merge(other);

        assertNotNull(base.getDetails());
        assertAll(
                () -> assertEquals(2, base.getDetails().size()),
                () -> assertEquals(1, base.getDetails().get("x")),
                () -> assertEquals(2, base.getDetails().get("y"))
                 );
    }

    @Test
    void mergeDoesNotMutateOther() {
        var other = new ModelUsageStats()
                .incrementRequestsForRun(3)
                .incrementToolCallsForRun(4)
                .incrementRequestTokens(50)
                .incrementResponseTokens(60)
                .incrementTotalTokens(110)
                .addDetails(Map.of("m", 7, "n", 8));
        other.getRequestTokenDetails().incrementCachedTokens(9).incrementAudioTokens(10);
        other.getResponseTokenDetails().incrementReasoningTokens(11).incrementAcceptedPredictionTokens(12)
                .incrementRejectedPredictionTokens(13).incrementAudioTokens(14);

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
        var a = new ModelUsageStats().incrementRequestsForRun(1);
        var b = new ModelUsageStats().incrementRequestsForRun(2);

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

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads,
                                                                     Runtime.getRuntime().availableProcessors()));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    var other = new ModelUsageStats()
                            .incrementRequestsForRun(1)
                            .incrementToolCallsForRun(2)
                            .incrementRequestTokens(3)
                            .incrementResponseTokens(4)
                            .incrementTotalTokens(7)
                            .addDetails(Map.of("k" + index, index));
                    other.getRequestTokenDetails().incrementCachedTokens(5).incrementAudioTokens(6);
                    other.getResponseTokenDetails().incrementReasoningTokens(8).incrementAcceptedPredictionTokens(9)
                            .incrementRejectedPredictionTokens(10).incrementAudioTokens(11);

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
                .incrementRequestsForRun(10)
                .incrementToolCallsForRun(10)
                .incrementRequestTokens(100)
                .incrementResponseTokens(100)
                .incrementTotalTokens(200)
                .addDetails(Map.of("x", 1));
        base.getRequestTokenDetails().incrementCachedTokens(10).incrementAudioTokens(10);
        base.getResponseTokenDetails().incrementReasoningTokens(10).incrementAcceptedPredictionTokens(10)
                .incrementRejectedPredictionTokens(10).incrementAudioTokens(10);

        var other = new ModelUsageStats()
                .incrementRequestsForRun(-3)
                .incrementToolCallsForRun(-2)
                .incrementRequestTokens(-10)
                .incrementResponseTokens(-20)
                .incrementTotalTokens(-30);
        other.getRequestTokenDetails().incrementCachedTokens(-4).incrementAudioTokens(-5);
        other.getResponseTokenDetails().incrementReasoningTokens(-6).incrementAcceptedPredictionTokens(-7)
                .incrementRejectedPredictionTokens(-8).incrementAudioTokens(-9);

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