package com.github.pvtitov.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;

/**
 * CLI tool for generating embeddings from text files using Ollama.
 *
 * Usage:
 *   java -jar embedding-tool.jar file1.txt file2.pdf [--chunk 500] [--overlap 50]
 */
public class EmbeddingTool {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    static {
        // Load logging configuration to suppress PDFBox warnings
        try (InputStream is = EmbeddingTool.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        // Parse arguments
        List<String> filePaths = new ArrayList<>();
        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlapSize = DEFAULT_OVERLAP_SIZE;

        int i = 0;
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
                    content = readFile(path);
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

    /**
     * Reads file content. Supports .txt (plain text) and .pdf files.
     */
    private static String readFile(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            return PdfReader.readPdf(path);
        } else {
            return Files.readString(path);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar embedding-tool.jar <file1> [file2...] [--chunk SIZE] [--overlap SIZE]

                Options:
                  --chunk     Chunk size (default: 500)
                  --overlap   Overlap size (default: 50)

                Supported file types: .txt, .pdf

                Examples:
                  java -jar embedding-tool.jar file.txt
                  java -jar embedding-tool.jar file1.txt file2.pdf --chunk 1000 --overlap 100
                """);
    }
}
