package com.winlator.cmod.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Rectangle whose corners are chamfered — cut off along a 45° diagonal — instead of rounded.
 *
 * <h3>Why this class exists</h3>
 * Nothing in the platform can express a cut corner from a drawable resource. {@code <shape>} only
 * offers {@code <corners>}, which is always an arc; {@code <vector>} can draw the outline but maps
 * its viewport independently per axis, so a chamfer stretches into a slanted wedge as soon as the
 * view is not the exact aspect ratio the path was drawn at. Material's {@code CornerFamily.CUT}
 * would do the job, but it is only read by MaterialButton, MaterialCardView and TextInputLayout,
 * none of which this UI uses, and it needs a {@code Theme.MaterialComponents} descendant while the
 * app themes descend from AppCompat.
 *
 * <p>Colors are fixed at construction. Callers build one per use rather than mutating a shared
 * instance, which is also why there are no color setters — {@link #setAlpha(int)} and
 * {@link #setColorFilter(ColorFilter)} exist only because {@link Drawable} requires them.
 */
public class CutCornerDrawable extends Drawable {
    // ANTI_ALIAS_FLAG is not optional: the diagonals are the whole point of this shape, and without
    // it they render as visible staircases at the sizes this is used at.
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final int fillColor;
    private final int strokeColor;
    private final float strokeWidthPx;
    private final float cutPx;

    private int alpha = 0xFF;
    private boolean pathDirty = true;

    public CutCornerDrawable(int fillColor, float cutPx) {
        this(fillColor, cutPx, 0f, Color.TRANSPARENT);
    }

    /**
     * @param cutPx         length cut off each corner, measured along one edge; clamped so the two
     *                      chamfers sharing a side cannot cross
     * @param strokeWidthPx {@code <= 0} for no outline
     */
    public CutCornerDrawable(int fillColor, float cutPx, float strokeWidthPx, int strokeColor) {
        this.fillColor = fillColor;
        this.cutPx = cutPx;
        this.strokeWidthPx = strokeWidthPx;
        this.strokeColor = strokeColor;

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(0f, strokeWidthPx));
        syncPaints();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty()) return;
        if (pathDirty) rebuildPath();

        if (Color.alpha(fillColor) > 0) canvas.drawPath(path, fillPaint);
        if (strokeWidthPx > 0 && Color.alpha(strokeColor) > 0) canvas.drawPath(path, strokePaint);
    }

    private void rebuildPath() {
        final Rect b = getBounds();
        // A stroke is centred on the path it follows, so half its width would fall outside the
        // bounds and be clipped away. Inset by half — the same correction GradientDrawable makes.
        final float inset = strokeWidthPx > 0 ? strokeWidthPx / 2f : 0f;
        final float left = b.left + inset;
        final float top = b.top + inset;
        final float right = b.right - inset;
        final float bottom = b.bottom - inset;

        // A cut longer than half the shorter side makes the two chamfers on that side cross, and
        // the path renders as a bow tie. Clamp rather than let it self-intersect.
        final float cut = Math.max(0f, Math.min(cutPx, Math.min(right - left, bottom - top) / 2f));

        path.reset();
        path.moveTo(left + cut, top);
        path.lineTo(right - cut, top);
        path.lineTo(right, top + cut);
        path.lineTo(right, bottom - cut);
        path.lineTo(right - cut, bottom);
        path.lineTo(left + cut, bottom);
        path.lineTo(left, bottom - cut);
        path.lineTo(left, top + cut);
        path.close();

        pathDirty = false;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        pathDirty = true;
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        syncPaints();
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return fillPaint.getColorFilter();
    }

    /**
     * Always translucent, even behind a fully opaque fill: the four cut corners are transparent by
     * definition. Claiming OPAQUE lets the framework skip drawing whatever is behind this drawable,
     * which would leave the corners as black notches.
     */
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private void syncPaints() {
        fillPaint.setColor(modulateAlpha(fillColor, alpha));
        strokePaint.setColor(modulateAlpha(strokeColor, alpha));
    }

    /** Scales a color's own alpha by {@code alpha}, matching how the framework composes the two. */
    private static int modulateAlpha(int color, int alpha) {
        int scaled = Color.alpha(color) * (alpha + (alpha >> 7)) >> 8;
        return (color & 0x00FFFFFF) | (scaled << 24);
    }
}
