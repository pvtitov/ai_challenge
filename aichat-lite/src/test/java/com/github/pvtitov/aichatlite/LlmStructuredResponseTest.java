package com.github.pvtitov.aichatlite;

import com.github.pvtitov.aichatlite.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatlite.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class LlmStructuredResponseTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testParseStructuredResponse() throws Exception {
        String json = """
            {
                "response": "Hello! How can I help you today?",
                "tasks": [
                    {
                        "title": "Greeting task",
                        "requirements": ["Be friendly"],
                        "invariants": ["Use English"],
                        "verification": {
                            "verified": true,
                            "summary": "Response is appropriate"
                        }
                    }
                ]
            }
            """;
        
        LlmStructuredResponse response = objectMapper.readValue(json, LlmStructuredResponse.class);
        
        assertEquals("Hello! How can I help you today?", response.getResponse());
        assertNotNull(response.getTasks());
        assertEquals(1, response.getTasks().size());
        
        Task task = response.getTasks().get(0);
        assertEquals("Greeting task", task.getTitle());
        assertEquals(1, task.getRequirements().size());
        assertEquals("Be friendly", task.getRequirements().get(0));
        assertTrue(task.getVerification().isVerified());
    }
    
    @Test
    public void testParseResponseWithTokens() throws Exception {
        String json = """
            {
                "response": "Test response",
                "tasks": [],
                "tokens": {
                    "input": 10,
                    "output": 5,
                    "total": 15
                }
            }
            """;
        
        LlmStructuredResponse response = objectMapper.readValue(json, LlmStructuredResponse.class);
        
        assertEquals("Test response", response.getResponse());
        assertNotNull(response.getTokens());
        assertEquals(10, response.getTokens().getInput());
        assertEquals(5, response.getTokens().getOutput());
        assertEquals(15, response.getTokens().getTotal());
    }
}
