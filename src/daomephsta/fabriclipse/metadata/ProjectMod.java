package daomephsta.fabriclipse.metadata;

import java.io.InputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

public class ProjectMod extends Mod
{
    private final IProject project;

    public ProjectMod(ModMetadata metadata, IProject project)
    {
        super(metadata);
        this.project = project;
    }

    @Override
    public InputStream openResource(String path) throws CoreException
    {
        return project.getFile("src/main/resources/" + path).getContents();
    }
}
