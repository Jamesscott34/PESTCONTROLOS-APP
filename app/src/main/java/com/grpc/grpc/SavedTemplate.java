package com.grpc.grpc;

import java.util.ArrayList;
import java.util.List;

/**
 * A named template saved by the user (logo, watermark, header blocks).
 * Can be listed in View Templates and used when creating a report.
 */
public class SavedTemplate {

    private String id;
    private String name;
    private String logoPath;
    private boolean watermarkEnabled;
    private String watermarkType = PdfTemplateSettings.WATERMARK_TEXT;
    private String watermarkText = "";
    private String watermarkImagePath;
    private List<PdfTemplateSettings.HeaderBlock> headerBlocks = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public boolean isWatermarkEnabled() { return watermarkEnabled; }
    public void setWatermarkEnabled(boolean watermarkEnabled) { this.watermarkEnabled = watermarkEnabled; }

    public String getWatermarkType() { return watermarkType; }
    public void setWatermarkType(String watermarkType) { this.watermarkType = watermarkType != null ? watermarkType : PdfTemplateSettings.WATERMARK_TEXT; }

    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText != null ? watermarkText : ""; }

    public String getWatermarkImagePath() { return watermarkImagePath; }
    public void setWatermarkImagePath(String watermarkImagePath) { this.watermarkImagePath = watermarkImagePath; }

    public List<PdfTemplateSettings.HeaderBlock> getHeaderBlocks() { return headerBlocks; }
    public void setHeaderBlocks(List<PdfTemplateSettings.HeaderBlock> headerBlocks) { this.headerBlocks = headerBlocks != null ? headerBlocks : new ArrayList<>(); }

    /** Convert to PdfTemplateSettings for PDF generation (MY_TEMPLATE with this template's content). */
    public PdfTemplateSettings toPdfTemplateSettings() {
        PdfTemplateSettings s = new PdfTemplateSettings();
        s.setTemplateSelection(PdfTemplateSettings.MY_TEMPLATE);
        s.setLogoPath(logoPath);
        s.setWatermarkEnabled(watermarkEnabled);
        s.setWatermarkType(watermarkType);
        s.setWatermarkText(watermarkText);
        s.setWatermarkImagePath(watermarkImagePath);
        s.setHeaderBlocks(headerBlocks);
        return s;
    }
}
