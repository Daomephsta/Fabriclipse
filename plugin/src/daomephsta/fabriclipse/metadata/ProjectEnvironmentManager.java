package daomephsta.fabriclipse.metadata;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import daomephsta.fabriclipse.Fabriclipse;
import daomephsta.fabriclipse.mixin.MixinStore;

public class ProjectEnvironmentManager implements IResourceChangeListener
{
    public static final ProjectEnvironmentManager INSTANCE = new ProjectEnvironmentManager();
    private static final IPath FABRIC_MOD_JSON = Path.fromPortableString("src/main/resources/fabric.mod.json");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ModMetadata.class, (JsonDeserializer<ModMetadata>) ModMetadata::deserialize)
        .create();
    private final Map<IProject, CompletableFuture<ProjectEnvironment>> environments = new ConcurrentHashMap<>();

    public CompletableFuture<ProjectEnvironment> getProjectEnvironment(IProject project)
    {
        return environments.computeIfAbsent(project, iProject -> CompletableFuture.supplyAsync(() ->
        {
            ProjectEnvironment environment = new ProjectEnvironment(iProject);
            IFile fabricModJson = iProject.getFile(FABRIC_MOD_JSON);
            if (fabricModJson.exists())
                environment.setProjectMod(new ProjectMod(readModMetadata(fabricModJson), project));

            IJavaProject javaProject = JavaCore.create(iProject);
            try
            {
                for (IClasspathEntry entry : javaProject.getResolvedClasspath(true))
                {
                    if ("jar".equals(entry.getPath().getFileExtension()))
                        processJar(environment, entry.getPath());
                }
            }
            catch (JavaModelException e)
            {
                Fabriclipse.LOGGER.error("Classpath resolution failed", e);
            }
            return environment;
        }));
    }

    private void processJar(ProjectEnvironment environment, IPath jarPath)
    {
        File jarFile = jarPath.toFile();
        if (!jarFile.exists())
            return;
        try (JarFile jar = new JarFile(jarFile))
        {
            JarEntry jarModMetadata = jar.getJarEntry("fabric.mod.json");
            if (jarModMetadata != null)
            {
                try (Reader reader = new InputStreamReader(jar.getInputStream(jarModMetadata)))
                {
                    environment.addMod(jarPath,
                        new JarMod(GSON.fromJson(reader, ModMetadata.class), jarPath));
                }
            }
        }
        catch (IOException e)
        {
            Fabriclipse.LOGGER.error("Reading " + jarFile, e);
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
                    ModMetadata metadata = readModMetadata(metadataFile);
                    getProjectEnvironment(project).thenAccept(environment ->
                    {
                        Mod projectMod = environment.projectMod;
                        ModMetadata oldMetadata = projectMod.getMetadata();
                        Set<String> existingConfigs = oldMetadata != null
                            ? oldMetadata.getMixinConfigs() : Collections.emptySet();
                        var added = Sets.difference(metadata.getMixinConfigs(), existingConfigs);
                        for (String config : added)
                            MixinStore.INSTANCE.loadConfig(environment.project, projectMod, config);
                        var removed = Sets.difference(existingConfigs, metadata.getMixinConfigs());
                        for (String config : removed)
                            MixinStore.INSTANCE.removeByConfig(project, config);
                        projectMod.setMetadata(metadata);
                    });
                }
            }
            catch (CoreException e)
            {
                Fabriclipse.LOGGER.error("Processing " + metadataFile, e);
            }
            break;
        case IResourceDelta.REMOVED:
            CompletableFuture<ProjectEnvironment> environmentFuture = environments.remove(project);
            if (environmentFuture != null)
            {
                environmentFuture.thenAccept(environment ->
                {
                    for (String config : environment.projectMod.getMetadata().getMixinConfigs())
                        MixinStore.INSTANCE.removeByConfig(project, config);
                });
            }
            break;
        }
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

    public static class ProjectEnvironment
    {
        private final IProject project;
        private Mod projectMod;
        private final Map<IPath, Mod> classpathMods = new ConcurrentHashMap<>();

        ProjectEnvironment(IProject project)
        {
            this.project = project;
        }

        public Iterable<Mod> allMods()
        {
            return Iterables.concat(classpathMods.values(), Collections.singleton(projectMod));
        }

        void setProjectMod(Mod projectMod)
        {
            this.projectMod = projectMod;
        }

        void addMod(IPath modPath, Mod mod)
        {
            this.classpathMods.put(modPath, mod);
        }
    }
}
