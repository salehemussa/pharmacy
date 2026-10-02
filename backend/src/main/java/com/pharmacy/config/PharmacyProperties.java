package com.pharmacy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "pharmacy")
public class PharmacyProperties {

    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private String corsOrigins = "http://localhost:4200";
    private Security security = new Security();

    public List<String> corsOriginList() {
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private int accessTokenMinutes = 15;
        private int refreshTokenHours = 12;
    }

    @Getter
    @Setter
    public static class Admin {
        private String username = "admin";
        private String email = "admin@pharmacy.local";
        private String password = "";
        private String fullName = "System Administrator";
        private boolean mustChangePassword = true;
    }

    @Getter
    @Setter
    public static class Security {
        private int loginMaxFailures = 5;
        private int loginLockMinutes = 15;
    }
}
