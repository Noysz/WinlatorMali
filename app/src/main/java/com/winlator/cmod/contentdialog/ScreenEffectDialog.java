package com.winlator.cmod.contentdialog;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.vulkan.VulkanRenderer;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.widget.SeekBar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ScreenEffectDialog extends ContentDialog {

    private final XServerDisplayActivity activity;
    private final CheckBox cbEnableCRTShader;
    private final CheckBox cbEnableFXAA;
    private final CheckBox cbEnableToonShader;
    private final CheckBox cbEnableNTSCEffect;
    private final SharedPreferences preferences;
    private final Spinner sProfile;
    private final SeekBar sbBrightness;
    private final SeekBar sbContrast;
    private final SeekBar sbGamma;
    private final SeekBar sbSaturation;

    private static final String TAG = "ScreenEffectDialog";


    public ScreenEffectDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.screen_effect_dialog);
        this.activity = activity;

        preferences = PreferenceManager.getDefaultSharedPreferences(activity);

        boolean isDarkMode = preferences.getBoolean("dark_mode", true);

        TextView lblColorAdjustment = findViewById(R.id.LBLColorAdjustment);
        applyFieldSetLabelStyle(lblColorAdjustment, isDarkMode);

        sProfile = findViewById(R.id.SProfile);
        sbBrightness = findViewById(R.id.SBBrightness);
        sbContrast = findViewById(R.id.SBContrast);
        sbGamma = findViewById(R.id.SBGamma);
        sbSaturation = findViewById(R.id.SBSaturation);
        cbEnableFXAA = findViewById(R.id.CBEnableFXAA);
        cbEnableCRTShader = findViewById(R.id.CBEnableCRTShader);

        cbEnableToonShader = findViewById(R.id.CBEnableToonShader);
        cbEnableNTSCEffect = findViewById(R.id.CBEnableNTSCEffect);


        HostRenderer hostRenderer = activity.getXServerView().getRenderer();
        if (hostRenderer == null) {
            Log.e(TAG, "Renderer is null in ScreenEffectDialog initialization!");
            return;
        }

        if (hostRenderer instanceof GLRenderer) {
            GLRenderer renderer = (GLRenderer) hostRenderer;
            ColorEffect colorEffect = (ColorEffect) renderer.getEffectComposer().getEffect(ColorEffect.class);
            FXAAEffect fxaaEffect = (FXAAEffect) renderer.getEffectComposer().getEffect(FXAAEffect.class);
            CRTEffect crtEffect = (CRTEffect) renderer.getEffectComposer().getEffect(CRTEffect.class);
            ToonEffect toonEffect = (ToonEffect) renderer.getEffectComposer().getEffect(ToonEffect.class);
            NTSCCombinedEffect ntscEffect = (NTSCCombinedEffect) renderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);

            if (colorEffect != null) {
                sbBrightness.setValue(colorEffect.getBrightness() * 100);
                sbContrast.setValue(colorEffect.getContrast() * 100);
                sbGamma.setValue(colorEffect.getGamma());
                sbSaturation.setValue((colorEffect.getSaturation() - 1.0f) * 100);
            } else resetSettings();

            cbEnableFXAA.setChecked(fxaaEffect != null);
            cbEnableCRTShader.setChecked(crtEffect != null);
            cbEnableToonShader.setChecked(toonEffect != null);
            cbEnableNTSCEffect.setChecked(ntscEffect != null);
        }
        else if (hostRenderer instanceof VulkanRenderer) {
            VulkanRenderer vkr = (VulkanRenderer) hostRenderer;
            sbBrightness.setValue(vkr.getColorBrightness() * 100);
            sbContrast.setValue(vkr.getColorContrast() * 100);
            sbGamma.setValue(vkr.getColorGamma());
            sbSaturation.setValue((vkr.getColorSaturation() - 1.0f) * 100);
            cbEnableFXAA.setChecked(vkr.isFxaaEnabled());
            cbEnableCRTShader.setChecked(vkr.isCrtEnabled());
            cbEnableToonShader.setChecked(vkr.isToonEnabled());
            cbEnableNTSCEffect.setChecked(vkr.isNtscEnabled());
        }

        Log.d(TAG, "ScreenEffectDialog initialized");

        loadProfileSpinner(sProfile, activity.getScreenEffectProfile());

        sProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    loadProfile(sProfile.getSelectedItem().toString());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button resetButton = findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener(v -> resetSettings());

        findViewById(R.id.BTConfirm).setOnClickListener(v -> {
            Log.d(TAG, "BTConfirm clicked. Preparing to save profile and apply effects.");
            saveProfile(sProfile);
            Log.d(TAG, "Profile saved.");

            // Directly calling applyEffects to ensure it's triggered
            Log.d(TAG, "Calling applyEffects() directly.");
            applyEffects();

            Log.d(TAG, "Effects applied. Dismissing dialog.");
            dismiss(); // Close the dialog
            Log.d(TAG, "Dialog dismissed.");
        });



        findViewById(R.id.BTAddProfile).setOnClickListener(v -> promptAddProfile());
        findViewById(R.id.BTRemoveProfile).setOnClickListener(v -> promptDeleteProfile());

        setOnConfirmCallback(() -> {
            Log.d(TAG, "OnConfirm callback triggered. Applying effects.");
            applyEffects();
            Log.d(TAG, "Effects applied from callback.");

            // Optionally dismiss after applying effects in callback
            dismiss();
            Log.d(TAG, "Dialog dismissed after callback.");
        });

    }

    private static void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
//        Context context = textView.getContext();

        if (isDarkMode) {
            // Apply dark mode-specific attributes
            textView.setTextColor(Color.parseColor("#cccccc")); // Set text color to #cccccc
            textView.setBackgroundResource(R.color.window_background_color_dark); // Set dark background color
        } else {
            // Apply light mode-specific attributes (original FieldSetLabel)
            textView.setTextColor(Color.parseColor("#bdbdbd")); // Set text color to #bdbdbd
            textView.setBackgroundResource(R.color.window_background_color); // Set light background color
        }
    }

    private void promptAddProfile() {
        ContentDialog.prompt(activity, R.string.do_you_want_to_add_a_new_profile, null, name -> addProfile(name, sProfile));
    }

    private void promptDeleteProfile() {
        if (sProfile.getSelectedItemPosition() > 0) {
            String selectedProfile = sProfile.getSelectedItem().toString();
            ContentDialog.confirm(activity, R.string.do_you_want_to_remove_this_profile, () -> removeProfile(selectedProfile, sProfile));
        } else {
            AppUtils.showToast(activity, R.string.no_profile_selected);
        }
    }

    private void addProfile(String newName, Spinner sProfile) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            if (parts[0].equals(newName)) {
                return;
            }
        }
        profiles.add(newName + ":");
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply();
        loadProfileSpinner(sProfile, newName);
    }

    private void loadProfileSpinner(Spinner sProfile, String selectedName) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        ArrayList<String> items = new ArrayList<>();
        items.add("-- " + activity.getString(R.string.default_profile) + " --");
        int selectedPosition = 0, position = 1;
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            items.add(parts[0]);
            if (parts[0].equals(selectedName)) {
                selectedPosition = position;
            }
            position++;
        }
        sProfile.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, items));
        sProfile.setSelection(selectedPosition);
    }

    private void loadProfile(String name) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            if (parts[0].equals(name) && parts.length > 1 && !parts[1].isEmpty()) {
                KeyValueSet settings = new KeyValueSet(parts[1]);
                sbBrightness.setValue(settings.getFloat("brightness", 0));
                sbContrast.setValue(settings.getFloat("contrast", 0));
                sbGamma.setValue(settings.getFloat("gamma", 1.0f));
                sbSaturation.setValue(settings.getFloat("saturation", 0));
                cbEnableFXAA.setChecked(settings.getBoolean("fxaa", false));
                cbEnableCRTShader.setChecked(settings.getBoolean("crt_shader", false));
                cbEnableToonShader.setChecked(settings.getBoolean("toon_shader", false));
                cbEnableNTSCEffect.setChecked(settings.getBoolean("ntsc_effect", false));
                return;
            }
        }
    }

    private void removeProfile(String targetName, Spinner sProfile) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        profiles.removeIf(profile -> profile.split(":")[0].equals(targetName));
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply();
        loadProfileSpinner(sProfile, null);
        resetSettings();
    }

    private void resetSettings() {
        sbBrightness.setValue(0);
        sbContrast.setValue(0);
        sbGamma.setValue(1.0f);
        sbSaturation.setValue(0);
        cbEnableFXAA.setChecked(false);
        cbEnableCRTShader.setChecked(false);
        cbEnableToonShader.setChecked(false);
        cbEnableNTSCEffect.setChecked(false);
    }

    private void saveProfile(Spinner sProfile) {
        if (sProfile.getSelectedItemPosition() > 0) {
            String selectedProfile = sProfile.getSelectedItem().toString();
            Set<String> oldProfiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
            Set<String> newProfiles = new LinkedHashSet<>();
            KeyValueSet settings = new KeyValueSet();
            settings.put("brightness", sbBrightness.getValue());
            settings.put("contrast", sbContrast.getValue());
            settings.put("gamma", sbGamma.getValue());
            settings.put("saturation", sbSaturation.getValue());
            settings.put("fxaa", cbEnableFXAA.isChecked());
            settings.put("crt_shader", cbEnableCRTShader.isChecked());
            settings.put("toon_shader", cbEnableToonShader.isChecked());
            settings.put("ntsc_effect", cbEnableNTSCEffect.isChecked());

            for (String profile : oldProfiles) {
                String[] parts = profile.split(":");
                if (parts[0].equals(selectedProfile)) {
                    newProfiles.add(selectedProfile + ":" + settings.toString());
                } else {
                    newProfiles.add(profile);
                }
            }
            preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply();
            activity.setScreenEffectProfile(selectedProfile);
        }
    }

    public void applyEffects() {
        Log.d(TAG, "applyEffects() called");

        float brightness = sbBrightness.getValue();
        float contrast = sbContrast.getValue();
        float gamma = sbGamma.getValue();
        float saturation = (sbSaturation.getValue() / 100.0f) + 1.0f;
        boolean fxaa = cbEnableFXAA.isChecked();
        boolean crt = cbEnableCRTShader.isChecked();
        boolean toon = cbEnableToonShader.isChecked();
        boolean ntsc = cbEnableNTSCEffect.isChecked();

        HostRenderer hostRenderer = activity.getXServerView().getRenderer();
        if (hostRenderer instanceof GLRenderer) {
            GLRenderer renderer = (GLRenderer) hostRenderer;
            ColorEffect ce = (ColorEffect) renderer.getEffectComposer().getEffect(ColorEffect.class);
            FXAAEffect  fx = (FXAAEffect)  renderer.getEffectComposer().getEffect(FXAAEffect.class);
            CRTEffect   cr = (CRTEffect)   renderer.getEffectComposer().getEffect(CRTEffect.class);
            ToonEffect  to = (ToonEffect)  renderer.getEffectComposer().getEffect(ToonEffect.class);
            NTSCCombinedEffect nt = (NTSCCombinedEffect) renderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);

            if (ce == null) ce = new ColorEffect();
            if (brightness == 0 && contrast == 0 && gamma == 1.0f && saturation == 1.0f) renderer.getEffectComposer().removeEffect(ce);
            else {
                ce.setBrightness(brightness / 100f);
                ce.setContrast(contrast / 100f);
                ce.setGamma(gamma);
                ce.setSaturation(saturation);
                renderer.getEffectComposer().addEffect(ce);
            }

            if (fxaa) { if (fx == null) renderer.getEffectComposer().addEffect(new FXAAEffect()); }
            else if (fx != null) renderer.getEffectComposer().removeEffect(fx);

            if (crt) { if (cr == null) renderer.getEffectComposer().addEffect(new CRTEffect()); }
            else if (cr != null) renderer.getEffectComposer().removeEffect(cr);

            if (toon) { if (to == null) renderer.getEffectComposer().addEffect(new ToonEffect()); }
            else if (to != null) renderer.getEffectComposer().removeEffect(to);

            if (ntsc) { if (nt == null) renderer.getEffectComposer().addEffect(new NTSCCombinedEffect()); }
            else if (nt != null) renderer.getEffectComposer().removeEffect(nt);
        }
        else if (hostRenderer instanceof VulkanRenderer) {
            ((VulkanRenderer)hostRenderer).setScreenEffects(brightness, contrast, gamma, saturation, fxaa, toon, crt, ntsc);
        }

        saveProfile(sProfile);
    }

    public void setOnConfirmCallback(Runnable confirmCallback) {
        Log.d(TAG, "Setting OnConfirm callback.");
        this.onConfirmCallback = confirmCallback;
    }

    public static void applyProfileToRenderer(HostRenderer hostRenderer, SharedPreferences preferences, String profileName) {
        if (hostRenderer == null || profileName == null || profileName.isEmpty()) return;
        Set<String> profiles = preferences.getStringSet("screen_effect_profiles", null);
        if (profiles == null) return;

        for (String profile : profiles) {
            String[] parts = profile.split(":");
            if (parts[0].equals(profileName) && parts.length > 1 && !parts[1].isEmpty()) {
                KeyValueSet settings = new KeyValueSet(parts[1]);
                float b = settings.getFloat("brightness", 0);
                float c = settings.getFloat("contrast", 0);
                float g = settings.getFloat("gamma", 1.0f);
                float s = settings.getFloat("saturation", 0);
                boolean fxaa = settings.getBoolean("fxaa", false);
                boolean crt  = settings.getBoolean("crt_shader", false);
                boolean toon = settings.getBoolean("toon_shader", false);
                boolean ntsc = settings.getBoolean("ntsc_effect", false);

                if (hostRenderer instanceof VulkanRenderer) {
                    ((VulkanRenderer) hostRenderer).setScreenEffects(b, c, g, s + 1.0f, fxaa, toon, crt, ntsc);
                }
                break;
            }
        }
    }
}
