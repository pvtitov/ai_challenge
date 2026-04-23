package com.github.pvtitov.aichatclearning.dto;

import java.util.List;

public class LlmStructuredResponse {

    private String response;
    private TokenUsage tokens;
    private boolean jsonParseFailed;
    private List<EmbeddingSearchResult> ragSources;

    public static class TokenUsage {
        private int input;
        private int output;
        private int total;

        public TokenUsage() {
        }

        public TokenUsage(int input, int output, int total) {
            this.input = input;
            this.output = output;
            this.total = total;
        }

        public int getInput() {
            return input;
        }

        public void setInput(int input) {
            this.input = input;
        }

        public int getOutput() {
            return output;
        }

        public void setOutput(int output) {
            this.output = output;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public LlmStructuredResponse() {
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public TokenUsage getTokens() {
        return tokens;
    }

    public void setTokens(TokenUsage tokens) {
        this.tokens = tokens;
    }

    public boolean isJsonParseFailed() {
        return jsonParseFailed;
    }

    public void setJsonParseFailed(boolean jsonParseFailed) {
        this.jsonParseFailed = jsonParseFailed;
    }

    public List<EmbeddingSearchResult> getRagSources() {
        return ragSources;
    }

    public void setRagSources(List<EmbeddingSearchResult> ragSources) {
        this.ragSources = ragSources;
    }
}
