package com.grpc.grpc.reports.data;

import com.grpc.grpc.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A named template saved by the user (logo, watermark, header blocks).
 * Can be listed in View Templates and used when creating a report.
 */
public class SavedTemplate {

    private String id;
    private String name;
    private String mainHeaderText = "";
    private String mainHeaderColorHex = "#0000FF";
    private String logoPath;
    private boolean watermarkEnabled;
    private String watermarkType = PdfTemplateSettings.WATERMARK_TEXT;
    private String watermarkText = "";
    private String watermarkImagePath;
    private String headerSize = PdfTemplateSettings.HEADER_SIZE_DEFAULT;
    private String bodyTextSize = PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT;
    private String footerText;
    private List<PdfTemplateSettings.HeaderBlock> headerBlocks = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public String getMainHeaderText() { return mainHeaderText != null ? mainHeaderText : ""; }
    public void setMainHeaderText(String mainHeaderText) { this.mainHeaderText = mainHeaderText != null ? mainHeaderText : ""; }

    public String getMainHeaderColorHex() { return mainHeaderColorHex != null ? mainHeaderColorHex : "#0000FF"; }
    public void setMainHeaderColorHex(String mainHeaderColorHex) { this.mainHeaderColorHex = mainHeaderColorHex != null && !mainHeaderColorHex.isEmpty() ? mainHeaderColorHex : "#0000FF"; }

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

    public String getHeaderSize() { return headerSize != null ? headerSize : PdfTemplateSettings.HEADER_SIZE_DEFAULT; }
    public void setHeaderSize(String headerSize) { this.headerSize = headerSize != null ? headerSize : PdfTemplateSettings.HEADER_SIZE_DEFAULT; }

    public String getBodyTextSize() { return bodyTextSize != null ? bodyTextSize : PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT; }
    public void setBodyTextSize(String bodyTextSize) { this.bodyTextSize = bodyTextSize != null ? bodyTextSize : PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT; }

    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }

    public List<PdfTemplateSettings.HeaderBlock> getHeaderBlocks() { return headerBlocks; }
    public void setHeaderBlocks(List<PdfTemplateSettings.HeaderBlock> headerBlocks) { this.headerBlocks = headerBlocks != null ? headerBlocks : new ArrayList<>(); }

    /** Convert to PdfTemplateSettings for PDF generation (MY_TEMPLATE with this template's content). */
    public PdfTemplateSettings toPdfTemplateSettings() {
        PdfTemplateSettings s = new PdfTemplateSettings();
        s.setTemplateSelection(PdfTemplateSettings.MY_TEMPLATE);
        s.setMainHeaderText(mainHeaderText);
        s.setMainHeaderColorHex(mainHeaderColorHex);
        s.setLogoPath(logoPath);
        s.setHeaderSize(getHeaderSize());
        s.setBodyTextSize(getBodyTextSize());
        s.setFooterText(footerText);
        s.setWatermarkEnabled(watermarkEnabled);
        s.setWatermarkType(watermarkType);
        s.setWatermarkText(watermarkText);
        s.setWatermarkImagePath(watermarkImagePath);
        s.setHeaderBlocks(headerBlocks);
        return s;
    }
}
