package com.github.pvtitov.aichatlite.service;

import com.github.pvtitov.aichatlite.constants.ApiConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that manages available LLM services and provides the current active one.
 */
public class LlmServiceRegistry {
    
    private final Map<String, LlmService> services = new ConcurrentHashMap<>();
    private volatile String currentModelId;
    
    public LlmServiceRegistry() {
        // Register default services
        registerDefaultServices();
    }
    
    private void registerDefaultServices() {
        // Register GigaChat (if credentials are available)
        try {
            GigaChatApiService gigaChat = new GigaChatApiService();
            register(gigaChat);
            if (currentModelId == null) {
                setCurrentModel(gigaChat.getModel().getId());
            }
        } catch (IllegalStateException e) {
            // GigaChat credentials not available, skip
        }
        
        // Register default Ollama model
        try {
            OllamaLlmService ollama = new OllamaLlmService(ApiConstants.OLLAMA_DEFAULT_MODEL);
            register(ollama);
            if (currentModelId == null) {
                setCurrentModel(ollama.getModel().getId());
            }
        } catch (Exception e) {
            // Ollama not available, skip
        }
    }
    
    /**
     * Register an LLM service.
     */
    public void register(LlmService service) {
        services.put(service.getModel().getId(), service);
    }
    
    /**
     * Get the current active LLM service.
     */
    public LlmService getCurrentService() {
        if (currentModelId == null) {
            throw new IllegalStateException("No LLM model is currently selected");
        }
        LlmService service = services.get(currentModelId);
        if (service == null) {
            throw new IllegalStateException("Current model '" + currentModelId + "' not found in registry");
        }
        return service;
    }
    
    /**
     * Set the current model by its ID.
     * @return true if successful, false if model not found
     */
    public boolean setCurrentModel(String modelId) {
        if (!services.containsKey(modelId)) {
            return false;
        }
        this.currentModelId = modelId;
        return true;
    }
    
    /**
     * Get the current model ID.
     */
    public String getCurrentModelId() {
        return currentModelId;
    }
    
    /**
     * Get all available models.
     */
    public List<LlmModel> getAvailableModels() {
        List<LlmModel> models = new ArrayList<>();
        for (LlmService service : services.values()) {
            models.add(service.getModel());
        }
        return Collections.unmodifiableList(models);
    }
    
    /**
     * Get a service by model ID (for switching).
     */
    public Optional<LlmService> getServiceByModelId(String modelId) {
        return Optional.ofNullable(services.get(modelId));
    }
    
    /**
     * Switch to a model by its model name (not ID).
     * Tries to find a matching service.
     */
    public boolean switchToModelByName(String modelName) {
        for (Map.Entry<String, LlmService> entry : services.entrySet()) {
            if (entry.getValue().getModel().getModelName().equalsIgnoreCase(modelName)) {
                currentModelId = entry.getKey();
                return true;
            }
        }
        return false;
    }
}
