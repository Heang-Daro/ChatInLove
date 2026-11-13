package org.dr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chat")
@Data
public class ChatProperties {
    private GroupConfig group;
    private CallConfig call;

    @Data
    public static class GroupConfig {
        private int minInvitedMembers = 3;
    }

    @Data
    public static class CallConfig {
        private int ringTimeoutSeconds = 30;
        private int maxActiveCallsPerUser = 1;
    }
}
