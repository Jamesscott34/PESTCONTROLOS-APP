package com.grpc.grpc.pdfeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws annotations for one page and captures text taps or ink strokes.
 */
public class PdfEditorOverlayView extends View {

    public enum EditMode {
        TEXT,
        DRAW
    }

    public interface TextRequestListener {
        void onRequestText(float nx, float ny);
    }

    private EditMode mode = EditMode.DRAW;
    private int inkColor = 0xFF000000;
    private float strokePx;
    private PdfEditorPageAnnotations annotations;
    @Nullable
    private PdfEditorPageAnnotations.InkStroke currentStroke;
    private TextRequestListener textRequestListener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final GestureDetector tapDetector;

    public PdfEditorOverlayView(Context context) {
        super(context);
        strokePx = dp(4f);
        tapDetector = createTapDetector(context);
    }

    public PdfEditorOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokePx = dp(4f);
        tapDetector = createTapDetector(context);
    }

    private GestureDetector createTapDetector(Context context) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (mode != EditMode.TEXT || textRequestListener == null) {
                    return false;
                }
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) {
                    return false;
                }
                textRequestListener.onRequestText(e.getX() / w, e.getY() / h);
                return true;
            }
        });
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setEditMode(EditMode mode) {
        this.mode = mode;
    }

    public void setInkColor(int color) {
        this.inkColor = color;
    }

    public void setAnnotations(@Nullable PdfEditorPageAnnotations annotations) {
        this.annotations = annotations;
        invalidate();
    }

    public void setTextRequestListener(@Nullable TextRequestListener textRequestListener) {
        this.textRequestListener = textRequestListener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (annotations == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        for (PdfEditorPageAnnotations.TextStamp t : annotations.texts) {
            if (t.text == null || t.text.isEmpty()) {
                continue;
            }
            paint.setColor(t.color);
            paint.setTextSize(Math.max(8f, t.relTextSize * h));
            canvas.drawText(t.text, t.nx * w, t.ny * h, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (PdfEditorPageAnnotations.InkStroke s : annotations.strokes) {
            drawStroke(canvas, w, h, s);
        }
        if (currentStroke != null) {
            drawStroke(canvas, w, h, currentStroke);
        }
    }

    private void drawStroke(Canvas canvas, int w, int h, PdfEditorPageAnnotations.InkStroke s) {
        if (s.points.isEmpty()) {
            return;
        }
        paint.setColor(s.color);
        paint.setStrokeWidth(Math.max(1f, s.relStrokeWidth * h));
        path.reset();
        float x0 = s.points.get(0).x * w;
        float y0 = s.points.get(0).y * h;
        path.moveTo(x0, y0);
        for (int i = 1; i < s.points.size(); i++) {
            path.lineTo(s.points.get(i).x * w, s.points.get(i).y * h);
        }
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        if (mode == EditMode.TEXT) {
            boolean handled = tapDetector.onTouchEvent(event);
            return handled || super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                currentStroke = new PdfEditorPageAnnotations.InkStroke();
                currentStroke.color = inkColor;
                currentStroke.relStrokeWidth = strokePx / h;
                appendPoint(currentStroke, event, w, h);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentStroke != null) {
                    appendPoint(currentStroke, event, w, h);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (currentStroke != null && annotations != null) {
                    if (currentStroke.points.size() > 1) {
                        annotations.strokes.add(currentStroke);
                    }
                    currentStroke = null;
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private static void appendPoint(PdfEditorPageAnnotations.InkStroke stroke, MotionEvent e, int w, int h) {
        float nx = e.getX() / w;
        float ny = e.getY() / h;
        if (!stroke.points.isEmpty()) {
            float lx = stroke.points.get(stroke.points.size() - 1).x;
            float ly = stroke.points.get(stroke.points.size() - 1).y;
            float dx = nx - lx;
            float dy = ny - ly;
            if (dx * dx + dy * dy < 1e-6f) {
                return;
            }
        }
        stroke.points.add(new android.graphics.PointF(nx, ny));
    }
}
