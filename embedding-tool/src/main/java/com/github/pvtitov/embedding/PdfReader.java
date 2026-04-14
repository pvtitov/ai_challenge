package com.github.pvtitov.embedding;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility for reading PDF files using Apache PDFBox.
 */
public class PdfReader {

    /**
     * Reads text content from a PDF file.
     *
     * @param path path to the PDF file
     * @return extracted text content
     * @throws IOException if the file cannot be read
     */
    public static String readPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
