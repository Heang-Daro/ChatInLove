package org.dr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves locally uploaded files (the "uploads" folder next to the running app)
 * over HTTP so the frontend can render them as image / voice / video URLs.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Absolute or relative location of the local upload folder. */
    @Value("${app.upload.dir:./uploads/}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + location)
                .setCachePeriod(86400);
    }
}
