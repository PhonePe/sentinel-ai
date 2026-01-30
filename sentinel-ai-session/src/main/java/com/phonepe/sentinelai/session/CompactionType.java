package com.phonepe.sentinelai.session;

public enum CompactionType {
    /**
     * Evaluation will be done after every run
     */
    EVERY_RUN,
    /**
     * Evaluation will be done intelligently based on the amount of tokens being generated
     * Threshold can be specified using {@link AgentSessionExtensionSetup}
     */
    AUTOMATIC
}
