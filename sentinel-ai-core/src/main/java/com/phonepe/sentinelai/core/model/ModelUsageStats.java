package com.phonepe.sentinelai.core.model;

import lombok.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Model usage object.
 * Usage Notes:
 *  -
 */
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class ModelUsageStats {

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    @EqualsAndHashCode
    public static class PromptTokenDetails {
        private final AtomicInteger cachedTokens = new AtomicInteger(0);
        private final AtomicInteger audioTokens = new AtomicInteger(0);

        public int getCachedTokens() {
            return cachedTokens.get();
        }

        public int getAudioTokens() {
            return audioTokens.get();
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    @EqualsAndHashCode
    public static class ResponseTokenDetails {
        private final AtomicInteger reasoningTokens = new AtomicInteger(0);
        private final AtomicInteger acceptedPredictionTokens = new AtomicInteger(0);
        private final AtomicInteger rejectedPredictionTokens = new AtomicInteger(0);
        private final AtomicInteger audioTokens = new AtomicInteger(0);

        public int getReasoningTokens() {
            return reasoningTokens.get();
        }

        public int getAcceptedPredictionTokens() {
            return acceptedPredictionTokens.get();
        }

        public int getRejectedPredictionTokens() {
            return rejectedPredictionTokens.get();
        }

        public int getAudioTokens() {
            return audioTokens.get();
        }
    }
    private final AtomicInteger requestsForRun = new AtomicInteger(0);
    private final AtomicInteger toolCallsForRun = new AtomicInteger(0);
    private final AtomicInteger requestTokens = new AtomicInteger(0);
    private final AtomicInteger responseTokens = new AtomicInteger(0);
    private final AtomicInteger totalTokens = new AtomicInteger(0);
    private final PromptTokenDetails requestTokenDetails = new PromptTokenDetails();
    private final ResponseTokenDetails responseTokenDetails = new ResponseTokenDetails();
    private final Map<String, Integer> details = new ConcurrentHashMap<>();

    public int getRequestsForRun() {
        return requestsForRun.get();
    }

    public int getToolCallsForRun() {
        return toolCallsForRun.get();
    }

    public int getRequestTokens() {
        return requestTokens.get();
    }

    public int getResponseTokens() {
        return responseTokens.get();
    }

    public int getTotalTokens() {
        return totalTokens.get();
    }

    public Map<String, Integer> getDetails() {
        return Map.copyOf(details);
    }
    
    public ModelUsageStats incrementRequestsForRun() {
        return incrementRequestsForRun(1);
    }

    public ModelUsageStats incrementRequestsForRun(int value) {
        this.requestsForRun.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementToolCallsForRun() {
        return incrementToolCallsForRun(1);
    }

    public ModelUsageStats incrementToolCallsForRun(int value) {
        this.toolCallsForRun.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementRequestTokens(int value) {
        this.requestTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementResponseTokens(int value) {
        this.responseTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementTotalTokens(int value) {
        this.totalTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementRequestCachedTokens(int value) {
        this.requestTokenDetails.cachedTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementRequestAudioTokens(int value) {
        this.requestTokenDetails.audioTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementResponseReasoningTokens(int value) {
        this.responseTokenDetails.reasoningTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementResponseAcceptedPredictionTokens(int value) {
        this.responseTokenDetails.acceptedPredictionTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementResponseRejectedPredictionTokens(int value) {
        this.responseTokenDetails.rejectedPredictionTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats incrementResponseAudioTokens(int value) {
        this.responseTokenDetails.audioTokens.addAndGet(value);
        return this;
    }

    public ModelUsageStats addDetails(final Map<String, Integer> otherDetails) {
        if (null != otherDetails) {
            details.putAll(otherDetails);
        }
        return this;
    }

    public ModelUsageStats merge(final ModelUsageStats other) {
        if (null == other) {
            return this;
        }
        return this.incrementRequestsForRun(other.getRequestsForRun())
                .incrementToolCallsForRun(other.getToolCallsForRun())
                .incrementRequestTokens(other.getRequestTokens())
                .incrementResponseTokens(other.getResponseTokens())
                .incrementTotalTokens(other.getTotalTokens())
                .addDetails(other.getDetails())
                .incrementRequestCachedTokens(other.getRequestTokenDetails().getCachedTokens())
                .incrementRequestAudioTokens(other.getRequestTokenDetails().getAudioTokens())
                .incrementResponseReasoningTokens(other.getResponseTokenDetails().getReasoningTokens())
                .incrementResponseAcceptedPredictionTokens(other.getResponseTokenDetails().getAcceptedPredictionTokens())
                .incrementResponseRejectedPredictionTokens(other.getResponseTokenDetails().getRejectedPredictionTokens())
                .incrementResponseAudioTokens(other.getResponseTokenDetails().getAudioTokens());
    }
}
