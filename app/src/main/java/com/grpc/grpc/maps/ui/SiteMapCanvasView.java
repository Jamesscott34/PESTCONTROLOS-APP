package com.grpc.grpc.maps.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SiteMapCanvasView extends View {
    public static final int TOOL_LINE = 0;
    public static final int TOOL_INTERNAL = 1;
    public static final int TOOL_EXTERNAL = 2;
    public static final int TOOL_FLY = 3;
    public static final int TOOL_INSECT = 4;
    public static final int TOOL_LABEL = 5;
    public static final int TOOL_ERASE = 6;
    public static final int TOOL_SHAPE = 7;

    public static final int SHAPE_RECTANGLE = 0;
    public static final int SHAPE_SQUARE = 1;
    public static final int SHAPE_CIRCLE = 2;
    public static final int SHAPE_TRIANGLE = 3;
    public static final int SHAPE_DIAMOND = 4;

    private static final float MARKER_RADIUS = 24f;
    private static final float DELETE_THRESHOLD = 60f;
    private static final float MIN_LINE_LENGTH = 10f;
    private static final float DEFAULT_SHAPE_SIZE = 90f;
    private static final float MIN_SHAPE_SIZE = 30f;
    private static final float MAX_SHAPE_SIZE = 220f;

    private final Paint backgroundPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<MapElement> elements = new ArrayList<>();
    private int activeTool = TOOL_LINE;
    private Float pendingLineStartX;
    private Float pendingLineStartY;
    private Float pendingLineCurrentX;
    private Float pendingLineCurrentY;
    private String pendingLabelText = "";
    private int pendingShapeType = SHAPE_RECTANGLE;
    private float pendingShapeSize = DEFAULT_SHAPE_SIZE;
    private ShapeElement selectedShape;
    private OnShapeSizeChangedListener shapeSizeChangedListener;
    private Bitmap backgroundImage = null;

    public SiteMapCanvasView(Context context) {
        super(context);
        init();
    }

    public SiteMapCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SiteMapCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint.setColor(Color.WHITE);

        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);

        borderPaint.setColor(Color.BLACK);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setStyle(Paint.Style.STROKE);

        pendingPaint.setColor(Color.GRAY);
        pendingPaint.setStyle(Paint.Style.STROKE);
        pendingPaint.setStrokeWidth(4f);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(32f);
        textPaint.setStyle(Paint.Style.FILL);

        markerNumberPaint.setColor(Color.WHITE);
        markerNumberPaint.setTextSize(24f);
        markerNumberPaint.setTextAlign(Paint.Align.CENTER);

        selectedPaint.setColor(Color.parseColor("#AA6200EE"));
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(4f);
    }

    public void setActiveTool(int tool) {
        activeTool = tool;
        clearPendingLine();
        notifyShapeSizeChanged();
        invalidate();
    }

    public void setPendingLabelText(String labelText) {
        pendingLabelText = labelText != null ? labelText.trim() : "";
    }

    public void setPendingShapeType(int shapeType) {
        pendingShapeType = shapeType;
        invalidate();
    }

    public void setOnShapeSizeChangedListener(OnShapeSizeChangedListener listener) {
        shapeSizeChangedListener = listener;
    }

    public void setBackgroundImage(Bitmap bitmap) {
        this.backgroundImage = bitmap;
        invalidate();
    }

    public void adjustShapeSize(float delta) {
        if (selectedShape != null) {
            selectedShape.size = clampShapeSize(selectedShape.size + delta);
            pendingShapeSize = selectedShape.size;
        } else {
            pendingShapeSize = clampShapeSize(pendingShapeSize + delta);
        }
        notifyShapeSizeChanged();
        invalidate();
    }

    public String getShapeSizeLabel() {
        float size = selectedShape != null ? selectedShape.size : pendingShapeSize;
        return String.valueOf(Math.round(size));
    }

    public void undoLast() {
        if (!elements.isEmpty()) {
            elements.remove(elements.size() - 1);
            invalidate();
        }
    }

    public void clearAll() {
        elements.clear();
        clearPendingLine();
        selectedShape = null;
        notifyShapeSizeChanged();
        invalidate();
    }

    public boolean hasContent() {
        return !elements.isEmpty();
    }

    public Bitmap renderToBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        if (backgroundImage != null) {
            android.graphics.RectF dst = new android.graphics.RectF(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(backgroundImage, null, dst, null);
        }

        for (MapElement element : elements) {
            element.draw(canvas, linePaint, markerPaint, borderPaint, textPaint, markerNumberPaint);
        }

        if (selectedShape != null) {
            selectedShape.drawSelection(canvas, selectedPaint);
        }

        if (activeTool == TOOL_LINE && pendingLineStartX != null && pendingLineStartY != null) {
            canvas.drawCircle(pendingLineStartX, pendingLineStartY, MARKER_RADIUS / 3f, borderPaint);
            if (pendingLineCurrentX != null && pendingLineCurrentY != null) {
                canvas.drawLine(pendingLineStartX, pendingLineStartY, pendingLineCurrentX, pendingLineCurrentY, pendingPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (activeTool) {
            case TOOL_LINE:
                handleLineTouch(event, x, y);
                break;
            case TOOL_LABEL:
                if (event.getAction() == MotionEvent.ACTION_DOWN && !pendingLabelText.isEmpty()) {
                    selectedShape = null;
                    notifyShapeSizeChanged();
                    elements.add(new TextElement(x, y, pendingLabelText));
                    invalidate();
                }
                break;
            case TOOL_ERASE:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    selectedShape = null;
                    removeNearestElement(x, y);
                }
                break;
            case TOOL_SHAPE:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ShapeElement tappedShape = findNearestShape(x, y);
                    if (tappedShape != null && tappedShape.distanceTo(x, y) <= DELETE_THRESHOLD) {
                        selectedShape = tappedShape;
                    } else {
                        ShapeElement newShape = new ShapeElement(x, y, pendingShapeType, pendingShapeSize);
                        elements.add(newShape);
                        selectedShape = newShape;
                    }
                    notifyShapeSizeChanged();
                    invalidate();
                }
                break;
            default:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    selectedShape = null;
                    notifyShapeSizeChanged();
                    elements.add(new CircleElement(x, y, colorForTool(activeTool), nextMarkerNumber(activeTool)));
                    invalidate();
                }
                break;
        }

        return true;
    }

    private void handleLineTouch(MotionEvent event, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pendingLineStartX = x;
                pendingLineStartY = y;
                pendingLineCurrentX = x;
                pendingLineCurrentY = y;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (pendingLineStartX != null && pendingLineStartY != null) {
                    pendingLineCurrentX = x;
                    pendingLineCurrentY = y;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pendingLineStartX != null && pendingLineStartY != null) {
                    pendingLineCurrentX = x;
                    pendingLineCurrentY = y;
                    float dx = x - pendingLineStartX;
                    float dy = y - pendingLineStartY;
                    if (Math.hypot(dx, dy) >= MIN_LINE_LENGTH) {
                        elements.add(new LineElement(pendingLineStartX, pendingLineStartY, x, y));
                    }
                }
                clearPendingLine();
                invalidate();
                break;
        }
    }

    private int colorForTool(int tool) {
        switch (tool) {
            case TOOL_INTERNAL:
                return Color.GREEN;
            case TOOL_EXTERNAL:
                return Color.RED;
            case TOOL_FLY:
                return Color.BLUE;
            case TOOL_INSECT:
            default:
                return Color.BLACK;
        }
    }

    private int nextMarkerNumber(int tool) {
        int count = 0;
        for (MapElement element : elements) {
            if (element instanceof CircleElement) {
                CircleElement circle = (CircleElement) element;
                if (circle.toolType == tool) {
                    count++;
                }
            }
        }
        return count + 1;
    }

    private void removeNearestElement(float x, float y) {
        int nearestIndex = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < elements.size(); i++) {
            float distance = elements.get(i).distanceTo(x, y);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        if (nearestIndex >= 0 && nearestDistance <= DELETE_THRESHOLD) {
            if (elements.get(nearestIndex) == selectedShape) {
                selectedShape = null;
            }
            elements.remove(nearestIndex);
            notifyShapeSizeChanged();
            invalidate();
        }
    }

    private ShapeElement findNearestShape(float x, float y) {
        ShapeElement nearestShape = null;
        float nearestDistance = Float.MAX_VALUE;
        for (MapElement element : elements) {
            if (element instanceof ShapeElement) {
                float distance = element.distanceTo(x, y);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestShape = (ShapeElement) element;
                }
            }
        }
        return nearestShape;
    }

    private void clearPendingLine() {
        pendingLineStartX = null;
        pendingLineStartY = null;
        pendingLineCurrentX = null;
        pendingLineCurrentY = null;
    }

    private static float clampShapeSize(float size) {
        return Math.max(MIN_SHAPE_SIZE, Math.min(MAX_SHAPE_SIZE, size));
    }

    private void notifyShapeSizeChanged() {
        if (shapeSizeChangedListener != null) {
            shapeSizeChangedListener.onShapeSizeChanged(getShapeSizeLabel());
        }
    }

    private interface MapElement {
        void draw(Canvas canvas, Paint linePaint, Paint markerPaint, Paint borderPaint, Paint textPaint, Paint markerNumberPaint);
        float distanceTo(float x, float y);
    }

    private static final class LineElement implements MapElement {
        private final float startX;
        private final float startY;
        private final float endX;
        private final float endY;

        private LineElement(float startX, float startY, float endX, float endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        @Override
        public void draw(Canvas canvas, Paint linePaint, Paint markerPaint, Paint borderPaint, Paint textPaint, Paint markerNumberPaint) {
            canvas.drawLine(startX, startY, endX, endY, linePaint);
        }

        @Override
        public float distanceTo(float x, float y) {
            return distanceToSegment(x, y, startX, startY, endX, endY);
        }
    }

    private static final class CircleElement implements MapElement {
        private final float centerX;
        private final float centerY;
        private final int color;
        private final int toolType;
        private final int number;

        private CircleElement(float centerX, float centerY, int color, int number) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.color = color;
            this.toolType = colorToTool(color);
            this.number = number;
        }

        @Override
        public void draw(Canvas canvas, Paint linePaint, Paint markerPaint, Paint borderPaint, Paint textPaint, Paint markerNumberPaint) {
            markerPaint.setStyle(Paint.Style.FILL);
            markerPaint.setColor(color);
            canvas.drawCircle(centerX, centerY, MARKER_RADIUS, markerPaint);
            canvas.drawCircle(centerX, centerY, MARKER_RADIUS, borderPaint);
            Paint.FontMetrics metrics = markerNumberPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(number), centerX, baseline, markerNumberPaint);
        }

        @Override
        public float distanceTo(float x, float y) {
            return (float) Math.hypot(x - centerX, y - centerY);
        }

        private static int colorToTool(int color) {
            if (color == Color.GREEN) return TOOL_INTERNAL;
            if (color == Color.RED) return TOOL_EXTERNAL;
            if (color == Color.BLUE) return TOOL_FLY;
            return TOOL_INSECT;
        }
    }

    private static final class TextElement implements MapElement {
        private final float x;
        private final float y;
        private final String text;

        private TextElement(float x, float y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }

        @Override
        public void draw(Canvas canvas, Paint linePaint, Paint markerPaint, Paint borderPaint, Paint textPaint, Paint markerNumberPaint) {
            canvas.drawText(text, x, y, textPaint);
        }

        @Override
        public float distanceTo(float touchX, float touchY) {
            return (float) Math.hypot(touchX - x, touchY - y);
        }
    }

    private static final class ShapeElement implements MapElement {
        private final float centerX;
        private final float centerY;
        private final int shapeType;
        private float size;

        private ShapeElement(float centerX, float centerY, int shapeType, float size) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.shapeType = shapeType;
            this.size = clampShapeSize(size);
        }

        @Override
        public void draw(Canvas canvas, Paint linePaint, Paint markerPaint, Paint borderPaint, Paint textPaint, Paint markerNumberPaint) {
            borderPaint.setStyle(Paint.Style.STROKE);
            switch (shapeType) {
                case SHAPE_CIRCLE:
                    canvas.drawCircle(centerX, centerY, size / 2f, borderPaint);
                    break;
                case SHAPE_TRIANGLE:
                    canvas.drawPath(buildTrianglePath(), borderPaint);
                    break;
                case SHAPE_DIAMOND:
                    canvas.drawPath(buildDiamondPath(), borderPaint);
                    break;
                case SHAPE_SQUARE:
                    canvas.drawRect(buildRect(), borderPaint);
                    break;
                case SHAPE_RECTANGLE:
                default:
                    RectF rect = buildRect();
                    canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, borderPaint);
                    break;
            }
        }

        @Override
        public float distanceTo(float x, float y) {
            return (float) Math.hypot(x - centerX, y - centerY);
        }

        private RectF buildRect() {
            float halfWidth = shapeType == SHAPE_RECTANGLE ? size * 0.65f : size / 2f;
            float halfHeight = shapeType == SHAPE_RECTANGLE ? size * 0.4f : size / 2f;
            return new RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
        }

        private Path buildTrianglePath() {
            float half = size / 2f;
            Path path = new Path();
            path.moveTo(centerX, centerY - half);
            path.lineTo(centerX - half, centerY + half);
            path.lineTo(centerX + half, centerY + half);
            path.close();
            return path;
        }

        private Path buildDiamondPath() {
            float half = size / 2f;
            Path path = new Path();
            path.moveTo(centerX, centerY - half);
            path.lineTo(centerX - half, centerY);
            path.lineTo(centerX, centerY + half);
            path.lineTo(centerX + half, centerY);
            path.close();
            return path;
        }

        private void drawSelection(Canvas canvas, Paint selectionPaint) {
            float pad = 12f;
            RectF rect = buildRect();
            switch (shapeType) {
                case SHAPE_CIRCLE:
                    canvas.drawCircle(centerX, centerY, (size / 2f) + pad, selectionPaint);
                    break;
                case SHAPE_TRIANGLE:
                case SHAPE_DIAMOND:
                    canvas.drawRect(centerX - (size / 2f) - pad, centerY - (size / 2f) - pad,
                            centerX + (size / 2f) + pad, centerY + (size / 2f) + pad, selectionPaint);
                    break;
                case SHAPE_SQUARE:
                case SHAPE_RECTANGLE:
                default:
                    canvas.drawRect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad, selectionPaint);
                    break;
            }
        }
    }

    private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return (float) Math.hypot(px - x1, py - y1);
  