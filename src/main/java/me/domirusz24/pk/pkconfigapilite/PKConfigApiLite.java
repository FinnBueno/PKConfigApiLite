package me.domirusz24.pk.pkconfigapilite;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import me.domirusz24.pk.pkconfigapilite.annotations.ConfigAbility;
import me.domirusz24.pk.pkconfigapilite.annotations.ConfigValue;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class PKConfigApiLite {

    private static class Pair<T, R> {
        private T first;
        private R second;
        Pair(T first, R second) { this.first = first; this.second = second; }
    }

    private static final Map<String, Function<AddonAbility, String>> PLACEHOLDERS = new HashMap<>();
    static {
        PLACEHOLDERS.put("version", AddonAbility::getVersion);
        PLACEHOLDERS.put("author", AddonAbility::getAuthor);
    }

    public static void registerConfigValues(CoreAbility ability) {
        // get the class of the ability that was passed
        Class<? extends CoreAbility> clazz = ability.getClass();

        // find the ConfigAbility annotation that indicates the ability has configurable constants
        ConfigAbility abilityAnnotation = clazz.getDeclaredAnnotation(ConfigAbility.class);
        // in case this class is not annotated with ConfigAbility, don't check for config values
        if (abilityAnnotation == null) return;
        // find the element of the ability, also check for sub elements!
        String element = ability.getElement() instanceof Element.SubElement ?
            ((Element.SubElement) ability.getElement()).getParentElement().getName() :
            ability.getElement().getName();

        // load all the config files defined by the ConfigAbility annotation
        Map<String, Pair<FileConfiguration, File>> configTypeToFile = new HashMap<>();
        for (String configName : abilityAnnotation.configs()) {
            String configFileName = configName + "." + abilityAnnotation.extension();
            File file = getFile(configFileName, abilityAnnotation.plugin());
            FileConfiguration fileConfig = getConfig(file);
            if (fileConfig == null) {
                System.err.println("Config for " + ability.getName() + " couldn't be loaded! Path " + configFileName + " - Plugin: " + abilityAnnotation.plugin());
                continue;
            }
            fileConfig.options().copyDefaults(true);
            // store them in our map so we can reference them by name later
            configTypeToFile.put(configName, new Pair<>(fileConfig, file));
        }

        // find the config path for this ability. This will usually be something like "ExtraAbilities.<Author>.<Ability>."
        String path = ability instanceof AddonAbility ?
                abilityAnnotation.value() + "." + ((AddonAbility) ability).getAuthor() + "." + element + "." + ability.getName() + "." :
                abilityAnnotation.value() + "." + element + "." + ability.getName() + ".";

        // here the magic starts, loop through all fields in the ability class
        Object value;
        Object configValue;
        String name;
        ConfigValue configValueAnnotation;
        int modifiers;
        for (Field field : clazz.getDeclaredFields()) {
            modifiers = field.getModifiers();

            // if this field isn't static, it can't be configurable and thus we skip it
            if (!Modifier.isStatic(modifiers)) {
                return;
            }

            // find if there's a ConfigValue annotation above the field. If not, we skip it
            configValueAnnotation = field.getAnnotation(ConfigValue.class);
            if (configValueAnnotation == null) continue;

            // remove the private modifier
            field.setAccessible(true);
            // remove the final modifier
            if (Modifier.isFinal(field.getModifiers())) {
                try {
                    field.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                } catch (IllegalAccessException e) {
                    System.err.println("Field " + field.getName() + " in " + ability.getName() + " could not have their final modifier removed! (Try making the field non-final)");
                    e.printStackTrace();
                }
            }

            // get the default value from the field itself
            try {
                value = field.get(ability);
            } catch (IllegalAccessException e) {
                System.err.println("Field " + field.getName() + " in " + ability.getName() + " doesn't want to be accessed, skipping! (Try making the field not private)");
                continue;
            }
            // default values are required. If none were supplied, skip
            if (value == null) {
                System.err.println("Field " + field.getName() + " in " + ability.getName() + " doesn't have a default value set in code!");
                continue;
            }

            // get the config file from the name specified by the ConfigValue annotation
            Pair<FileConfiguration, File> configAndFile = configTypeToFile.get(configValueAnnotation.type());
            FileConfiguration fileConfig = configAndFile.first;

            // get the name of the config value
            name = configValueAnnotation.value();
            // if no name was supplied, we generate one from the field name
            if (name.length() == 0) {
                // generate name instead
                name = field.getName().charAt(0) + field.getName().toLowerCase().substring(1);
            }
            // set the default value in the config
            // this method only sets the value in the config if it doesn't exist yet
            fileConfig.addDefault(path + name, value);
            // then get the value from the config
            configValue = fileConfig.get(path + name);
            // if the value from the config is a string and we're dealing with an addon, apply some placeholders
            if (configValue instanceof String && ability instanceof AddonAbility) {
                configValue = populatePlaceholders((String) configValue, (AddonAbility) ability);
            }

            try {
                // assign the value from the  config to the field
                field.set(null, configValue);
            } catch (IllegalAccessException e) {
                System.out.println("Field " + field.getName() + " in " + ability.getName() + " doesn't want to be accessed, skipping! (Try making the field not private)");
            }
        }

        try {
            // finally, we make sure we save all of the used config files when we're done!
            for (Map.Entry<String, Pair<FileConfiguration, File>> configType : configTypeToFile.entrySet()) {
                Pair<FileConfiguration, File> configAndFile = configType.getValue();
                FileConfiguration fileConfig = configAndFile.first;
                File file = configAndFile.second;
                fileConfig.save(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Config for " + ability.getName() + " couldn't be saved! Plugin: " + abilityAnnotation.plugin());
        }

    }

    /**
     * Replaces all placeholders in the given string with information from the given addon.
     * @param value The string to replace placeholders in
     * @param ability The addon to pull information from
     * @return A populated string
     */
    private static String populatePlaceholders(String value, AddonAbility ability) {
        for (Map.Entry<String, Function<AddonAbility, String>> placeholder : PLACEHOLDERS.entrySet()) {
            value = value.replace("%" + placeholder.getKey() + "%", placeholder.getValue().apply(ability));
        }
        return value;
    }

    /**
     * Gets a {@link FileConfiguration} from a {@link File}. Throws an error if the file could not be found and a new one could not be created.
     * @param file The {@link File} to get the {@link FileConfiguration} from
     * @return A {@link FileConfiguration} instance
     */
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

    /**
     * Gets a config file as a {@link File} instance from a given config name and plugin name.
     * Throws an Exception if the plugin's data folder or config could not be created.
     * @param path The name of the config file
     * @param plugin The name of the plugin
     * @return A {@link File} instance representing the given plugin's config file.
     */
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
