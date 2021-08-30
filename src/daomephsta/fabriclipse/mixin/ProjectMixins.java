package daomephsta.fabriclipse.mixin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import daomephsta.fabriclipse.metadata.ModMetadata;
import daomephsta.fabriclipse.metadata.ModMetadataStore;
import daomephsta.fabriclipse.mixin.MixinStore.MixinInfo;
import daomephsta.fabriclipse.util.Mixins;

public class ProjectMixins
{
    private static final Gson GSON = new GsonBuilder().create();
    private final IJavaProject javaProject;
    final Set<IType> all;
    final Multimap<String, MixinInfo> byTarget;
    final Multimap<String, MixinInfo> byConfig;

    public ProjectMixins(IProject project)
    {
        this.javaProject = JavaCore.create(project);
        this.all = Sets.newConcurrentHashSet();
        this.byTarget = concurrentMultimap();
        this.byConfig = concurrentMultimap();
        loadAllConfigs(project);
    }

    void removeByConfig(String config)
    {
        for (MixinInfo info : byConfig.removeAll(config))
        {
            byTarget.remove(info.target(), info.mixin());
            all.remove(info.mixin());
        }
    }

    private void loadAllConfigs(IProject project)
    {
        ModMetadata metadata = ModMetadataStore.INSTANCE.getProjectMetadata(project);
        try
        {
            for (String config : metadata.getMixinConfigs())
                loadConfig(config);
        }
        catch (JavaModelException e)
        {
            e.printStackTrace();
        }
    }

    void loadConfig(String config) throws JavaModelException
    {
        IFile configFile = javaProject.getProject().getFile("src/main/resources/" + config);
        if (!configFile.exists())
            return;
        for (String mixinName : readMixinNames(configFile))
        {
            IType mixinClass = javaProject.findType(mixinName);
            for (String target : Mixins.getTargetClasses(mixinClass))
            {
                MixinInfo info = new MixinInfo(target, mixinClass);
                byTarget.put(info.target(), info);
                byConfig.put(config, info);
                all.add(info.mixin());
            }
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

    private static <K, V> Multimap<K, V> concurrentMultimap()
    {
        return Multimaps.newMultimap(new ConcurrentHashMap<>(), Sets::newConcurrentHashSet);
    }
}