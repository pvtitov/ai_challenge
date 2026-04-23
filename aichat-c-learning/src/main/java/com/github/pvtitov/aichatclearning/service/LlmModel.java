package com.github.pvtitov.aichatclearning.service;

/**
 * Represents an LLM model provider with its configuration.
 */
public class LlmModel {
    
    private final String id;
    private final String name;
    private final LlmProvider provider;
    private final String modelName;
    
    public enum LlmProvider {
        GIGACHAT,
        OLLAMA
    }
    
    public LlmModel(String id, String name, LlmProvider provider, String modelName) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.modelName = modelName;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public LlmProvider getProvider() {
        return provider;
    }
    
    public String getModelName() {
        return modelName;
    }
    
    @Override
    public String toString() {
        return name + " (" + modelName + ")";
    }
}
