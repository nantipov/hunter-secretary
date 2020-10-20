package org.nantipov.huntersecretary.config;

import org.nantipov.huntersecretary.services.GoogleAuthService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(GoogleAuthService.Scopes.class)
@EnableScheduling
public class GeneralConfig {
}
