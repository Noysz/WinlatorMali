package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;

import java.util.Locale;

public class VulkanRendererConfigDialog extends ContentDialog {

    public VulkanRendererConfigDialog(Context context, Container container) {
        super(context, R.layout.vulkan_renderer_config_dialog);

        final Spinner sPresentMode = findViewById(R.id.SPresentMode);
        final SeekBar sbBrightness = findViewById(R.id.SBBrightness);
        final SeekBar sbContrast = findViewById(R.id.SBContrast);
        final SeekBar sbGamma = findViewById(R.id.SBGamma);

        final TextView tvBrightness = findViewById(R.id.TVBrightness);
        final TextView tvContrast = findViewById(R.id.TVContrast);
        final TextView tvGamma = findViewById(R.id.TVGamma);

        // Load present mode
        String pm = container != null ? container.getRendererPresentMode() : "fifo";
        String[] pmValues = getContext().getResources().getStringArray(R.array.vulkan_present_mode_values);
        int pmIndex = 0;
        for (int i = 0; i < pmValues.length; i++) {
            if (pmValues[i].equalsIgnoreCase(pm)) {
                pmIndex = i;
                break;
            }
        }
        sPresentMode.setSelection(pmIndex);

        // Load colors
        float b = container != null ? container.getColorBrightness() : 0.0f;
        float c = container != null ? container.getColorContrast() : 0.0f;
        float g = container != null ? container.getColorGamma() : 1.0f;

        sbBrightness.setProgress(Math.round(b + 100f));
        sbContrast.setProgress(Math.round(c + 100f));
        sbGamma.setProgress(Math.round((g - 0.5f) * 100f));

        tvBrightness.setText(String.format(Locale.ENGLISH, "Brightness: %d", Math.round(b)));
        tvContrast.setText(String.format(Locale.ENGLISH, "Contrast: %d", Math.round(c)));
        tvGamma.setText(String.format(Locale.ENGLISH, "Gamma: %.2f", g));

        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress - 100;
                tvBrightness.setText(String.format(Locale.ENGLISH, "Brightness: %d", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress - 100;
                tvContrast.setText(String.format(Locale.ENGLISH, "Contrast: %d", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbGamma.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + (progress * 0.01f);
                tvGamma.setText(String.format(Locale.ENGLISH, "Gamma: %.2f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        setOnConfirmCallback(() -> {
            if (container == null) return;
            int sel = sPresentMode.getSelectedItemPosition();
            if (sel >= 0 && sel < pmValues.length) {
                container.setRendererPresentMode(pmValues[sel]);
            }
            container.setColorBrightness(sbBrightness.getProgress() - 100f);
            container.setColorContrast(sbContrast.getProgress() - 100f);
            container.setColorGamma(0.5f + (sbGamma.getProgress() * 0.01f));
        });
    }
}
