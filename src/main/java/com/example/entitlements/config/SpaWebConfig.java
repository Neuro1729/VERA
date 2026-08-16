package com.example.entitlements.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class SpaWebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    static final class SpaResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
                return null;
            }
            if (resourcePath.isEmpty() || "/".equals(resourcePath)) {
                return indexIfPresent(location);
            }
            Resource existing = super.getResource(resourcePath, location);
            if (existing != null) {
                return existing;
            }
            if (hasExtension(resourcePath)) {
                return null;
            }
            return indexIfPresent(location);
        }

        private static Resource indexIfPresent(Resource location) throws IOException {
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        private static boolean hasExtension(String resourcePath) {
            int slash = resourcePath.lastIndexOf('/');
            String name = slash >= 0 ? resourcePath.substring(slash + 1) : resourcePath;
            return name.contains(".");
        }
    }
}
