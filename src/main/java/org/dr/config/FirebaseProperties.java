package org.dr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {
    private String key;
    private String projectId;
    private String storageBucket; // Automatically maps app.firebase.storage-bucket
}