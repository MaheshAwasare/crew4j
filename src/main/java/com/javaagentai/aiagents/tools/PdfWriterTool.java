package com.javaagentai.aiagents.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Author: Mahesh Awasare
 * <p>
 * PDFWriter tool
 */
public class PdfWriterTool implements Tool {

    @Override
    public String getName() {
        return "PdfWriter";
    }

    @Override
    public String getDescription() {
        return "A tool to create a PDF document. It now properly handles text with newlines.";
    }


    @Override
    public CompletableFuture<String> use(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String text = (String) params.getOrDefault("text", "No Text found");
            String filePath = (String) params.getOrDefault("filePath", "output.pdf"); // Default to output.pdf

            if (text == null || text.isEmpty() || filePath == null || filePath.isEmpty()) {
                return "Parameters 'text' and 'filePath' are required and cannot be empty.";
            }

            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream contents = new PDPageContentStream(document, page)) {
                    // Using a standard font like Courier from Standard14Fonts
                    contents.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 12);

                    float startX = 50; // Starting X position (left margin)
                    float startY = 750; // Starting Y position (top of the page, adjust as needed)
                    float leading = 15; // Line spacing (font size + a little extra)

                    String[] lines = text.split("\\r?\\n"); // Split text by newlines (\n or \r\n)

                    contents.beginText();
                    contents.newLineAtOffset(startX, startY); // Set initial text position

                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i];
                        contents.showText(line);
                        // Only move to the next line if it's not the last line
                        if (i < lines.length - 1) {
                            contents.newLineAtOffset(0, -leading); // Move down for the next line
                        }
                    }
                    contents.endText();
                }

                document.save(filePath);
                return "PDF saved to " + filePath;

            } catch (IOException e) { // Catch specific IOException for PDF operations
                return "Failed to create PDF: " + e.getMessage();
            } catch (Exception e) { // Catch any other unexpected exceptions
                return "An unexpected error occurred: " + e.getMessage();
            }
        });
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("text", "The text content to be written into the PDF");
        schema.put("filePath", "The path (including filename) where the PDF will be saved");
        return schema;
    }

}