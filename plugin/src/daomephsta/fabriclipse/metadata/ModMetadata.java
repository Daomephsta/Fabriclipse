package daomephsta.fabriclipse.metadata;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

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
            String modId = root.get("id").getAsString();
            Set<String> mixinConfigs;
            if (root.has("mixins"))
            {
                JsonElement mixinsElement = root.get("mixins");
                if (mixinsElement.isJsonArray())
                    mixinConfigs = context.deserialize(mixinsElement, new TypeToken<Set<String>>() {}.getType());
                else if (mixinsElement instanceof JsonObject mixinsObject)
                    mixinConfigs = new HashSet<>(mixinsObject.keySet());
                else
                    throw new JsonParseException("mixins element of fabric.mod.json must be an array or object");
            }
            else
                mixinConfigs = Collections.emptySet();
            return new ModMetadata(modId, mixinConfigs);
        }
        throw new JsonParseException("Root element of fabric.mod.json must be an object");
    }
}
