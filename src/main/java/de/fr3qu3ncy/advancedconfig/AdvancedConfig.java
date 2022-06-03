package de.fr3qu3ncy.advancedconfig;

import de.fr3qu3ncy.advancedconfig.annotation.Comment;
import de.fr3qu3ncy.advancedconfig.annotation.Config;
import de.fr3qu3ncy.advancedconfig.serialization.ConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AdvancedConfig {

    private final Map<Class<?>, ConfigParser<?>> configParsers = new HashMap<>();
    private final List<Class<?>> configClasses = new ArrayList<>();

    private static final String COMMENT_IDENTIFIER = "_COMMENT_";

    private final Plugin plugin;
    private final String filePath;
    private final String fileName;

    private File configFile;
    private YamlConfiguration config;

    /**
     * Register a class containing configuration values
     *
     * @param clazz The class to register
     */
    public void registerConfiguration(Class<?> clazz) {
        if (!configClasses.contains(clazz)) {
            configClasses.add(clazz);
        }
    }

    /**
     * Register a ConfigParser
     *
     * @param clazz  The class to parse
     * @param parser The parser
     */
    public <T> void registerConfigParser(Class<T> clazz, ConfigParser<T> parser) {
        configParsers.put(clazz, parser);
    }

    private File getFileDirectory() {
        return new File(plugin.getDataFolder().getAbsolutePath(), filePath);
    }

    /**
     * Loads (and creates) the configuration of this module
     * The config file is not created until
     * this method is called at least once
     *
     * @return The {@link YamlConfiguration} of this module
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored", "java:S899"})
    @SneakyThrows
    public YamlConfiguration getConfig() {
        //Check if the config already exists
        if (configFile == null || config == null) {
            //Create the needed directory
            File configDir = getFileDirectory();
            configDir.mkdirs();

            //Create the config file
            configFile = new File(configDir, fileName + ".yml");
            if (!configFile.exists()) {
                configFile.createNewFile();
            }

            //Load the configuration from file
            this.config = YamlConfiguration.loadConfiguration(configFile);

            //Set copyDefaults true for automatic value adding
            config.options().copyDefaults(true);
        }
        return config;
    }

    @SneakyThrows
    public void reloadConfig() {
        configFile = null;
        config = null;
        loadConfiguration();
    }

    /**
     * Loads all registered Config classes
     */
    public void loadConfiguration() throws IllegalAccessException, IOException {
        //Loop all config
        for (Class<?> clazz : configClasses) {
            for (Field field : clazz.getFields()) {

                if (!field.isAnnotationPresent(Config.class) || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                //Call getConfig here to create all needed files
                getConfig();

                Config data = field.getAnnotation(Config.class);
                String path = data.value();
                ConfigurationSection section = config.getConfigurationSection(path);

                String comment = null;
                if (field.isAnnotationPresent(Comment.class)) {
                    comment = field.getAnnotation(Comment.class).value();
                }

                //Get the specified default value
                Object defaultValue = field.get(null);

                //Check if there is a parser for specified type
                Class<?> type = field.getType();
                ConfigParser<?> parser = configParsers.get(type);

                if (!config.contains(path) && defaultValue != null) {
                    applyDefaultValue(parser, comment, path, defaultValue);
                } else {
                    writeToField(section, field, parser, path, defaultValue);
                }
            }
        }
        replaceComments();
    }

    @SuppressWarnings("java:S3011")
    @SneakyThrows
    private void writeToField(ConfigurationSection section, Field field, ConfigParser<?> parser,
                              String path, Object defaultValue) {
        Object value;
        if (parser != null) {
            value = parser.deserialize(section);
        } else {
            value = getConfig().get(path, defaultValue);
        }
        field.set(null, value);
    }

    private void replaceComments() throws IOException {
        File oldConfig = new File(getFileDirectory(), fileName + ".old.yml");
        if (!oldConfig.exists() && !oldConfig.createNewFile()) return;
        FileUtils.copyFile(configFile, oldConfig);

        try (BufferedReader reader = new BufferedReader(new FileReader(oldConfig)); FileWriter writer = new FileWriter(configFile)) {

            String line;
            while ((line = reader.readLine()) != null) {
                writeLine(writer, line);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Files.delete(oldConfig.toPath());
    }

    private void writeLine(FileWriter writer, String line) throws IOException {
        String path = line.split(":")[0].replace(" ", "");
        if (path.contains(COMMENT_IDENTIFIER)) {
            int startIndex = -1;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != ' ') {
                    startIndex = i;
                    break;
                }
            }
            if (startIndex != -1) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0 ; i < startIndex ; i++) {
                    sb.append(" ");
                }
                sb.append("#").append(line.substring(line.indexOf(":") + 2));
                line = sb.toString();
            }
        }
        writer.write(line + "\n");
    }

    private void applyDefaultValue(ConfigParser<?> parser, String comment, String path, Object defaultValue) {
        if (parser != null) {
            ConfigurationSection section = config.createSection(path);
            if (comment != null) {
                config.set(section.getCurrentPath() + COMMENT_IDENTIFIER, comment);
            }
            applyDefault(section, parser, defaultValue);
        } else {
            if (comment != null) {
                config.set(path + COMMENT_IDENTIFIER, comment);
            }
            config.set(path, defaultValue);
        }
        saveConfig();
    }

    @SuppressWarnings("unchecked")
    private <T> void applyDefault(ConfigurationSection section, ConfigParser<T> parser, Object object) {
        parser.serialize(section, (T) object);
    }

    @SneakyThrows
    public void saveConfig() {
        config.save(configFile);
    }
}
