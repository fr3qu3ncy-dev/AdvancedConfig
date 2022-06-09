package de.fr3qu3ncy.advancedconfig;

import de.fr3qu3ncy.advancedconfig.annotation.Comment;
import de.fr3qu3ncy.advancedconfig.annotation.Config;
import de.fr3qu3ncy.advancedconfig.annotation.ReplaceColors;
import de.fr3qu3ncy.advancedconfig.serialization.ConfigParser;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;

@Getter
public class ConfigField {

    private final Field field;
    private final AdvancedConfig config;

    private final String path;
    private Object defaultValue;
    private final ConfigParser<?> parser;
    private String comment;

    @SneakyThrows
    public ConfigField(AdvancedConfig config, Field field) {
        Config data = field.getAnnotation(Config.class);

        this.field = field;
        this.config = config;
        this.path = data.value();

        //Get the specified default value
        this.defaultValue = field.get(null);

        //Check if there is a parser for specified type
        Class<?> type = field.getType();
        this.parser = AdvancedConfig.getConfigParsers().get(type);

        if (field.isAnnotationPresent(Comment.class)) {
            this.comment = field.getAnnotation(Comment.class).value();
        }

        if (field.isAnnotationPresent(ReplaceColors.class) && defaultValue instanceof String) {
            this.defaultValue = ChatColor.translateAlternateColorCodes('&', (String) defaultValue);
        }
    }

    @SneakyThrows
    protected void load() {
        ConfigurationSection section = config.getConfig().getConfigurationSection(path);

        if (!config.getConfig().contains(path) && defaultValue != null) {
            config.applyDefaultValue(this);
        } else {
            config.writeToField(section, this);
        }
    }
}
