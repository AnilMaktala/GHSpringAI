package com.example.triage.client;

public class GitHubApiException extends RuntimeException {
    
    private final int exitCode;
    
    public GitHubApiException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }
    
    public GitHubApiException(String message, Throwable cause, int exitCode) {
        super(message, cause);
        this.exitCode = exitCode;
    }
    
    public int getExitCode() {
        return exitCode;
    }
}
