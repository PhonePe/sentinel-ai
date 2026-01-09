package com.phonepe.sentinel.session;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A summary for the session
 */
@Value
@JsonClassDescription("A summary for the session based on the latest few messages")
@Builder
public class SessionSummary {
    @JsonPropertyDescription("Session id for this session")
    String sessionId;

    String title;

    @JsonPropertyDescription("A short summary of the conversation thus far between the user and the agent")
    String summary;

    @JsonPropertyDescription("A short list of topics being discussed")
    List<String> keywords;

    long updatedAt;
}
