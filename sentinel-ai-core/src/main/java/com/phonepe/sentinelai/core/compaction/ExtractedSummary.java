package com.phonepe.sentinelai.core.compaction;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Value;

import java.util.List;

/**
 * Summary extracted by LLM
 */
@Value
@JsonClassDescription("Summary of the session till now and the current run")
public class ExtractedSummary {

    @JsonPropertyDescription("""
        A summary of the conversation thus far between the user and the agent. \
        Formatted in a structured manner so that it can be used by an LLM to understand the conversation \
        history thus far without needing all the raw messages""")
    String sessionSummary;

    @JsonPropertyDescription("A short title for the session summarizing the main topic being discussed")
    String title;

    @JsonPropertyDescription("Important one-word keywords/topics being discussed in the session")
    List<String> keywords;
}
