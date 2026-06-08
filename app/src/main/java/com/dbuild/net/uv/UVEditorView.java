package com.dbuild.net.uv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.FileInputStream;

/**
 * Custom View that displays and allows editing of UV mapping.
 * Draws a grid representing 0-1 UV space, the UV rectangle,
 * optional texture background, and supports drag interactions.
 */
public class UVEditorView extends View {

    private static final int GRID_DIVISIONS = 16;
    private static final float CORNER_HANDLE_SIZE_DP = 12f;
    private static final float CORNER_HIT_SIZE_DP = 24f;
    private static final float MIN_UV_SIZE = 1f / GRID_DIVISIONS;

    private UVData uvData;
    private Bitmap textureBitmap;
    private boolean gridVisible = true;
    private boolean snapToGrid = false;

    private Paint gridPaint;
    private Paint gridBackgroundPaint;
    private Paint uvFillPaint;
    private Paint uvStrokePaint;
    private Paint cornerPaint;
    private Paint textPaint;
    private Paint texturePaint;
    private Paint axisLabelPaint;

    private UVChangeListener listener;

    private float cornerHandleSize;
    private float cornerHitSize;

    private enum DragMode {
        NONE, MOVE, CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR
    }

    private DragMode currentDragMode = DragMode.NONE;
    private float lastTouchX;
    private float lastTouchY;
    private float viewPadding = 32f;
    private RectF uvBounds = new RectF();

    public interface UVChangeListener {
        void onUVChanged(UVData newData);
    }

    public UVEditorView(Context context) {
        super(context);
        init(context);
    }

    public UVEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UVEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        cornerHandleSize = CORNER_HANDLE_SIZE_DP * density;
        cornerHitSize = CORNER_HIT_SIZE_DP * density;
        viewPadding = 32f * density;

        uvData = new UVData();

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.argb(60, 255, 255, 255));
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        gridBackgroundPaint = new Paint();
        gridBackgroundPaint.setColor(Color.argb(200, 30, 30, 40));
        gridBackgroundPaint.setStyle(Paint.Style.FILL);

        uvFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        uvFillPaint.setColor(Color.argb(80, 0, 180, 255));
        uvFillPaint.setStyle(Paint.Style.FILL);

        uvStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        uvStrokePaint.setColor(Color.argb(220, 0, 200, 255));
        uvStrokePaint.setStrokeWidth(2.5f * density);
        uvStrokePaint.setStyle(Paint.Style.STROKE);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.argb(240, 255, 220, 50));
        cornerPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(11f * density);

        axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisLabelPaint.setColor(Color.argb(180, 200, 200, 200));
        axisLabelPaint.setTextSize(10f * density);

        texturePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        setWillNotDraw(false);
    }

    public void setUVData(UVData data) {
        if (data == null) {
            this.uvData = new UVData();
        } else {
            this.uvData = new UVData(data);
        }
        invalidate();
    }

    public UVData getUVData() {
        return new UVData(uvData);
    }

    public void setUVChangeListener(UVChangeListener listener) {
        this.listener = listener;
    }

    public void setTexturePath(String path) {
        if (textureBitmap != null) {
            textureBitmap.recycle();
            textureBitmap = null;
        }
        if (path == null || path.isEmpty()) {
            invalidate();
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(path);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            textureBitmap = BitmapFactory.decodeStream(fis, null, opts);
            fis.close();
        } catch (Exception e) {
            textureBitmap = null;
        }
        invalidate();
    }

    public void setGridVisible(boolean visible) {
        this.gridVisible = visible;
        invalidate();
    }

    public boolean isGridVisible() {
        return gridVisible;
    }

    public void setSnapToGrid(boolean snap) {
        this.snapToGrid = snap;
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    private RectF getDrawArea() {
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h) - viewPadding * 2;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        return new RectF(left, top, left + size, top + size);
    }

    private float uvToViewX(float u, RectF area) {
        return area.left + u * area.width();
    }

    private float uvToViewY(float v, RectF area) {
        return area.top + v * area.height();
    }

    private float viewToU(float x, RectF area) {
        return (x - area.left) / area.width();
    }

    private float viewToV(float y, RectF area) {
        return (y - area.top) / area.height();
    }

    private float snapValue(float val) {
        if (!snapToGrid) return val;
        float step = 1f / GRID_DIVISIONS;
        return Math.round(val * GRID_DIVISIONS) / (float) GRID_DIVISIONS;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF area = getDrawArea();

        // Draw background
        canvas.drawRect(area, gridBackgroundPaint);

        // Draw texture background
        if (textureBitmap != null) {
            RectF texSrc = new RectF(0, 0, textureBitmap.getWidth(), textureBitmap.getHeight());
            Matrix texMatrix = new Matrix();
            texMatrix.setRectToRect(texSrc, area, Matrix.ScaleToFit.FILL);
            canvas.save();
            canvas.concat(texMatrix);
            canvas.drawBitmap(textureBitmap, 0, 0, texturePaint);
            canvas.restore();
            // Dim the texture slightly so grid and UV rect are visible
            Paint dimPaint = new Paint();
            dimPaint.setColor(Color.argb(100, 0, 0, 0));
            dimPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(area, dimPaint);
        }

        // Draw grid
        if (gridVisible) {
            drawGrid(canvas, area);
        }

        // Draw axis labels
        canvas.drawText("U \u2192", area.left, area.bottom + viewPadding * 0.6f, axisLabelPaint);
        canvas.drawText("V \u2193", area.left - viewPadding * 0.7f, area.top + viewPadding * 0.2f, axisLabelPaint);

        // Draw UV rectangle
        drawUVRectangle(canvas, area);

        // Draw corner handles
        drawCornerHandles(canvas, area);

        // Draw coordinate labels
        drawCoordinateLabels(canvas, area);
    }

    private void drawGrid(Canvas canvas, RectF area) {
        for (int i = 0; i <= GRID_DIVISIONS; i++) {
            float fraction = (float) i / GRID_DIVISIONS;
            float x = area.left + fraction * area.width();
            float y = area.top + fraction * area.height();

            if (i % 4 == 0) {
                Paint boldPaint = new Paint(gridPaint);
                boldPaint.setColor(Color.argb(100, 255, 255, 255));
                boldPaint.setStrokeWidth(gridPaint.getStrokeWidth() * 2f);
                canvas.drawLine(x, area.top, x, area.bottom, boldPaint);
                canvas.drawLine(area.left, y, area.right, y, boldPaint);
            } else {
                canvas.drawLine(x, area.top, x, area.bottom, gridPaint);
                canvas.drawLine(area.left, y, area.right, y, gridPaint);
            }
        }

        Paint borderPaint = new Paint(gridPaint);
        borderPaint.setColor(Color.argb(180, 255, 255, 255));
        borderPaint.setStrokeWidth(borderPaint.getStrokeWidth() * 2f);
        canvas.drawRect(area, borderPaint);
    }

    private void drawUVRectangle(Canvas canvas, RectF area) {
        float left = uvToViewX(uvData.getU(), area);
        float top = uvToViewY(uvData.getV(), area);
        float right = uvToViewX(uvData.getU() + uvData.getUScale(), area);
        float bottom = uvToViewY(uvData.getV() + uvData.getVScale(), area);

        uvBounds.set(left, top, right, bottom);

        if (uvData.getRotation() != 0.0f) {
            canvas.save();
            float cx = (left + right) / 2f;
            float cy = (top + bottom) / 2f;
            canvas.rotate(uvData.getRotation(), cx, cy);
            canvas.drawRect(uvBounds, uvFillPaint);
            canvas.drawRect(uvBounds, uvStrokePaint);
            canvas.restore();
        } else {
            canvas.drawRect(uvBounds, uvFillPaint);
            canvas.drawRect(uvBounds, uvStrokePaint);
        }
    }

    private void drawCornerHandles(Canvas canvas, RectF area) {
        float half = cornerHandleSize / 2f;
        float[][] corners = getCornerPositions(area);

        for (float[] corner : corners) {
            float cx = corner[0];
            float cy = corner[1];
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, cornerPaint);
        }
    }

    private float[][] getCornerPositions(RectF area) {
        float left = uvToViewX(uvData.getU(), area);
        float top = uvToViewY(uvData.getV(), area);
        float right = uvToViewX(uvData.getU() + uvData.getUScale(), area);
        float bottom = uvToViewY(uvData.getV() + uvData.getVScale(), area);

        return new float[][]{
                {left, top},     // TL
                {right, top},    // TR
                {left, bottom},  // BL
                {right, bottom}  // BR
        };
    }

    private void drawCoordinateLabels(Canvas canvas, RectF area) {
        float left = uvToViewX(uvData.getU(), area);
        float top = uvToViewY(uvData.getV(), area);
        float right = uvToViewX(uvData.getU() + uvData.getUScale(), area);
        float bottom = uvToViewY(uvData.getV() + uvData.getVScale(), area);

        String topLeftLabel = String.format("(%.3f, %.3f)", uvData.getU(), uvData.getV());
        String bottomRightLabel = String.format("(%.3f, %.3f)",
                uvData.getU() + uvData.getUScale(), uvData.getV() + uvData.getVScale());
        String sizeLabel = String.format("%.3f \u00D7 %.3f", uvData.getUScale(), uvData.getVScale());
        String rotLabel = String.format("%.1f\u00B0", uvData.getRotation());

        float density = getResources().getDisplayMetrics().density;
        float labelOffset = textPaint.getTextSize() * 0.5f;

        canvas.drawText(topLeftLabel, left + 4 * density, top - labelOffset, textPaint);
        canvas.drawText(bottomRightLabel, right + 4 * density, bottom + textPaint.getTextSize(), textPaint);

        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float sizeWidth = textPaint.measureText(sizeLabel);
        canvas.drawText(sizeLabel, cx - sizeWidth / 2f, cy, textPaint);

        if (uvData.getRotation() != 0.0f) {
            float rotWidth = textPaint.measureText(rotLabel);
            canvas.drawText(rotLabel, cx - rotWidth / 2f, cy + textPaint.getTextSize() + 4f, textPaint);
        }
    }

    private DragMode hitTest(float x, float y) {
        RectF area = getDrawArea();
        float[][] corners = getCornerPositions(area);
        float hitRadius = cornerHitSize;

        float[] tl = corners[0];
        float[] tr = corners[1];
        float[] bl = corners[2];
        float[] br = corners[3];

        if (Math.abs(x - tl[0]) < hitRadius && Math.abs(y - tl[1]) < hitRadius) {
            return DragMode.CORNER_TL;
        }
        if (Math.abs(x - tr[0]) < hitRadius && Math.abs(y - tr[1]) < hitRadius) {
            return DragMode.CORNER_TR;
        }
        if (Math.abs(x - bl[0]) < hitRadius && Math.abs(y - bl[1]) < hitRadius) {
            return DragMode.CORNER_BL;
        }
        if (Math.abs(x - br[0]) < hitRadius && Math.abs(y - br[1]) < hitRadius) {
            return DragMode.CORNER_BR;
        }

        float left = uvToViewX(uvData.getU(), area);
        float top = uvToViewY(uvData.getV(), area);
        float right = uvToViewX(uvData.getU() + uvData.getUScale(), area);
        float bottom = uvToViewY(uvData.getV() + uvData.getVScale(), area);

        if (x >= left && x <= right && y >= top && y <= bottom) {
            return DragMode.MOVE;
        }

        return DragMode.NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                currentDragMode = hitTest(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return currentDragMode != DragMode.NONE;

            case MotionEvent.ACTION_MOVE:
                if (currentDragMode == DragMode.NONE) return false;

                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                RectF area = getDrawArea();

                float dU = dx / area.width();
                float dV = dy / area.height();

                switch (currentDragMode) {
                    case MOVE:
                        uvData.setU(snapValue(uvData.getU() + dU));
                        uvData.setV(snapValue(uvData.getV() + dV));
                        break;

                    case CORNER_TL: {
                        float newU = snapValue(uvData.getU() + dU);
                        float newV = snapValue(uvData.getV() + dV);
                        float newUScale = uvData.getUScale() - (newU - uvData.getU());
                        float newVScale = uvData.getVScale() - (newV - uvData.getV());
                        if (newUScale >= MIN_UV_SIZE && newVScale >= MIN_UV_SIZE) {
                            uvData.setU(newU);
                            uvData.setV(newV);
                            uvData.setUScale(newUScale);
                            uvData.setVScale(newVScale);
                        }
                        break;
                    }

                    case CORNER_TR: {
                        float newV = snapValue(uvData.getV() + dV);
                        float newUScale = snapValue(uvData.getUScale() + dU);
                        float newVScale = uvData.getVScale() - (newV - uvData.getV());
                        if (newUScale >= MIN_UV_SIZE && newVScale >= MIN_UV_SIZE) {
                            uvData.setV(newV);
                            uvData.setUScale(newUScale);
                            uvData.setVScale(newVScale);
                        }
                        break;
                    }

                    case CORNER_BL: {
                        float newU = snapValue(uvData.getU() + dU);
                        float newUScale = uvData.getUScale() - (newU - uvData.getU());
                        float newVScale = snapValue(uvData.getVScale() + dV);
                        if (newUScale >= MIN_UV_SIZE && newVScale >= MIN_UV_SIZE) {
                            uvData.setU(newU);
                            uvData.setUScale(newUScale);
                            uvData.setVScale(newVScale);
                        }
                        break;
                    }

                    case CORNER_BR: {
                        float newUScale = snapValue(uvData.getUScale() + dU);
                        float newVScale = snapValue(uvData.getVScale() + dV);
                        if (newUScale >= MIN_UV_SIZE && newVScale >= MIN_UV_SIZE) {
                            uvData.setUScale(newUScale);
                            uvData.setVScale(newVScale);
                        }
                        break;
                    }
                }

                uvData.clampValues();
                lastTouchX = x;
                lastTouchY = y;

                if (listener != null) {
                    listener.onUVChanged(new UVData(uvData));
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentDragMode = DragMode.NONE;
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredSize = (int) (300 * getResources().getDisplayMetrics().density);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width, height;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredSize, widthSize);
        } else {
            width = desiredSize;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredSize, heightSize);
        } else {
            height = desiredSize;
        }

        setMeasuredDimension(width, height);
    }
}
