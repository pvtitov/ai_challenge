package com.github.pvtitov.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits text into overlapping chunks with section detection.
 */
public class TextChunker {

    private final int chunkSize;
    private final int overlapSize;

    public TextChunker(int chunkSize, int overlapSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("Chunk size must be positive");
        if (overlapSize < 0) throw new IllegalArgumentException("Overlap size must be non-negative");
        if (overlapSize >= chunkSize) throw new IllegalArgumentException("Overlap must be less than chunk size");
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    /**
     * Chunks text from a file, detecting sections and assigning metadata.
     *
     * @param filePath  source file path
     * @param title     title (usually filename)
     * @param content   full text content
     * @param startChunkId starting chunk ID
     * @return list of chunks with metadata
     */
    public List<Chunk> chunk(String filePath, String title, String content, int startChunkId) {
        List<Chunk> chunks = new ArrayList<>();

        // Detect sections (headings like # Title, ## Section, or blank-line separated)
        List<Section> sections = detectSections(content);

        int currentChunkId = startChunkId;

        for (Section section : sections) {
            String sectionText = section.text.trim();
            if (sectionText.isEmpty()) continue;

            // If section text is small enough, keep as one chunk
            if (sectionText.length() <= chunkSize) {
                Chunk chunk = new Chunk(currentChunkId++, filePath, title, section.name, sectionText);
                chunks.add(chunk);
            } else {
                // Split into overlapping chunks
                List<String> subChunks = splitWithOverlap(sectionText);
                for (int i = 0; i < subChunks.size(); i++) {
                    String subChunk = subChunks.get(i);
                    // First chunk gets the section name, subsequent ones inherit it
                    String sectionName = (i == 0) ? section.name : section.name;
                    Chunk chunk = new Chunk(currentChunkId++, filePath, title, sectionName, subChunk);
                    chunks.add(chunk);
                }
            }
        }

        return chunks;
    }

    /**
     * Detects sections in the text based on markdown-style headings.
     */
    private List<Section> detectSections(String content) {
        List<Section> sections = new ArrayList<>();

        // Pattern for markdown headings: # Heading, ## Heading, etc.
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        List<Integer> headingPositions = new ArrayList<>();
        List<String> headingNames = new ArrayList<>();

        while (matcher.find()) {
            headingPositions.add(matcher.start());
            headingNames.add(matcher.group(2).trim());
        }

        if (headingPositions.isEmpty()) {
            // No headings, treat entire content as one section
            sections.add(new Section("", content));
            return sections;
        }

        // Check if there's content before the first heading (preface/title)
        if (headingPositions.get(0) > 0) {
            String preface = content.substring(0, headingPositions.get(0)).trim();
            if (!preface.isEmpty()) {
                // Use the first line as the title for preface section
                String prefaceTitle = preface.split("\\n")[0].trim();
                sections.add(new Section(prefaceTitle, content.substring(0, headingPositions.get(0))));
            }
        }

        // Extract section texts
        for (int i = 0; i < headingPositions.size(); i++) {
            int start = headingPositions.get(i);
            int end = (i + 1 < headingPositions.size()) ? headingPositions.get(i + 1) : content.length();
            String sectionText = content.substring(start, end).trim();
            sections.add(new Section(headingNames.get(i), sectionText));
        }

        return sections;
    }

    /**
     * Splits text into overlapping chunks.
     */
    private List<String> splitWithOverlap(String text) {
        List<String> result = new ArrayList<>();
        int step = chunkSize - overlapSize;
        int pos = 0;

        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            String chunk = text.substring(pos, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
            pos += step;
        }

        return result;
    }

    private static class Section {
        final String name;
        final String text;

        Section(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }
}
