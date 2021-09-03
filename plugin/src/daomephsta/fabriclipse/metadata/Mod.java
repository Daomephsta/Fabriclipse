package daomephsta.fabriclipse.metadata;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.core.runtime.CoreException;

public abstract class Mod
{
    private ModMetadata metadata;

    public Mod(ModMetadata metadata)
    {
        this.metadata = metadata;
    }

    public abstract InputStream openResource(String path) throws CoreException, IOException;

    public ModMetadata getMetadata()
    {
        return metadata;
    }

    public void setMetadata(ModMetadata newMetadata)
    {
        this.metadata = newMetadata;
    }
}
