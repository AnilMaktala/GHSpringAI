package com.example.triage.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);
    
    private final TriageConfiguration config;
    
    public ConfigurationValidator(TriageConfiguration config) {
        this.config = config;
    }
    
    @PostConstruct
    public void validateConfiguration() {
        logger.info("Validating configuration...");
        
        boolean hasErrors = false;
        
        // Validate GitHub configuration
        if (config.getGithub() == null) {
            logger.error("GitHub configuration is missing");
            hasErrors = true;
        } else {
            if (isBlank(config.getGithub().getOwner())) {
                logger.error("GitHub owner is not configured. Set GITHUB_OWNER environment variable.");
                hasErrors = true;
            }
            if (isBlank(config.getGithub().getRepo())) {
                logger.error("GitHub repository is not configured. Set GITHUB_REPO environment variable.");
                hasErrors = true;
            }
            if (isBlank(config.getGithub().getToken())) {
                logger.error("GitHub token is not configured. Set GITHUB_TOKEN environment variable.");
                hasErrors = true;
            }
        }
        
        // Validate AI configuration
        if (config.getAi() == null) {
            logger.error("AI configuration is missing");
            hasErrors = true;
        } else {
            if (isBlank(config.getAi().getModel())) {
                logger.error("AI model is not configured.");
                hasErrors = true;
            }
        }
        
        // Validate output path
        if (isBlank(config.getOutputPath())) {
            logger.error("Output path is not configured.");
            hasErrors = true;
        }
        
        if (hasErrors) {
            logger.error("Configuration validation failed. Please check the errors above and configure the required properties.");
            System.exit(5);
        }
        
        logger.info("Configuration validation successful");
        logger.info("GitHub Repository: {}/{}", config.getGithub().getOwner(), config.getGithub().getRepo());
        logger.info("AI Model: {}", config.getAi().getModel());
        logger.info("Output Path: {}", config.getOutputPath());
        logger.info("Schedule: {}", config.getSchedule());
    }
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
