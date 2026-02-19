package com.grpc.grpc;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline PDF template settings. Stored locally (SharedPreferences).
 * Used when templateSelection is MY_TEMPLATE for Create Report.
 */
public class PdfTemplateSettings {

    public static final String GRPC = "GRPC";
    public static final String MY_TEMPLATE = "MY_TEMPLATE";

    public static final String WATERMARK_TEXT = "TEXT";
    public static final String WATERMARK_IMAGE = "IMAGE";

    public static final String BLOCK_TEXT = "TEXT";
    public static final String BLOCK_IMAGE = "IMAGE";

    public static final String STYLE_H1 = "H1";
    public static final String STYLE_H2 = "H2";
    public static final String STYLE_BODY = "BODY";

    /** GRPC or MY_TEMPLATE */
    private String templateSelection = GRPC;
    /** Local file path for logo (app internal storage). Null = use GRPC drawable. */
    private String logoPath;
    private boolean watermarkEnabled;
    /** WATERMARK_TEXT or WATERMARK_IMAGE */
    private String watermarkType = WATERMARK_TEXT;
    private String watermarkText = "";
    /** Local file path for watermark image. Used when watermarkType is IMAGE. */
    private String watermarkImagePath;
    private List<HeaderBlock> headerBlocks = new ArrayList<>();

    public String getTemplateSelection() { return templateSelection; }
    public void setTemplateSelection(String templateSelection) { this.templateSelection = templateSelection != null ? templateSelection : GRPC; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public boolean isWatermarkEnabled() { return watermarkEnabled; }
    public void setWatermarkEnabled(boolean watermarkEnabled) { this.watermarkEnabled = watermarkEnabled; }

    public String getWatermarkType() { return watermarkType; }
    public void setWatermarkType(String watermarkType) { this.watermarkType = watermarkType != null ? watermarkType : WATERMARK_TEXT; }

    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText != null ? watermarkText : ""; }

    public String getWatermarkImagePath() { return watermarkImagePath; }
    public void setWatermarkImagePath(String watermarkImagePath) { this.watermarkImagePath = watermarkImagePath; }

    public List<HeaderBlock> getHeaderBlocks() { return headerBlocks; }
    public void setHeaderBlocks(List<HeaderBlock> headerBlocks) { this.headerBlocks = headerBlocks != null ? headerBlocks : new ArrayList<>(); }

    public static class HeaderBlock {
        private String blockType = BLOCK_TEXT; // TEXT or IMAGE
        private String textStyle = STYLE_BODY;  // H1, H2, BODY
        private String text = "";
        private String imagePath;              // for IMAGE blocks

        public String getBlockType() { return blockType; }
        public void setBlockType(String blockType) { this.blockType = blockType != null ? blockType : BLOCK_TEXT; }

        public String getTextStyle() { return textStyle; }
        public void setTextStyle(String textStyle) { this.textStyle = textStyle != null ? textStyle : STYLE_BODY; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text != null ? text : ""; }

        public String getImagePath() { return imagePath; }
        public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    }
}
