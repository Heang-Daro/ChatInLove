package org.dr.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties properties;
    private final ResourceLoader resourceLoader;

    @PostConstruct
    public void init() {
        try {
            InputStream serviceAccount = null;
            if (properties.getCredentialsJson() != null && !properties.getCredentialsJson().isBlank()) {
                serviceAccount = new java.io.ByteArrayInputStream(
                        properties.getCredentialsJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if (properties.getKey() != null) {
                var resource = resourceLoader.getResource(properties.getKey());
                if (resource.exists()) {
                    serviceAccount = resource.getInputStream();
                }
            }

            final InputStream finalServiceAccount = serviceAccount;
            if (finalServiceAccount != null) {
                try (finalServiceAccount) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(finalServiceAccount))
                            .setStorageBucket(properties.getStorageBucket())
                            .build();

                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                    }
                }
            } else {
                org.slf4j.LoggerFactory.getLogger(FirebaseConfig.class)
                        .warn("Firebase credentials not found. Provide APP_FIREBASE_CREDENTIALS_JSON or firebase-service-account.json.");
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(FirebaseConfig.class)
                    .error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }
}