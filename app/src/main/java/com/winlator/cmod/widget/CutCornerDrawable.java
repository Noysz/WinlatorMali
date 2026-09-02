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
import android.util.TypedValue;

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
 * <h3>How {@code ?attr/} is resolved (the part that is not obvious)</h3>
 * {@code ResourcesImpl.loadXmlDrawable} inflates every drawable resource with a <em>null</em> theme,
 * so a {@code ?attr/} value arrives here as an unresolved {@code TYPE_ATTRIBUTE} and reading it with
 * {@code TypedArray.getColor} throws {@code UnsupportedOperationException}. The framework's own
 * drawables do not resolve these at inflate time either: they remember the attribute ids and wait
 * for {@code Resources.loadDrawable} to call {@link #applyTheme(Resources.Theme)} with the real
 * theme. This class follows the same two-phase contract, which is also why it has a
 * {@link #getConstantState() constant state} — {@code DrawableWrapperState.canApplyTheme()} asks the
 * wrapped drawable's constant state, not the drawable, so an {@code <inset>} around a drawable
 * without one reports that it cannot be themed and the second phase is skipped in silence.
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

    private CutState state;

    private int alpha = 0xFF;
    private boolean pathDirty = true;

    /**
     * Required by {@code DrawableInflater} for {@code <com.winlator.cmod.widget.CutCornerDrawable>}
     * tags; leaves everything transparent until {@link #inflate} fills it in. Java callers want one
     * of the other two constructors.
     */
    public CutCornerDrawable() {
        this(new CutState());
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
        this(new CutState());
        state.fillColor = fillColor;
        state.strokeColor = strokeColor;
        state.strokeWidthPx = strokeWidthPx;
        state.cutTopLeftPx = cutPx;
        state.cutTopRightPx = cutPx;
        state.cutBottomRightPx = cutPx;
        state.cutBottomLeftPx = cutPx;
        syncFromState();
    }

    private CutCornerDrawable(@NonNull CutState state) {
        this.state = state;
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        syncFromState();
    }

    @Override
    public void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
                        @NonNull AttributeSet attrs, @Nullable Resources.Theme theme)
            throws XmlPullParserException, IOException {
        // Handles android:visible, which is declared on Drawable itself and not in our styleable.
        super.inflate(r, parser, attrs, theme);

        // theme is null in practice — see the class doc. The themed branch is kept for the callers
        // that do pass one (Drawable.createFromXmlInner from a themed context).
        final TypedArray a = theme != null
                ? theme.obtainStyledAttributes(attrs, R.styleable.CutCornerDrawable, 0, 0)
                : r.obtainAttributes(attrs, R.styleable.CutCornerDrawable);
        try {
            state.changingConfigurations |= a.getChangingConfigurations();
            state.presentMask = presentMask(a);
            state.themeAttrs = extractThemeAttrs(a);
            updateStateFrom(a);
        } finally {
            a.recycle();
        }
        syncFromState();
    }

    /** Which attributes the XML actually spelled out, resolved or not — {@code cutSize} needs it. */
    private static int presentMask(@NonNull TypedArray a) {
        int mask = 0;
        for (int i = 0; i < R.styleable.CutCornerDrawable.length; i++) {
            if (a.hasValue(i)) mask |= 1 << i;
        }
        return mask;
    }

    /**
     * Collects the attribute ids behind the {@code ?attr/} values the framework left unresolved,
     * indexed by position in {@code R.styleable.CutCornerDrawable}. Returns null when there is
     * nothing to defer, which is what {@link #canApplyTheme()} keys off.
     *
     * <p>{@link TypedArray#peekValue} is the only getter that reports a {@code TYPE_ATTRIBUTE}
     * instead of throwing on it, hence reading the raw value rather than calling {@code getColor}.
     */
    @Nullable
    private static int[] extractThemeAttrs(@NonNull TypedArray a) {
        int[] deferred = null;
        for (int i = 0; i < R.styleable.CutCornerDrawable.length; i++) {
            final TypedValue v = a.peekValue(i);
            if (v == null || v.type != TypedValue.TYPE_ATTRIBUTE || v.data == 0) continue;
            if (deferred == null) deferred = new int[R.styleable.CutCornerDrawable.length];
            deferred[i] = v.data;
        }
        return deferred;
    }

    private void updateStateFrom(@NonNull TypedArray a) {
        final CutState s = state;
        s.fillColor = color(a, R.styleable.CutCornerDrawable_cutFillColor, s.fillColor);
        s.strokeColor = color(a, R.styleable.CutCornerDrawable_cutStrokeColor, s.strokeColor);
        s.strokeWidthPx = dimension(a, R.styleable.CutCornerDrawable_cutStrokeWidth, s.strokeWidthPx);

        final float all = dimension(a, R.styleable.CutCornerDrawable_cutSize, 0f);
        s.cutTopLeftPx = dimension(a, R.styleable.CutCornerDrawable_cutSizeTopLeft, all);
        s.cutTopRightPx = dimension(a, R.styleable.CutCornerDrawable_cutSizeTopRight, all);
        s.cutBottomRightPx = dimension(a, R.styleable.CutCornerDrawable_cutSizeBottomRight, all);
        s.cutBottomLeftPx = dimension(a, R.styleable.CutCornerDrawable_cutSizeBottomLeft, all);
    }

    private int color(@NonNull TypedArray a, int index, int fallback) {
        return isDeferred(index) ? fallback : a.getColor(index, fallback);
    }

    private float dimension(@NonNull TypedArray a, int index, float fallback) {
        return isDeferred(index) ? fallback : a.getDimension(index, fallback);
    }

    private boolean isDeferred(int index) {
        return state.themeAttrs != null && state.themeAttrs[index] != 0;
    }

    private boolean isSpelledOut(int index) {
        return (state.presentMask & (1 << index)) != 0;
    }

    @Override
    public boolean canApplyTheme() {
        return state.themeAttrs != null || super.canApplyTheme();
    }

    @Override
    public void applyTheme(@NonNull Resources.Theme t) {
        super.applyTheme(t);
        final CutState s = state;
        if (s.themeAttrs == null) return;

        // cutSize goes first: it seeds only the corners that did not spell out a value of their own,
        // the same cascade updateStateFrom() applies.
        if (isDeferred(R.styleable.CutCornerDrawable_cutSize)) {
            final float all = resolveDimension(t, R.styleable.CutCornerDrawable_cutSize, 0f);
            if (!isSpelledOut(R.styleable.CutCornerDrawable_cutSizeTopLeft)) s.cutTopLeftPx = all;
            if (!isSpelledOut(R.styleable.CutCornerDrawable_cutSizeTopRight)) s.cutTopRightPx = all;
            if (!isSpelledOut(R.styleable.CutCornerDrawable_cutSizeBottomRight)) s.cutBottomRightPx = all;
            if (!isSpelledOut(R.styleable.CutCornerDrawable_cutSizeBottomLeft)) s.cutBottomLeftPx = all;
        }

        s.fillColor = resolveColor(t, R.styleable.CutCornerDrawable_cutFillColor, s.fillColor);
        s.strokeColor = resolveColor(t, R.styleable.CutCornerDrawable_cutStrokeColor, s.strokeColor);
        s.strokeWidthPx = resolveDimension(t, R.styleable.CutCornerDrawable_cutStrokeWidth, s.strokeWidthPx);
        s.cutTopLeftPx = resolveDimension(t, R.styleable.CutCornerDrawable_cutSizeTopLeft, s.cutTopLeftPx);
        s.cutTopRightPx = resolveDimension(t, R.styleable.CutCornerDrawable_cutSizeTopRight, s.cutTopRightPx);
        s.cutBottomRightPx = resolveDimension(t, R.styleable.CutCornerDrawable_cutSizeBottomRight, s.cutBottomRightPx);
        s.cutBottomLeftPx = resolveDimension(t, R.styleable.CutCornerDrawable_cutSizeBottomLeft, s.cutBottomLeftPx);

        // themeAttrs is deliberately kept: ResourcesImpl caches the drawable per theme, and a cache
        // hit hands out a copy that must still be resolvable against the theme it is handed.
        syncFromState();
    }

    private int resolveColor(@NonNull Resources.Theme t, int index, int fallback) {
        final TypedValue v = resolve(t, index);
        if (v == null) return fallback;
        if (v.type >= TypedValue.TYPE_FIRST_COLOR_INT && v.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return v.data;
        }
        // A color resource that is itself an XML selector resolves to its file name, not a value.
        if (v.resourceId != 0) {
            try {
                return t.getResources().getColor(v.resourceId, t);
            } catch (Resources.NotFoundException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private float resolveDimension(@NonNull Resources.Theme t, int index, float fallback) {
        final TypedValue v = resolve(t, index);
        if (v == null || v.type != TypedValue.TYPE_DIMENSION) return fallback;
        return v.getDimension(t.getResources().getDisplayMetrics());
    }

    /** Resolved value of a deferred attribute, or null if it is not deferred or the theme lacks it. */
    @Nullable
    private TypedValue resolve(@NonNull Resources.Theme t, int index) {
        if (!isDeferred(index)) return null;
        final TypedValue v = new TypedValue();
        return t.resolveAttribute(state.themeAttrs[index], v, true) ? v : null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty()) return;
        if (pathDirty) rebuildPath();

        if (Color.alpha(state.fillColor) > 0) canvas.drawPath(path, fillPaint);
        if (state.strokeWidthPx > 0 && Color.alpha(state.strokeColor) > 0) {
            canvas.drawPath(path, strokePaint);
        }
    }

    private void rebuildPath() {
        final Rect b = getBounds();
        // A stroke is centred on the path it follows, so half its width would fall outside the
        // bounds and be clipped away. Inset by half — the same correction GradientDrawable makes.
        final float inset = state.strokeWidthPx > 0 ? state.strokeWidthPx / 2f : 0f;
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

        final float tl0 = Math.max(0f, state.cutTopLeftPx);
        final float tr0 = Math.max(0f, state.cutTopRightPx);
        final float br0 = Math.max(0f, state.cutBottomRightPx);
        final float bl0 = Math.max(0f, state.cutBottomLeftPx);

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

    @Nullable
    @Override
    public ConstantState getConstantState() {
        return state;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | state.changingConfigurations;
    }

    /** Pushes {@link #state} into the paints and invalidates the cached path. */
    private void syncFromState() {
        strokePaint.setStrokeWidth(Math.max(0f, state.strokeWidthPx));
        syncPaints();
        pathDirty = true;
    }

    private void syncPaints() {
        fillPaint.setColor(modulateAlpha(state.fillColor, alpha));
        strokePaint.setColor(modulateAlpha(state.strokeColor, alpha));
    }

    /** Scales a color's own alpha by {@code alpha}, matching how the framework composes the two. */
    private static int modulateAlpha(int color, int alpha) {
        int scaled = Color.alpha(color) * (alpha + (alpha >> 7)) >> 8;
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    /**
     * The shape itself, shared between copies of the drawable.
     *
     * <p>Having one is what lets a {@code ?attr/} colour survive: {@code DrawableWrapperState}
     * decides whether an {@code <inset>} can be themed by asking its child's constant state, and 16
     * of the 18 drawables built from this class sit inside an {@code <inset>}. A null constant state
     * makes the wrapper answer "no" and {@link #applyTheme} is then never called, leaving every
     * themed fill transparent.
     */
    private static final class CutState extends ConstantState {
        int fillColor = Color.TRANSPARENT;
        int strokeColor = Color.TRANSPARENT;
        float strokeWidthPx;
        float cutTopLeftPx;
        float cutTopRightPx;
        float cutBottomRightPx;
        float cutBottomLeftPx;

        /** Attribute ids left unresolved at inflate time, by styleable index; null if none. */
        @Nullable
        int[] themeAttrs;
        /** Bit per styleable index the XML spelled out. */
        int presentMask;
        int changingConfigurations;

        CutState() {
        }

        CutState(@NonNull CutState orig) {
            fillColor = orig.fillColor;
            strokeColor = orig.strokeColor;
            strokeWidthPx = orig.strokeWidthPx;
            cutTopLeftPx = orig.cutTopLeftPx;
            cutTopRightPx = orig.cutTopRightPx;
            cutBottomRightPx = orig.cutBottomRightPx;
            cutBottomLeftPx = orig.cutBottomLeftPx;
            // Shared, not copied: nothing writes to this array after inflate.
            themeAttrs = orig.themeAttrs;
            presentMask = orig.presentMask;
            changingConfigurations = orig.changingConfigurations;
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return new CutCornerDrawable(new CutState(this));
        }

        @Override
        public int getChangingConfigurations() {
            return changingConfigurations;
        }

        @Override
        public boolean canApplyTheme() {
            return themeAttrs != null;
        }
    }
}
