package com.phonepe.sentinelai.session;

import lombok.Builder;
import lombok.Value;

/**
 * Setup for Agent Session Extension
 */
@Value
@Builder
@SuppressWarnings("java:S6548")
public class AgentSessionExtensionSetup {
    public static final int DEFAULT_HISTORICAL_MESSAGES_COUNT = 5;
    public static final int DEFAULT_HISTORICAL_MESSAGES_FETCH_COUNT = -1;
    public static final int DEFAULT_MAX_MESSAGES_TO_SUMMARIZE = 50;
    public static final int DEFAULT_MAX_SUMMARY_LENGTH = 1000;
    public static final boolean DEFAULT_DISABLE_SUMMARIZATION = false;
    public static final CompactionType DEFAULT_COMPACTION_STRATEGY = CompactionType.AUTOMATIC;
    private static final int DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD = 0;

    public static final AgentSessionExtensionSetup DEFAULT = new AgentSessionExtensionSetup(
            DEFAULT_HISTORICAL_MESSAGES_COUNT,
            DEFAULT_HISTORICAL_MESSAGES_FETCH_COUNT,
            DEFAULT_MAX_MESSAGES_TO_SUMMARIZE,
            DEFAULT_MAX_SUMMARY_LENGTH,
            DEFAULT_DISABLE_SUMMARIZATION,
            DEFAULT_COMPACTION_STRATEGY,
            DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD);

    /**
     * Maximum number of old messages to be fetched from history store and injected into messages.
     */
    @Builder.Default
    int historicalMessagesCount = DEFAULT_HISTORICAL_MESSAGES_COUNT;

    /**
     * Number of historical messages to fetch from session store in one go.
     * <p>
     * For some custom selection strategies, more messages may need to be fetched than actually injected. Defaults to
     * historicalMessagesCount. Session store
     * implementations should use this value to optimize fetches.
     * In case of difference between historicalMessagesCount and historicalMessageFetchSize, the maximum value
     * between the two will be used to fetch messages.
     */
    @Builder.Default
    int historicalMessageFetchSize = DEFAULT_HISTORICAL_MESSAGES_FETCH_COUNT;

    /**
     * Threshold of number of messages in session beyond which summarization is triggered
     */
    @Builder.Default
    int maxMessagesToSummarize = DEFAULT_MAX_MESSAGES_TO_SUMMARIZE;

    /**
     * Maximum length of the summary to be generated.
     */
    @Builder.Default
    int maxSummaryLength = DEFAULT_MAX_SUMMARY_LENGTH;

    @Builder.Default
    boolean disableSummarization = DEFAULT_DISABLE_SUMMARIZATION;

    @Builder.Default
    CompactionType compactionStrategy = DEFAULT_COMPACTION_STRATEGY;

    @Builder.Default
    int autoSummarizationThreshold = DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD;
}
