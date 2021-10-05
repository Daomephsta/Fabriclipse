package daomephsta.fabriclipse.metadata;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import daomephsta.fabriclipse.Fabriclipse;

public class ModMetadata
{
    private final String modId;
    private final Set<String> mixinConfigs;

    private ModMetadata(String modId, Set<String> mixinConfigs)
    {
        this.modId = modId;
        this.mixinConfigs = mixinConfigs;
    }

    public String getId()
    {
        return modId;
    }

    public Set<String> getMixinConfigs()
    {
        return mixinConfigs;
    }

    public static ModMetadata deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException
    {
        if (json instanceof JsonObject root)
        {
            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            String modId = root.get("id").getAsString();
            if (schemaVersion != 1)
            {
                Fabriclipse.LOGGER.warn("Unknown schemaVersion " + schemaVersion);
                return new ModMetadata(modId, Collections.emptySet());
            }
            Set<String> mixinConfigs = readMixinConfigs(root);
            return new ModMetadata(modId, mixinConfigs);
        }
        throw new JsonParseException("Root element of fabric.mod.json must be an object");
    }

    private static Set<String> readMixinConfigs(JsonObject root)
    {
        Set<String> mixinConfigs = new HashSet<>();
        if (root.has("mixins"))
        {
            var mixins = root.get("mixins").getAsJsonArray();
            for (JsonElement mixinConfig : mixins)
            {
                if (mixinConfig.isJsonObject()) // Side can be safely ignored as a dev env is merged
                    mixinConfigs.add(mixinConfig.getAsJsonObject().get("config").getAsString());
                else if (mixinConfig.isJsonPrimitive() && mixinConfig.getAsJsonPrimitive().isString())
                    mixinConfigs.add(mixinConfig.getAsString());
                else
                {
                    throw new JsonParseException("Elements in mixins array"
                        + " of fabric.mod.json must be strings or JSON objects");
                }
            }
        }
        return mixinConfigs;
    }
}
