package com.github.pvtitov.embedding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI tool for generating embeddings from text files using Ollama.
 *
 * Usage:
 *   java -jar embedding-tool.jar /index file1.txt file2.txt [--chunk 500] [--overlap 50]
 *   java -jar embedding-tool.jar /index_print [count]
 */
public class EmbeddingTool {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        try {
            switch (command) {
                case "/index" -> handleIndex(args);
                case "/index_print" -> handleIndexPrint(args);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleIndex(String[] args) {
        // Parse arguments
        List<String> filePaths = new ArrayList<>();
        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlapSize = DEFAULT_OVERLAP_SIZE;

        int i = 1;
        while (i < args.length) {
            String arg = args[i];
            if ("--chunk".equals(arg) && i + 1 < args.length) {
                chunkSize = Integer.parseInt(args[++i]);
            } else if ("--overlap".equals(arg) && i + 1 < args.length) {
                overlapSize = Integer.parseInt(args[++i]);
            } else if (!arg.startsWith("--")) {
                filePaths.add(arg);
            }
            i++;
        }

        if (filePaths.isEmpty()) {
            System.err.println("Error: No files specified");
            printUsage();
            System.exit(1);
        }

        System.out.println("=== Embedding Index Tool ===");
        System.out.println("Chunk size: " + chunkSize);
        System.out.println("Overlap size: " + overlapSize);
        System.out.println("Files to process: " + filePaths.size());
        System.out.println();

        // Validate Ollama availability
        OllamaClient ollama = new OllamaClient();
        if (!ollama.isAvailable()) {
            System.err.println("Error: Ollama is not available at localhost:11434");
            System.err.println("Please start Ollama first: ollama serve");
            System.exit(1);
        }
        System.out.println("✓ Ollama is available");

        // Initialize repository
        try (EmbeddingRepository repo = new EmbeddingRepository()) {
            repo.initialize();
            System.out.println("✓ Database initialized");
            System.out.println();

            int startChunkId = repo.getNextChunkId();
            TextChunker chunker = new TextChunker(chunkSize, overlapSize);
            int totalChunks = 0;

            for (String filePath : filePaths) {
                Path path = Path.of(filePath);
                if (!Files.exists(path)) {
                    System.err.println("Warning: File not found, skipping: " + filePath);
                    continue;
                }

                String title = path.getFileName().toString();
                System.out.println("Processing: " + filePath + " (" + title + ")");

                String content;
                try {
                    content = Files.readString(path);
                } catch (IOException e) {
                    System.err.println("Warning: Cannot read file, skipping: " + filePath + " - " + e.getMessage());
                    continue;
                }

                // Chunk the text
                List<Chunk> chunks = chunker.chunk(filePath, title, content, startChunkId);
                System.out.println("  → Split into " + chunks.size() + " chunks");

                // Generate embeddings
                int processed = 0;
                for (Chunk chunk : chunks) {
                    float[] embedding = ollama.embed(chunk.getContent());
                    chunk.setEmbedding(embedding);
                    processed++;
                    if (processed % 10 == 0 || processed == chunks.size()) {
                        System.out.print("  → Generating embeddings: " + processed + "/" + chunks.size() + "\r");
                    }
                }
                System.out.println();

                // Save to database
                repo.saveBatch(chunks);
                System.out.println("  ✓ Saved " + chunks.size() + " embeddings to database");
                System.out.println();

                startChunkId += chunks.size();
                totalChunks += chunks.size();
            }

            System.out.println("=== Indexing Complete ===");
            System.out.println("Total chunks indexed: " + totalChunks);
            System.out.println("Database: " + System.getProperty("user.dir") + "/embeddings.db");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format for --chunk or --overlap: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleIndexPrint(String[] args) {
        Integer limit = null;
        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Error: Invalid number format for limit: " + args[1]);
                System.exit(1);
            }
        }

        try (EmbeddingRepository repo = new EmbeddingRepository()) {
            repo.initialize();

            int total = repo.count();
            if (total == 0) {
                System.out.println("No entries in the index. Use /index to add files first.");
                return;
            }

            List<EmbeddingRepository.IndexedEntry> entries = repo.findByLimit(limit);

            System.out.println("=== Embedding Index Contents ===");
            if (limit != null) {
                System.out.println("Showing first " + Math.min(limit, entries.size()) + " of " + total + " entries");
            } else {
                System.out.println("Total entries: " + total);
            }
            System.out.println();

            for (EmbeddingRepository.IndexedEntry entry : entries) {
                System.out.println(entry);
                System.out.println();
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar embedding-tool.jar /index <file1> [file2...] [--chunk SIZE] [--overlap SIZE]
                  java -jar embedding-tool.jar /index_print [COUNT]

                Commands:
                  /index        Index files into embeddings
                    --chunk     Chunk size (default: 500)
                    --overlap   Overlap size (default: 50)

                  /index_print  Print index contents
                    COUNT       Limit number of entries to print (default: all)

                Examples:
                  java -jar embedding-tool.jar /index file.txt
                  java -jar embedding-tool.jar /index file1.txt file2.txt --chunk 1000 --overlap 100
                  java -jar embedding-tool.jar /index_print
                  java -jar embedding-tool.jar /index_print 5
                """);
    }
}
