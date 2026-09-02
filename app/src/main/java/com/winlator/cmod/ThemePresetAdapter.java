package com.winlator.cmod;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.core.Callback;

/**
 * Grid of theme presets for the Settings screen: each cell previews one preset and the selected one
 * is marked with a checkmark.
 *
 * <h3>Why the colors are built in Java instead of XML</h3>
 * A cell must paint the colors of the preset it represents, not of the preset currently in effect.
 * {@code ?attr/theme*} resolves against the active theme, so it can only ever produce one preset's
 * palette — useless for a picker showing eighteen. The values therefore come straight from
 * {@link ThemeManager#PRESETS}. Rounded shapes are built as {@link GradientDrawable} rather than a
 * {@code <shape>} resource for the same reason: a shape resource carries a fixed color, and
 * {@code setBackgroundColor} would replace the drawable (and its corners) instead of recoloring it.
 *
 * <p>Every color pair used here is one that ThemePreset already guarantees contrast for
 * (onSurface/surface, onPrimary/primary), so no cell can come out unreadable regardless of which
 * palettes get appended to PRESETS later.
 */
public class ThemePresetAdapter extends RecyclerView.Adapter<ThemePresetAdapter.ViewHolder> {
    private static final float SWATCH_RADIUS_DP = 4f;
    private static final float ACCENT_RADIUS_DP = 3f;
    private static final float BORDER_DP = 1f;
    private static final float BORDER_SELECTED_DP = 2f;

    private final int selectedIndex;
    private final Callback<Integer> onPick;

    /**
     * @param selectedIndex index to draw the checkmark on — pass what ThemeManager currently has
     * @param onPick invoked with the tapped index; the caller persists it and recreates the
     *               activity, so this adapter never mutates its own selection
     */
    public ThemePresetAdapter(int selectedIndex, Callback<Integer> onPick) {
        this.selectedIndex = selectedIndex;
        this.onPick = onPick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.theme_preset_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ThemePreset preset = ThemeManager.PRESETS.get(position);
        final Context context = holder.itemView.getContext();
        final boolean isSelected = position == selectedIndex;

        // Border pakai accentOnSurface waktu terpilih (dijamin 3:1 lawan surface) dan divider
        // waktu tidak — dua-duanya diukur lawan surface, yg persis warna isi swatch-nya.
        holder.swatch.setBackground(roundedRect(context, SWATCH_RADIUS_DP, preset.surface,
                isSelected ? BORDER_SELECTED_DP : BORDER_DP,
                isSelected ? preset.accentOnSurface : preset.divider));
        // primary MENTAH, bukan accentOnSurface: preview harusnya nunjukin warna brand preset
        // apa adanya, termasuk kalau kontrasnya tipis — itu informasi buat yg milih.
        holder.accent.setBackground(roundedRect(context, ACCENT_RADIUS_DP, preset.primary, 0, 0));
        holder.name.setText(preset.name);
        holder.name.setTextColor(preset.onSurface);

        holder.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        if (isSelected) holder.check.setColorFilter(preset.onPrimary);

        holder.itemView.setOnClickListener((v) -> {
            if (isSelected) return;
            onPick.call(position);
        });
    }

    @Override
    public int getItemCount() {
        return ThemeManager.PRESETS.size();
    }

    /** Rounded rectangle with an optional stroke. {@code strokeDp <= 0} means no stroke. */
    private static GradientDrawable roundedRect(Context context, float radiusDp, int fillColor,
                                                float strokeDp, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dpToPx(context, radiusDp));
        shape.setColor(fillColor);
        if (strokeDp > 0) shape.setStroke(Math.round(dpToPx(context, strokeDp)), strokeColor);
        return shape;
    }

    private static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout swatch;
        final View accent;
        final TextView name;
        final ImageView check;

        ViewHolder(View itemView) {
            super(itemView);
            swatch = itemView.findViewById(R.id.LLPresetSwatch);
            accent = itemView.findViewById(R.id.VPresetAccent);
            name = itemView.findViewById(R.id.TVPresetName);
            check = itemView.findViewById(R.id.IVPresetCheck);
        }
    }
}
