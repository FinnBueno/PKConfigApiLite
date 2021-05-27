package me.domirusz24.pk.pkconfigapilite;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import me.domirusz24.pk.pkconfigapilite.annotations.ConfigAbility;
import me.domirusz24.pk.pkconfigapilite.annotations.ConfigValue;
import me.domirusz24.pk.pkconfigapilite.annotations.LanguageValue;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class PKConfigApiLite {

    private static void registerConfigValues(CoreAbility ability) {
        int modifiers;

        ConfigValue configAnnotation;
        LanguageValue languageAnnotation;

        ConfigAbility abilityAnnotation;

        FileConfiguration abilityConfig;
        FileConfiguration languageConfig;

        File abilityConfigFile;
        File languageConfigFile;

        Object value;
        Object configValue;
        String name;
        String element;

        Class<? extends CoreAbility> clazz = ability.getClass();

        abilityAnnotation = clazz.getDeclaredAnnotation(ConfigAbility.class);
        if (abilityAnnotation == null) return;
        element = ability.getElement() instanceof Element.SubElement ? ((Element.SubElement) ability.getElement()).getParentElement().getName() : ability.getElement().getName();

        // Get ability config
        abilityConfigFile = getFile(abilityAnnotation.configPath(), abilityAnnotation.plugin());
        abilityConfig = getConfig(abilityConfigFile);
        if (abilityConfig == null) {
            System.out.println("Config for " + ability.getName() + " couldn't be loaded! Path: " + abilityAnnotation.configPath() + " Plugin: " + abilityAnnotation.plugin());
            return;
        }
        abilityConfig.options().copyDefaults(true);

        // Get language config
        languageConfigFile = getFile(abilityAnnotation.languagePath(), abilityAnnotation.plugin());
        languageConfig = getConfig(languageConfigFile);
        if (languageConfig == null) {
            System.out.println("Config for " + ability.getName() + " couldn't be loaded! Path: " + abilityAnnotation.configPath() + " Plugin: " + abilityAnnotation.plugin());
            return;
        }
        languageConfig.options().copyDefaults(true);

        String path = !(ability instanceof AddonAbility) ?
                abilityAnnotation.value() + "." + element + "." + ability.getName() + "." :
                abilityAnnotation.value() + "." + ((AddonAbility) ability).getAuthor() + "." + element + "." + ability.getName() + ".";

        for (Field field : clazz.getDeclaredFields()) {
            modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {

                configAnnotation = field.getAnnotation(ConfigValue.class);
                languageAnnotation = field.getAnnotation(LanguageValue.class);

                if (configAnnotation != null || languageAnnotation != null) {

                    field.setAccessible(true);
                    try {
                        value = field.get(ability);
                    } catch (IllegalAccessException e) {
                        System.out.println("Field " + field.getName() + " in " + ability.getName() + " doesn't want to be accessed, skipping! (Try making the field not private)");
                        continue;
                    }
                    if (value == null) {
                        System.out.println("Field " + field.getName() + " in " + ability.getName() + " doesn't have a default value set in code!");
                        continue;
                    }

                    if (configAnnotation != null) {
                        name = configAnnotation.value();
                        abilityConfig.addDefault(path + name, value);
                        configValue = abilityConfig.get(path + name);
                    } else {
                        name = languageAnnotation.value();
                        languageConfig.addDefault(path + name, value);
                        configValue = languageConfig.get(path + name);
                        if (configValue instanceof String && ability instanceof AddonAbility) {
                            configValue = ((String) configValue).replace("%version%", ((AddonAbility) ability).getVersion());
                        }
                    }

                    try {
                        field.set(null, configValue);
                    } catch (IllegalAccessException e) {
                        System.out.println("Field " + field.getName() + " in " + ability.getName() + " doesn't want to be accessed, skipping! (Try making the field not private)");
                    }
                }
            }
        }

        try {
            if (abilityAnnotation.plugin().equals("ProjectKorra")) {
                if (!abilityAnnotation.configPath().equals("config.yml")) {
                    abilityConfig.save(abilityConfigFile);
                }
                if (!abilityAnnotation.languagePath().equals("language.yml")) {
                    languageConfig.save(languageConfigFile);
                }
            } else {
                abilityConfig.save(abilityConfigFile);
                languageConfig.save(languageConfigFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Config for " + ability.getName() + " couldn't be saved! Plugin: " + abilityAnnotation.plugin());
        }

    }

    private static FileConfiguration getConfig(File file) {
        if (file == null) return null;
        if (!file.exists()) {
            try {
                file.getParentFile().mkdir();
                file.createNewFile();
                System.out.println("Generating new " + file.getName() + "!");
            } catch (Exception var2) {
                System.out.println("Failed to generate " + file.getName() + "!");
                var2.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static FileConfiguration getConfig(String path, String plugin) {
        if (plugin.equals("ProjectKorra")) {
            if (path.equals("config.yml")) {
                return ConfigManager.defaultConfig.get();
            } else if (path.equals("language.yml")) {
                return ConfigManager.languageConfig.get();
            }
        }
        File file = getFile(path, plugin);
        if (file == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static File getFile(String path, String plugin) {
        Plugin plg = Bukkit.getPluginManager().getPlugin(plugin);
        if (plg == null) {
            return null;
        }
        if (!plg.getDataFolder().exists()) {
            plg.getDataFolder().mkdir();
        }
        File file = new File(plg.getDataFolder(), path);

        if (!file.exists()) {
            try {
                file.getParentFile().mkdir();
                file.createNewFile();
                System.out.println("Generating new " + file.getName() + "!");
            } catch (Exception var2) {
                System.out.println("Failed to generate " + file.getName() + "!");
                var2.printStackTrace();
                return null;
            }
        }
        return file;
    }

}
