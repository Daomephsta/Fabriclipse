package daomephsta.fabriclipse.metadata;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import daomephsta.fabriclipse.mixin.MixinStore;

public class ModMetadataStore implements IResourceChangeListener
{
    public static final ModMetadataStore INSTANCE = new ModMetadataStore();
    private static final IPath FABRIC_MOD_JSON = Path.fromOSString("src/main/resources/fabric.mod.json");
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
            ProjectMetadataStore store = new ProjectMetadataStore(key);
            IFile fabricModJson = key.getFile(FABRIC_MOD_JSON);
            if (fabricModJson.exists())
                store.setProjectMetadata(readModMetadata(fabricModJson));
            return store;
        });
    }

    private ModMetadata readModMetadata(IFile file)
    {
        // Check for working copy first
        ITextFileBuffer workingCopy = FileBuffers.getTextFileBufferManager()
            .getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
        if (workingCopy != null)
            return GSON.fromJson(workingCopy.getDocument().get(), ModMetadata.class);
        // Fallback to direct file reading
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

    @Override
    public void resourceChanged(IResourceChangeEvent event)
    {
        if (event.getType() == IResourceChangeEvent.POST_CHANGE)
        {
            for (IResourceDelta projectDelta : event.getDelta().getAffectedChildren())
            {
                IResourceDelta fabricModJson = projectDelta.findMember(FABRIC_MOD_JSON);
                if (fabricModJson != null && fabricModJson.getResource() instanceof IFile metadataFile)
                    processMetadataFile(fabricModJson, metadataFile);
            }
        }
    }

    private void processMetadataFile(IResourceDelta fabricModJson, IFile metadataFile)
    {
        IProject project = fabricModJson.getResource().getProject();
        switch (fabricModJson.getKind())
        {
        case IResourceDelta.ADDED:
        case IResourceDelta.CHANGED:
            try
            {
                // Only reparse the file if it has no errors
                IMarker[] errors = metadataFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
                if (errors.length == 0)
                {
                    metadata.computeIfAbsent(project, ProjectMetadataStore::new)
                        .setProjectMetadata(readModMetadata(metadataFile));
                }
            }
            catch (CoreException e)
            {
                e.printStackTrace();
            }
            break;
        case IResourceDelta.REMOVED:
            ProjectMetadataStore store = metadata.remove(project);
            if (store != null)
            {
                for (String config : store.projectMetadata.getMixinConfigs())
                    MixinStore.INSTANCE.removeByConfig(project, config);
            }
            break;
        }
    }

    private class ProjectMetadataStore
    {
        private final IProject project;
        private ModMetadata projectMetadata;

        private ProjectMetadataStore(IProject project)
        {
            this.project = project;
        }

        void setProjectMetadata(ModMetadata newMetadata)
        {
            Set<String> existingConfigs = projectMetadata != null
                ? projectMetadata.getMixinConfigs() : Collections.emptySet();
            var added = Sets.difference(newMetadata.getMixinConfigs(), existingConfigs);
            for (String config : added)
                MixinStore.INSTANCE.loadConfig(project, config);
            var removed = Sets.difference(existingConfigs, newMetadata.getMixinConfigs());
            for (String config : removed)
                MixinStore.INSTANCE.removeByConfig(project, config);
            this.projectMetadata = newMetadata;
        }
    }
}
