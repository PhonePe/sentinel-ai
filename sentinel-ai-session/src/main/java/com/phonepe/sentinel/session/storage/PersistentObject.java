package com.phonepe.sentinel.session.storage;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;

/**
 * Base class for all objects stored in session storage
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistentSessionSummary.class, name = "SESSION_SUMMARY"),
})
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public abstract class PersistentObject {
    private final SessionStoredObjectType type;
    private final String id;
    private final String sessionId;
    private final String runId;
    private final long timestamp;

    /**
     * To be implemented by subclasses to accept visitor
     * @param visitor A visitor that implements subclass specific behaviour
     * @return Result of visitor operation
     * @param <T> Type of result
     */
    public abstract <T> T accept(final SessionStoredObjectVisitor<T> visitor);
}
