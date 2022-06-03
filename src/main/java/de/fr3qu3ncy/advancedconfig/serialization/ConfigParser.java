package de.fr3qu3ncy.advancedconfig.serialization;

import org.bukkit.configuration.ConfigurationSection;

public interface ConfigParser<T> {

    T deserialize(ConfigurationSection section);

    void serialize(ConfigurationSection section, T object);

}
