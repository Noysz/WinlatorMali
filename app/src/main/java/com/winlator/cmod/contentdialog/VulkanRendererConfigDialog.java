package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.widget.Spinner;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;

public class VulkanRendererConfigDialog extends ContentDialog {

    public VulkanRendererConfigDialog(Context context, Container container) {
        super(context, R.layout.vulkan_renderer_config_dialog);
        setIcon(R.drawable.icon_settings);
        setTitle("Vulkan Renderer Options");

        final Spinner sColors = findViewById(R.id.SColors);
        final Spinner sPresentMode = findViewById(R.id.SPresentMode);

        // Load colors (BGRA = 0, RGBA = 1 (swapRB))
        boolean swapRB = container != null && container.getRendererSwapRB();
        sColors.setSelection(swapRB ? 1 : 0);

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

        setOnConfirmCallback(() -> {
            if (container == null) return;
            container.setRendererSwapRB(sColors.getSelectedItemPosition() == 1);

            int sel = sPresentMode.getSelectedItemPosition();
            if (sel >= 0 && sel < pmValues.length) {
                container.setRendererPresentMode(pmValues[sel]);
            }
        });
    }
}
