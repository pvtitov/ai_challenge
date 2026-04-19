package com.github.pvtitov.aichatlite;

import com.github.pvtitov.aichatlite.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatlite.dto.TaskDecisionResponse;
import com.github.pvtitov.aichatlite.dto.TaskCompletionStatus;
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
                "response": "Hello! How can I help you today?"
            }
            """;

        LlmStructuredResponse response = objectMapper.readValue(json, LlmStructuredResponse.class);

        assertEquals("Hello! How can I help you today?", response.getResponse());
        assertFalse(response.isJsonParseFailed());
    }

    @Test
    public void testParseResponseWithTokens() throws Exception {
        String json = """
            {
                "response": "Test response",
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

    @Test
    public void testParseTaskDecisionResponse() throws Exception {
        String json = """
            {
                "taskTitle": "Write a Java function",
                "isNewTask": true,
                "existingTaskId": null,
                "requirements": ["Must be efficient", "Must be well-documented"],
                "addedRequirements": ["Must handle edge cases"]
            }
            """;

        TaskDecisionResponse response = objectMapper.readValue(json, TaskDecisionResponse.class);

        assertEquals("Write a Java function", response.getTaskTitle());
        assertTrue(response.isNewTask());
        assertNull(response.getExistingTaskId());
        assertNotNull(response.getRequirements());
        assertEquals(2, response.getRequirements().size());
        assertEquals("Must be efficient", response.getRequirements().get(0));
        assertNotNull(response.getAddedRequirements());
        assertEquals(1, response.getAddedRequirements().size());
        assertEquals("Must handle edge cases", response.getAddedRequirements().get(0));
    }

    @Test
    public void testTaskCompletionStatus() throws Exception {
        String json = """
            {
                "isCompleted": true,
                "reason": "The answer provides a complete implementation with all requirements met"
            }
            """;

        TaskCompletionStatus status = objectMapper.readValue(json, TaskCompletionStatus.class);

        assertTrue(status.isCompleted());
        assertEquals("The answer provides a complete implementation with all requirements met", 
                     status.getReason());
    }

    @Test
    public void testTaskCompletionStatusSerialization() throws Exception {
        TaskCompletionStatus status = new TaskCompletionStatus();
        status.setCompleted(true);
        status.setReason("Task completed successfully");
        
        String json = objectMapper.writeValueAsString(status);
        assertTrue(json.contains("isCompleted"));
        assertTrue(json.contains("reason"));
        assertTrue(json.contains("true"));
    }

    @Test
    public void testTaskDecisionResponseWithExistingTask() throws Exception {
        String json = """
            {
                "taskTitle": "Continue API development",
                "isNewTask": false,
                "existingTaskId": 5,
                "requirements": ["Implement REST endpoints", "Add authentication", "Use proper error handling"],
                "addedRequirements": ["Add rate limiting"]
            }
            """;

        TaskDecisionResponse response = objectMapper.readValue(json, TaskDecisionResponse.class);

        assertEquals("Continue API development", response.getTaskTitle());
        assertFalse(response.isNewTask());
        assertEquals(Long.valueOf(5), response.getExistingTaskId());
        assertEquals(3, response.getRequirements().size());
        assertEquals(1, response.getAddedRequirements().size());
        assertEquals("Add rate limiting", response.getAddedRequirements().get(0));
    }
}
