package com.phonepe.sentinel.session;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * Setup for Agent Session Extension
 */
@Value
@Builder
@SuppressWarnings("java:S6548")
public class AgentSessionExtensionSetup {
    public static final Set<AgentSessionExtensionFeature> ALL_FEATURES
            = Set.of(AgentSessionExtensionFeature.SUMMARY, AgentSessionExtensionFeature.HISTORY);
    public static final int DEFAULT_MAX_HISTORY_MESSAGES = 5;
    public static final int DEFAULT_SUMMARIZATION_THRESHOLD = 50;
    public static final int DEFAULT_MAX_SUMMARY_LENGTH = 1000;
    public static final AgentSessionExtensionSetup DEFAULT = new AgentSessionExtensionSetup(
            ALL_FEATURES,
            DEFAULT_MAX_HISTORY_MESSAGES,
            DEFAULT_SUMMARIZATION_THRESHOLD,
            DEFAULT_MAX_SUMMARY_LENGTH);

    /**
     * Features to be enabled in this extension
     */
    @Builder.Default
    Set<AgentSessionExtensionFeature> features = ALL_FEATURES;

    /**
     * Maximum number of old messages to be fetched from history store and injected into messages.
     */
    @Builder.Default
    int maxHistoryMessages = DEFAULT_MAX_HISTORY_MESSAGES;

    /**
     * Threshold of number of messages in session beyond which summarization is triggered
     */
    @Builder.Default
    int summarizationThreshold = DEFAULT_SUMMARIZATION_THRESHOLD;

    /**
     * Maximum length of the summary to be generated.
     */
    @Builder.Default
    int maxSummaryLength = DEFAULT_MAX_SUMMARY_LENGTH;
}
