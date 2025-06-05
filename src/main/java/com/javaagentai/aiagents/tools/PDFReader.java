package com.javaagentai.aiagents.tools;




import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PDFReader implements Tool {

    public PDFReader() {
        super("PDFReader");
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String use(String input) {
        return "";
    }

    @Override
    public String execute(Object input) {
        File pdfFile = (File) input;  // Expecting a File object as input

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);  // Extract text from PDF
        } catch (IOException e) {
            e.printStackTrace();
            return "Error reading PDF: " + e.getMessage();
        }
    }
}
