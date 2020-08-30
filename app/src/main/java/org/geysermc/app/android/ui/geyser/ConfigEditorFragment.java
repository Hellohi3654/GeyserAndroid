/*
 * Copyright (c) 2020-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserAndroid
 */

package org.geysermc.app.android.ui.geyser;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import org.geysermc.app.android.geyser.GeyserAndroidConfiguration;
import org.geysermc.app.android.utils.AndroidUtils;
import org.geysermc.connector.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigEditorFragment extends PreferenceFragmentCompat {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private File configFile;

    @SuppressLint("NewApi")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(preferenceScreen);

        configFile = AndroidUtils.getStoragePath(getContext()).resolve("config.yml").toFile();
        if (configFile.exists()) {
            try {
                parseConfig(preferenceScreen, configFile);
                AndroidUtils.HideLoader();
            } catch (IOException e) {
                AndroidUtils.HideLoader();
                new AlertDialog.Builder(getContext())
                        .setTitle("Config failed to load")
                        .setMessage("The config failed to load, please reset it manually!")
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            this.getActivity().finish();
                        })
                        .show();
            }
        } else {
            AndroidUtils.HideLoader();

            new AlertDialog.Builder(getContext())
                    .setTitle("Config missing")
                    .setMessage("No config has been created, please start the server first!")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        this.getActivity().finish();
                    })
                    .show();
        }
    }

    public void parseConfig(PreferenceScreen preferenceScreen, File configFile) throws IOException {
        // parse the configuration
        GeyserAndroidConfiguration configuration = FileUtils.loadConfig(configFile, GeyserAndroidConfiguration.class);

        // Get the available properties from the class
        List<BeanPropertyDefinition> availableProperties = getPOJOForClass(GeyserAndroidConfiguration.class);

        // Make the bedrock category
        PreferenceCategory bedrockCategory = new PreferenceCategory(preferenceScreen.getContext());
        bedrockCategory.setTitle("Bedrock");
        preferenceScreen.addPreference(bedrockCategory);

        // Make the bedrock category
        PreferenceCategory remoteCategory = new PreferenceCategory(preferenceScreen.getContext());
        remoteCategory.setTitle("Remote");
        preferenceScreen.addPreference(remoteCategory);

        // Make the advanced category
        PreferenceCategory advancedCategory = new PreferenceCategory(preferenceScreen.getContext());
        advancedCategory.setTitle("Advanced");
        preferenceScreen.addPreference(advancedCategory);

        // Make the metrics category
        PreferenceCategory metricsCategory = new PreferenceCategory(preferenceScreen.getContext());
        metricsCategory.setTitle("Metrics");
        preferenceScreen.addPreference(metricsCategory);

        for (BeanPropertyDefinition property : availableProperties) {
            // Ignore the autoconfiguredRemote property
            if ("autoconfiguredRemote".equals(property.getName())) {
                continue;
            }

            if ("bedrock".equals(property.getName()) || "remote".equals(property.getName())
                    || "metrics".equals(property.getName())) {

                // Get the category for the property
                PreferenceCategory currentCategory = advancedCategory;
                switch (property.getName()) {
                    case "bedrock":
                        currentCategory = bedrockCategory;
                        break;
                    case "remote":
                        currentCategory = remoteCategory;
                        break;
                    case "metrics":
                        currentCategory = metricsCategory;
                        break;
                }

                // Loop the sub class properties
                for (BeanPropertyDefinition subProperty : getPOJOForClass(property.getRawPrimaryType())) {
                    try {
                        createPreference(currentCategory, subProperty, subProperty.getGetter().callOn(property.getGetter().callOn(configuration)));
                    } catch (Exception e) { }
                }

                continue;
            }

            try {
                createPreference(advancedCategory, property, property.getGetter().callOn(configuration));
            } catch (Exception e) { }
        }
    }

    private List<BeanPropertyDefinition> getPOJOForClass(Class<?> clazz) {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(clazz);

        // Introspect the given type
        BeanDescription beanDescription = OBJECT_MAPPER.getSerializationConfig().introspect(javaType);

        // Find properties
        List<BeanPropertyDefinition> properties = beanDescription.findProperties();

        // Get the ignored properties
        Set<String> ignoredProperties = OBJECT_MAPPER.getSerializationConfig().getAnnotationIntrospector()
                .findPropertyIgnorals(beanDescription.getClassInfo()).getIgnored();

        // Filter properties removing the ignored ones
        return properties.stream()
                .filter(property -> !ignoredProperties.contains(property.getName()))
                .collect(Collectors.toList());
    }

    private void createPreference(PreferenceCategory category, BeanPropertyDefinition property, Object value) {
        Preference newPreference;

        // Do any specific initialisation for the property
        if ("config-version".equals(property.getName())) {
            newPreference = new Preference(category.getParent().getContext());
            newPreference.setEnabled(false);
        } else if ("uuid".equals(property.getName())) {
            newPreference = new Preference(category.getParent().getContext());
            newPreference.setEnabled(false);
        } else if ("userAuths".equals(property.getName())) {
            newPreference = new Preference(category.getParent().getContext());
            newPreference.setSummary("Click to edit user auths");
            newPreference.setOnPreferenceClickListener((preference) -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("TODO")
                        .setMessage("Add a user auth edit screen")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            });
        } else if (boolean.class.equals(property.getRawPrimaryType())) {
            newPreference = new SwitchPreference(category.getParent().getContext());
            try {
                ((SwitchPreference) newPreference).setChecked((boolean) value);
            } catch (Exception ignored) {
                ((SwitchPreference) newPreference).setChecked(true);
            }
        } else if (int.class.equals(property.getRawPrimaryType())) {
            newPreference = new EditTextPreference(category.getParent().getContext());
            ((EditTextPreference) newPreference).setOnBindEditTextListener((editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(value.toString());
                editText.setSelection(editText.getText().length());
            });
        } else {
            newPreference = new EditTextPreference(category.getParent().getContext());
            ((EditTextPreference) newPreference).setOnBindEditTextListener((editText) -> {
                editText.setText(value.toString());
                editText.setSelection(editText.getText().length());
            });
        }

        // Make sure we set the preference
        if (newPreference != null) {
            // Only set the summary if its not already been set
            if (newPreference.getSummary() == null) {
                newPreference.setSummary(value.toString());
            }

            newPreference.setTitle(property.getName());
            newPreference.setKey(property.getName());
            newPreference.setPersistent(false);

            // Add the preference to the category
            category.addPreference(newPreference);
        }
    }
}
