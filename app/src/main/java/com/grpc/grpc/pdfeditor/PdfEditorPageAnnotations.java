package com.grpc.grpc.pdfeditor;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * User drawings and text stamps for one PDF page. Coordinates are normalized 0–1 relative
 * to the page bitmap (display and export share the same aspect ratio).
 */
public final class PdfEditorPageAnnotations {

    public static final class TextStamp {
        public float nx;
        public float ny;
        public String text;
        public int color;
        /** Text size as a fraction of page height (e.g. 0.04). */
        public float relTextSize;
    }

    public static final class InkStroke {
        public final List<PointF> points = new ArrayList<>();
        public int color;
        /** Stroke width as a fraction of page height. */
        public float relStrokeWidth;
    }

    public final List<TextStamp> texts = new ArrayList<>();
    public final List<InkStroke> strokes = new ArrayList<>();
}
