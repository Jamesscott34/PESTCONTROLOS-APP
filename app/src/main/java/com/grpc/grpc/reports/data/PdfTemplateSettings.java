package com.grpc.grpc.reports.data;

import com.grpc.grpc.core.*;

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

    /** When logoPath equals this, use the same default logo drawable as PDFReportGenerator (GRPC). */
    public static final String USE_DEFAULT_LOGO = "DEFAULT";

    /** Header size: DEFAULT, BIGGER, SMALLER (affects main header and body section labels in PDF). */
    public static final String HEADER_SIZE_DEFAULT = "DEFAULT";
    public static final String HEADER_SIZE_BIGGER = "BIGGER";
    public static final String HEADER_SIZE_SMALLER = "SMALLER";

    /** Body text size: DEFAULT (12pt) or numeric "8", "10", "12", "14". Never exceeds header/section label size. */
    public static final String BODY_TEXT_SIZE_DEFAULT = "DEFAULT";
    /** Legacy; maps to 10pt. */
    public static final String BODY_TEXT_SIZE_SMALLER = "SMALLER";
    /** Legacy; maps to 14pt. */
    public static final String BODY_TEXT_SIZE_BIGGER = "BIGGER";

    /** GRPC or MY_TEMPLATE */
    private String templateSelection = GRPC;
    /** Main header text at top of PDF (e.g. "Good Riddance Pest Control"). User can set whatever they want. */
    private String mainHeaderText = "";
    /** Main header colour as hex (e.g. "#0000FF" for blue). Default blue. */
    private String mainHeaderColorHex = "#0000FF";
    /** Local file path for logo (app internal storage). Null = no logo (empty). */
    private String logoPath;
    private boolean watermarkEnabled;
    /** WATERMARK_TEXT or WATERMARK_IMAGE */
    private String watermarkType = WATERMARK_TEXT;
    private String watermarkText = "";
    /** Local file path for watermark image. Used when watermarkType is IMAGE. */
    private String watermarkImagePath;
    /** HEADER_SIZE_DEFAULT, HEADER_SIZE_BIGGER, or HEADER_SIZE_SMALLER */
    private String headerSize = HEADER_SIZE_DEFAULT;
    /** BODY_TEXT_SIZE_DEFAULT, BODY_TEXT_SIZE_SMALLER, or BODY_TEXT_SIZE_BIGGER. Body text never exceeds header size. */
    private String bodyTextSize = BODY_TEXT_SIZE_DEFAULT;
    /** Custom footer text. Null or empty = use default "Created by reporting system". */
    private String footerText;
    private List<HeaderBlock> headerBlocks = new ArrayList<>();

    public String getTemplateSelection() { return templateSelection; }
    public void setTemplateSelection(String templateSelection) { this.templateSelection = templateSelection != null ? templateSelection : GRPC; }

    public String getMainHeaderText() { return mainHeaderText != null ? mainHeaderText : ""; }
    public void setMainHeaderText(String mainHeaderText) { this.mainHeaderText = mainHeaderText != null ? mainHeaderText : ""; }

    public String getMainHeaderColorHex() { return mainHeaderColorHex != null ? mainHeaderColorHex : "#0000FF"; }
    public void setMainHeaderColorHex(String mainHeaderColorHex) { this.mainHeaderColorHex = mainHeaderColorHex != null && !mainHeaderColorHex.isEmpty() ? mainHeaderColorHex : "#0000FF"; }

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

    public String getHeaderSize() { return headerSize != null ? headerSize : HEADER_SIZE_DEFAULT; }
    public void setHeaderSize(String headerSize) { this.headerSize = headerSize != null ? headerSize : HEADER_SIZE_DEFAULT; }

    public String getBodyTextSize() { return bodyTextSize != null ? bodyTextSize : BODY_TEXT_SIZE_DEFAULT; }
    public void setBodyTextSize(String bodyTextSize) { this.bodyTextSize = bodyTextSize != null ? bodyTextSize : BODY_TEXT_SIZE_DEFAULT; }

    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }

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
