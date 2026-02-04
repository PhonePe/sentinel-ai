package com.phonepe.sentinelai.session;


import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * Setup for Agent Session Extension
 */
@Value
@Builder
@With
@SuppressWarnings("java:S6548")
public class AgentSessionExtensionSetup {
    public static final int MAX_HISTORICAL_MESSAGES_FETCH_COUNT = 30;
    public static final int DEFAULT_MAX_MESSAGES_TO_SUMMARIZE = 50;
    public static final int DEFAULT_MAX_SUMMARY_LENGTH = 1000;
    public static final boolean DEFAULT_DISABLE_SUMMARIZATION = false;
    private static final int DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD = 60;

    public static final AgentSessionExtensionSetup DEFAULT = new AgentSessionExtensionSetup(
            MAX_HISTORICAL_MESSAGES_FETCH_COUNT,
            DEFAULT_MAX_SUMMARY_LENGTH,
            DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD);

    /**
     * Number of historical messages to fetch from session store in one go.
     * <p>
     * The extension may need to fetch more messages than historicalMessagesFetchCount to find
     * all messages that need to be summarized. So it will fetch messages in batches of this size until
     * it has enough messages to summarize or there are no more messages left.
     * */
    @Builder.Default
    int historicalMessageFetchSize = MAX_HISTORICAL_MESSAGES_FETCH_COUNT;

    /**
     * Maximum length of the summary to be generated.
     */
    @Builder.Default
    int maxSummaryLength = DEFAULT_MAX_SUMMARY_LENGTH;

    /**
     * Threshold of percentage of tokens used in the session beyond which automatic summarization is triggered.
     */
    @Builder.Default
    int autoSummarizationThreshold = DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD;
}
