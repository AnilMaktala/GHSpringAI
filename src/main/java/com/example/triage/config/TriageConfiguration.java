package com.example.triage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "triage")
@Validated
public class TriageConfiguration {
    
    @NotNull
    private GitHub github;
    
    @NotNull
    private AI ai;
    
    @NotBlank
    private String outputPath;
    
    @NotBlank
    private String schedule;
    
    public GitHub getGithub() {
        return github;
    }
    
    public void setGithub(GitHub github) {
        this.github = github;
    }
    
    public AI getAi() {
        return ai;
    }
    
    public void setAi(AI ai) {
        this.ai = ai;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    public String getSchedule() {
        return schedule;
    }
    
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    
    @Validated
    public static class GitHub {
        @NotBlank
        private String owner;
        
        @NotBlank
        private String repo;
        
        @NotBlank
        private String token;
        
        public String getOwner() {
            return owner;
        }
        
        public void setOwner(String owner) {
            this.owner = owner;
        }
        
        public String getRepo() {
            return repo;
        }
        
        public void setRepo(String repo) {
            this.repo = repo;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
    }
    
    @Validated
    public static class AI {
        @NotBlank
        private String provider;
        
        @NotBlank
        private String model;
        
        @NotNull
        private Double temperature;
        
        @NotNull
        private Integer maxRetries;
        
        public String getProvider() {
            return provider;
        }
        
        public void setProvider(String provider) {
            this.provider = provider;
        }
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public Double getTemperature() {
            return temperature;
        }
        
        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }
        
        public Integer getMaxRetries() {
            return maxRetries;
        }
        
        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
