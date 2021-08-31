package daomephsta.fabriclipse.metadata;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.eclipse.core.runtime.IPath;

public class JarMod extends Mod
{
    private final IPath jarPath;

    public JarMod(ModMetadata metadata, IPath jarPath)
    {
        super(metadata);
        this.jarPath = jarPath;
    }

    @Override
    public InputStream openResource(String path) throws IOException
    {
        JarInputStream jarStream = new JarInputStream(new FileInputStream(jarPath.toFile()));
        JarEntry entry;
        while ((entry = jarStream.getNextJarEntry()) != null)
        {
            if (entry.getName().equals(path))
                return jarStream;
        }
        throw new FileNotFoundException(path + " not found in " + jarPath.toOSString());
    }
}
