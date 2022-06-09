package de.fr3qu3ncy.advancedconfig;

import de.fr3qu3ncy.advancedconfig.annotation.Config;
import de.fr3qu3ncy.advancedconfig.serialization.ConfigParser;
import lombok.AccessLevel;
import lombok.Getter;
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
import java.util.function.Supplier;

public class AdvancedConfig {

    private static final String COMMENT_IDENTIFIER = "_COMMENT_";

    @Getter(AccessLevel.PACKAGE)
    private static final Map<Class<?>, ConfigParser<?>> configParsers = new HashMap<>();
    private final List<Class<?>> configClasses = new ArrayList<>();

    private final Plugin plugin;
    private final String filePath;
    private final String fileName;

    private File configFile;
    private YamlConfiguration config;

    public AdvancedConfig(Plugin plugin, String filePath, String fileName) {
        this.plugin = plugin;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public AdvancedConfig(Plugin plugin, String fileName) {
        this(plugin, null, fileName);
    }

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
    public static <T> void registerConfigParser(Class<T> clazz, ConfigParser<T> parser) {
        configParsers.put(clazz, parser);
    }

    public static <T> void registerConfigParser(Class<T> clazz, Supplier<ConfigParser<T>> parser) {
        configParsers.put(clazz, parser.get());
    }

    private File getFileDirectory() {
        return filePath != null
            ? new File(plugin.getDataFolder().getAbsolutePath(), filePath)
            : new File(plugin.getDataFolder().getAbsolutePath());
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
    @SneakyThrows
    public void loadConfiguration() {
        //Loop all config
        if (!configClasses.isEmpty()) {
            //Call getConfig here to create all needed files
            getConfig();
        }
        for (Class<?> clazz : configClasses) {
            for (Field field : clazz.getFields()) {

                //Field needs to be public static and be annotated with Config
                if (!field.isAnnotationPresent(Config.class) || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                new ConfigField(this, field).load();
            }
        }
        replaceComments();
    }

    public static <T> T deserialize(Class<T> clazz, ConfigurationSection section) {
        ConfigParser<?> parser = configParsers.get(clazz);
        if (parser == null) return null;
        return (T) parser.deserialize(section);
    }

    public static <T> void serialize(ConfigurationSection section, Class<T> clazz, T object) {
        ConfigParser<T> parser = configParsers.containsKey(clazz) ? (ConfigParser<T>) configParsers.get(clazz) : null;
        if (parser == null) return;
        parser.serialize(section, object);
    }

    protected void applyDefaultValue(ConfigField field) {
        ConfigParser<?> parser = field.getParser();
        String path = field.getPath();
        String comment = field.getComment();
        Object defaultValue = field.getDefaultValue();

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
    protected  <T> void applyDefault(ConfigurationSection section, ConfigParser<T> parser, Object object) {
        parser.serialize(section, (T) object);
    }

    @SuppressWarnings("java:S3011")
    @SneakyThrows
    protected void writeToField(ConfigurationSection section, ConfigField field) {
        Object value;
        if (field.getParser() != null) {
            value = field.getParser().deserialize(section);
        } else {
            value = getConfig().get(field.getPath(), field.getDefaultValue());
        }
        field.getField().set(null, value);
    }

    private void replaceComments() throws IOException {
        if (configFile == null) return;
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

    @SneakyThrows
    public void saveConfig() {
        config.save(configFile);
    }
}
