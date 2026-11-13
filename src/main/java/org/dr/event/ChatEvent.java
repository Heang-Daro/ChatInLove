package org.dr.event;

import org.dr.dto.response.MessageResponse;

public record ChatEvent(Type type, MessageResponse message) {
    public enum Type { CREATED, EDITED, DELETED }
}