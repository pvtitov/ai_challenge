package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.CitationSource;
import com.github.pvtitov.aichat.dto.EmbeddingSearchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for enhanced RAG functionality.
 */
public class EnhancedRAGTest {

    @Test
    public void testCitationSourceCreation() {
        CitationSource citation = new CitationSource(
            1,
            "learn_c_the_hard_way.pdf",
            "learn_c_the_hard_way.pdf",
            "Pointers",
            "Pointers are variables that store memory addresses...",
            0.842
        );

        assertEquals(1, citation.getChunkId());
        assertEquals("learn_c_the_hard_way.pdf", citation.getSource());
        assertEquals("Pointers", citation.getSection());
        assertEquals(0.842, citation.getRelevanceScore());
        assertTrue(citation.getQuote().contains("Pointers"));
    }

    @Test
    public void testCitationSourceFormatting() {
        CitationSource citation = new CitationSource(
            2,
            "learn_c_the_hard_way.pdf",
            "learn_c_the_hard_way.pdf",
            "Memory Management",
            "Memory management requires careful attention...",
            0.798
        );

        String formatted = citation.formatCitation();
        assertTrue(formatted.contains("learn_c_the_hard_way.pdf"));
        assertTrue(formatted.contains("Memory Management"));
        assertTrue(formatted.contains("0.798"));
    }

    @Test
    public void testSearchContextWithResults() {
        EmbeddingSearchService service = new EmbeddingSearchService();
        
        List<EmbeddingSearchResult> mockResults = Arrays.asList(
            new EmbeddingSearchResult(1, "file1.pdf", "file1.pdf", "Section1", "Content 1", 0.85),
            new EmbeddingSearchResult(2, "file2.pdf", "file2.pdf", "Section2", "Content 2", 0.78)
        );

        EmbeddingSearchService.SearchContext context = service.formatResultsAsContextWithCitations(mockResults);

        assertTrue(context.hasResults());
        assertEquals(2, context.getCitations().size());
        assertEquals(0.85, context.getMaxRelevanceScore());
        assertTrue(context.getFormattedContext().contains("=== Relevant Knowledge Retrieved ==="));
        
        CitationSource firstCitation = context.getCitations().get(0);
        assertEquals(1, firstCitation.getChunkId());
        assertEquals(0.85, firstCitation.getRelevanceScore());
    }

    @Test
    public void testSearchContextEmptyResults() {
        EmbeddingSearchService service = new EmbeddingSearchService();
        
        EmbeddingSearchService.SearchContext context = service.formatResultsAsContextWithCitations(List.of());

        assertFalse(context.hasResults());
        assertEquals(0, context.getCitations().size());
        assertEquals(0.0, context.getMaxRelevanceScore());
        assertEquals("", context.getFormattedContext());
    }

    @Test
    public void testLowRelevanceDetection() {
        EmbeddingSearchService service = new EmbeddingSearchService();
        
        List<EmbeddingSearchResult> lowRelevanceResults = Arrays.asList(
            new EmbeddingSearchResult(1, "file1.pdf", "file1.pdf", "Section1", "Content 1", 0.65),
            new EmbeddingSearchResult(2, "file2.pdf", "file2.pdf", "Section2", "Content 2", 0.62)
        );

        EmbeddingSearchService.SearchContext context = service.formatResultsAsContextWithCitations(lowRelevanceResults);

        assertTrue(context.hasResults());
        assertEquals(0.65, context.getMaxRelevanceScore());
        
        // Test threshold logic (this would be in ChatServiceImpl)
        double threshold = 0.75;
        boolean isLowRelevance = context.getMaxRelevanceScore() < threshold;
        assertTrue(isLowRelevance, "Should detect low relevance when max score < 0.75");
    }

    @Test
    public void testHighRelevanceDetection() {
        EmbeddingSearchService service = new EmbeddingSearchService();
        
        List<EmbeddingSearchResult> highRelevanceResults = Arrays.asList(
            new EmbeddingSearchResult(1, "file1.pdf", "file1.pdf", "Section1", "Content 1", 0.88),
            new EmbeddingSearchResult(2, "file2.pdf", "file2.pdf", "Section2", "Content 2", 0.82)
        );

        EmbeddingSearchService.SearchContext context = service.formatResultsAsContextWithCitations(highRelevanceResults);

        assertTrue(context.hasResults());
        assertEquals(0.88, context.getMaxRelevanceScore());
        
        // Test threshold logic
        double threshold = 0.75;
        boolean isLowRelevance = context.getMaxRelevanceScore() < threshold;
        assertFalse(isLowRelevance, "Should NOT detect low relevance when max score >= 0.75");
    }

    @Test
    public void testQuoteExtraction() {
        EmbeddingSearchService service = new EmbeddingSearchService();
        
        // Test with long content (> 200 chars)
        String longContent = "This is a very long content that exceeds two hundred characters. ".repeat(10);
        List<EmbeddingSearchResult> results = List.of(
            new EmbeddingSearchResult(1, "file.pdf", "file.pdf", "Section", longContent, 0.85)
        );

        EmbeddingSearchService.SearchContext context = service.formatResultsAsContextWithCitations(results);
        
        CitationSource citation = context.getCitations().get(0);
        assertEquals(203, citation.getQuote().length()); // 200 + "..."
        assertTrue(citation.getQuote().endsWith("..."));
        
        // Test with short content (< 200 chars)
        String shortContent = "Short content";
        results = List.of(
            new EmbeddingSearchResult(1, "file.pdf", "file.pdf", "Section", shortContent, 0.85)
        );

        context = service.formatResultsAsContextWithCitations(results);
        citation = context.getCitations().get(0);
        assertEquals("Short content", citation.getQuote());
    }
}
