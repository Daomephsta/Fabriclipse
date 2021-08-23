package daomephsta.fabriclipse.metadata;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

public class ModMetadataStore
{
    public static final ModMetadataStore INSTANCE = new ModMetadataStore();
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ModMetadata.class, (JsonDeserializer<ModMetadata>) ModMetadata::deserialize)
        .create();
    private final Map<IProject, ProjectMetadataStore> metadata = new ConcurrentHashMap<>();

    public ModMetadata getProjectMetadata(IProject key)
    {
        return getProjectStore(key).projectMetadata;
    }

    private ProjectMetadataStore getProjectStore(IProject project)
    {
        return metadata.computeIfAbsent(project, key ->
        {
            ProjectMetadataStore store = new ProjectMetadataStore();
            IFile fabricModJson = project.getFile("src/main/resources/fabric.mod.json");
            if (fabricModJson.exists())
                store.projectMetadata = readModMetadata(fabricModJson);
            return store;
        });
    }

    private ModMetadata readModMetadata(IFile file)
    {
        try (Reader reader = new InputStreamReader(file.getContents()))
        {
            return GSON.fromJson(reader, ModMetadata.class);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read mod metadata", e);
        }
        catch (CoreException e)
        {
            throw new RuntimeException("Failed to read mod metadata", e);
        }
    }

    private class ProjectMetadataStore
    {
        ModMetadata projectMetadata;
    }
}
