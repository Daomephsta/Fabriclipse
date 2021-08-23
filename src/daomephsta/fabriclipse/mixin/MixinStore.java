package daomephsta.fabriclipse.mixin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import daomephsta.fabriclipse.metadata.ModMetadata;
import daomephsta.fabriclipse.metadata.ModMetadataStore;
import daomephsta.fabriclipse.util.JdtAnnotations;

public class MixinStore
{
    public static final MixinStore INSTANCE = new MixinStore();
    private static final Gson GSON = new GsonBuilder().create();
    private Map<String, Collection<IType>> mixinInfo;

    public CompletableFuture<Collection<IType>> mixinsFor(IProject project, String targetClass)
    {
        if (mixinInfo == null)
        {
            return CompletableFuture.supplyAsync(() ->
            {
                // To avoid race condition, use local map and assign
                // to member field when computation is finished
                Map<String, Collection<IType>> mixinInfo = new ConcurrentHashMap<>();
                IJavaProject javaProject = JavaCore.create(project);
                ModMetadata metadata = ModMetadataStore.INSTANCE.getProjectMetadata(project);
                try
                {
                    for (String config : metadata.getMixinConfigs())
                    {
                        for (String mixinName : readMixinNames(project.getFile("src/main/resources/" + config)))
                        {
                            IType mixinClass = javaProject.findType(mixinName);
                            for (String target : getTargetClasses(mixinClass))
                            {
                                mixinInfo.computeIfAbsent(target, k -> Sets.newConcurrentHashSet())
                                    .add(mixinClass);
                            }
                        }
                    }
                }
                catch (JavaModelException e)
                {
                    e.printStackTrace();
                }
                this.mixinInfo = mixinInfo;
                return mixinInfo.get(targetClass);
            });
        }
        else
        {
            return CompletableFuture.completedFuture(
                mixinInfo.getOrDefault(targetClass, Collections.emptyList()));
        }
    }

    private Iterable<String> getTargetClasses(IType mixinClass) throws JavaModelException
    {
        IAnnotation mixinAnnotation = mixinClass.getAnnotation("Mixin");
        return Stream.concat(
            JdtAnnotations.MemberType.CLASS.stream(mixinAnnotation, "value"),
            JdtAnnotations.MemberType.STRING.stream(mixinAnnotation, "targets"))
                .map(type -> resolveType(mixinClass, type))
                .toList();
    }

    private String resolveType(IType against, String type)
    {
        try
        {
            String[][] resolved = against.resolveType(type);
            if (resolved == null)
                throw new IllegalStateException("Resolution of " + type + " in " + against + " failed");
            if (resolved.length != 1)
                throw new IllegalStateException(type + " in " + against + " is ambiguous");
            return String.join(".", resolved[0]);
        }
        catch (JavaModelException e)
        {
            throw new IllegalStateException("Resolution of " + type + " in " + against + " failed", e);
        }
    }

    private Iterable<String> readMixinNames(IFile mixinConfig)
    {
        try (Reader reader = new InputStreamReader(mixinConfig.getContents()))
        {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            String packageName = root.get("package").getAsString();
            return Stream.of("mixins", "client", "server")
                .filter(root::has)
                .flatMap(key -> Streams.stream(root.get(key).getAsJsonArray()))
                .map(localName -> packageName + '.' + localName.getAsString())
                .toList();
        }
        catch (IOException | CoreException e)
        {
            throw new RuntimeException("Failed to read mixin config " + mixinConfig.getName(), e);
        }
    }
}
