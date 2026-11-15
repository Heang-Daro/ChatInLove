package org.dr.model.enums;

public enum CallSessionStatus {
    RINGING,  // offer sent, waiting for answer
    ACTIVE,   // call accepted and ongoing
    ENDED,    // call ended normally
    MISSED,   // callee never answered before expiresAt
    REJECTED  // callee declined
}
