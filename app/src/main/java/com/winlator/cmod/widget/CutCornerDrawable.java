package com.winlator.cmod.widget;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.cmod.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

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
 * <h3>Use from a drawable resource</h3>
 * The class name is the tag, and the attributes need the {@code res-auto} namespace:
 * <pre>{@code
 * <com.winlator.cmod.widget.CutCornerDrawable
 *     xmlns:app="http://schemas.android.com/apk/res-auto"
 *     app:cutFillColor="?attr/themeSurface"
 *     app:cutSize="@dimen/cut_corner_small" />
 * }</pre>
 * {@link android.graphics.drawable.DrawableInflater} reaches this through {@code inflateFromClass},
 * which needs the {@linkplain #CutCornerDrawable() public no-arg constructor} below and loads the
 * class reflectively — hence {@link Keep}, since nothing in compiled code references it. Note that
 * aapt2 does not validate drawable tag names, so a typo in the tag survives the build and only
 * throws when the drawable is first inflated.
 *
 * <p>Colors are fixed once set. Callers that build one in Java make a fresh instance per use rather
 * than mutating a shared one, which is also why there are no color setters — {@link #setAlpha(int)}
 * and {@link #setColorFilter(ColorFilter)} exist only because {@link Drawable} requires them.
 */
@Keep
public class CutCornerDrawable extends Drawable {
    // ANTI_ALIAS_FLAG is not optional: the diagonals are the whole point of this shape, and without
    // it they render as visible staircases at the sizes this is used at.
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int fillColor = Color.TRANSPARENT;
    private int strokeColor = Color.TRANSPARENT;
    private float strokeWidthPx;
    private float cutTopLeftPx;
    private float cutTopRightPx;
    private float cutBottomRightPx;
    private float cutBottomLeftPx;

    private int alpha = 0xFF;
    private boolean pathDirty = true;

    /**
     * Required by {@code DrawableInflater} for {@code <com.winlator.cmod.widget.CutCornerDrawable>}
     * tags; leaves everything transparent until {@link #inflate} fills it in. Java callers want one
     * of the other two constructors.
     */
    public CutCornerDrawable() {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    public CutCornerDrawable(int fillColor, float cutPx) {
        this(fillColor, cutPx, 0f, Color.TRANSPARENT);
    }

    /**
     * @param cutPx         length cut off each corner, measured along one edge; clamped so the two
     *                      chamfers sharing a side cannot cross
     * @param strokeWidthPx {@code <= 0} for no outline
     */
    public CutCornerDrawable(int fillColor, float cutPx, float strokeWidthPx, int strokeColor) {
        this();
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.strokeWidthPx = strokeWidthPx;
        this.cutTopLeftPx = cutPx;
        this.cutTopRightPx = cutPx;
        this.cutBottomRightPx = cutPx;
        this.cutBottomLeftPx = cutPx;

        strokePaint.setStrokeWidth(Math.max(0f, strokeWidthPx));
        syncPaints();
    }

    @Override
    public void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
                        @NonNull AttributeSet attrs, @Nullable Resources.Theme theme)
            throws XmlPullParserException, IOException {
        // Handles android:visible, which is declared on Drawable itself and not in our styleable.
        super.inflate(r, parser, attrs, theme);

        // Going through the theme is what makes `?attr/themeSurface` resolvable here; the plain
        // Resources path (no theme) would hand back the unresolved attribute reference instead.
        final TypedArray a = theme != null
                ? theme.obtainStyledAttributes(attrs, R.styleable.CutCornerDrawable, 0, 0)
                : r.obtainAttributes(attrs, R.styleable.CutCornerDrawable);
        try {
            fillColor = a.getColor(R.styleable.CutCornerDrawable_cutFillColor, Color.TRANSPARENT);
            strokeColor = a.getColor(R.styleable.CutCornerDrawable_cutStrokeColor, Color.TRANSPARENT);
            strokeWidthPx = a.getDimension(R.styleable.CutCornerDrawable_cutStrokeWidth, 0f);

            final float all = a.getDimension(R.styleable.CutCornerDrawable_cutSize, 0f);
            cutTopLeftPx = a.getDimension(R.styleable.CutCornerDrawable_cutSizeTopLeft, all);
            cutTopRightPx = a.getDimension(R.styleable.CutCornerDrawable_cutSizeTopRight, all);
            cutBottomRightPx = a.getDimension(R.styleable.CutCornerDrawable_cutSizeBottomRight, all);
            cutBottomLeftPx = a.getDimension(R.styleable.CutCornerDrawable_cutSizeBottomLeft, all);
        } finally {
            a.recycle();
        }

        strokePaint.setStrokeWidth(Math.max(0f, strokeWidthPx));
        syncPaints();
        pathDirty = true;
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

        path.reset();
        pathDirty = false;

        final float w = right - left;
        final float h = bottom - top;
        // A stroke wider than the bounds inverts them; nothing sensible to draw.
        if (w <= 0 || h <= 0) return;

        final float tl0 = Math.max(0f, cutTopLeftPx);
        final float tr0 = Math.max(0f, cutTopRightPx);
        final float br0 = Math.max(0f, cutBottomRightPx);
        final float bl0 = Math.max(0f, cutBottomLeftPx);

        // Two chamfers sharing a side cannot be longer together than that side, or the path crosses
        // itself and renders as a bow tie. Shrink all four proportionally by the worst offender —
        // the same treatment RoundRectShape gives oversized radii. Clamping each corner to
        // min(w,h)/2 in isolation would be simpler but also rejects legal shapes: the 4dp-tall
        // button bevel band needs a 4dp bottom cut, which fits only because its top cuts are 0.
        float scale = 1f;
        scale = shrink(scale, w, tl0 + tr0);
        scale = shrink(scale, h, tr0 + br0);
        scale = shrink(scale, w, br0 + bl0);
        scale = shrink(scale, h, bl0 + tl0);

        final float tl = tl0 * scale;
        final float tr = tr0 * scale;
        final float br = br0 * scale;
        final float bl = bl0 * scale;

        path.moveTo(left + tl, top);
        path.lineTo(right - tr, top);
        path.lineTo(right, top + tr);
        path.lineTo(right, bottom - br);
        path.lineTo(right - br, bottom);
        path.lineTo(left + bl, bottom);
        path.lineTo(left, bottom - bl);
        path.lineTo(left, top + tl);
        path.close();
    }

    /** Reduces {@code scale} so that {@code sum} scaled by it still fits inside {@code side}. */
    private static float shrink(float scale, float side, float sum) {
        return sum > side ? Math.min(scale, side / sum) : scale;
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

    // getConstantState() is deliberately left returning null: ResourcesImpl only caches a drawable
    // that reports one, so every inflation re-reads the ?attr/ colors against the theme in effect
    // right now. Costs a re-parse per inflation, buys immunity to a cached drawable outliving a
    // preset switch.

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
