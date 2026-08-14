package com.example.entitlements;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ResourceEntitlementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceEntitlementApplication.class, args);
    }
}
