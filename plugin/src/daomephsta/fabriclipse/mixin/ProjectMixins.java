package daomephsta.fabriclipse.mixin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import daomephsta.fabriclipse.Fabriclipse;
import daomephsta.fabriclipse.metadata.Mod;
import daomephsta.fabriclipse.metadata.ProjectEnvironmentManager;
import daomephsta.fabriclipse.mixin.MixinStore.MixinInfo;
import daomephsta.fabriclipse.util.Mixins;

public class ProjectMixins
{
    private static final Gson GSON = new GsonBuilder().create();
    private final IJavaProject javaProject;
    final Set<IType> all;
    final Multimap<String, MixinInfo> byTarget;
    final Multimap<String, MixinInfo> byConfig;

    private ProjectMixins(IProject project)
    {
        this.javaProject = JavaCore.create(project);
        this.all = Sets.newConcurrentHashSet();
        this.byTarget = concurrentMultimap();
        this.byConfig = concurrentMultimap();
    }

    static ProjectMixins forProject(IProject project)
    {
        ProjectMixins mixins = new ProjectMixins(project);
        mixins.loadAllConfigs(project).join();
        return mixins;
    }

    void removeByConfig(String config)
    {
        for (MixinInfo info : byConfig.removeAll(config))
        {
            byTarget.remove(info.target(), info.mixin());
            all.remove(info.mixin());
        }
    }

    private CompletableFuture<Void> loadAllConfigs(IProject project)
    {
        return ProjectEnvironmentManager.INSTANCE.getProjectEnvironment(project).thenAccept(environment ->
        {
            for (Mod mod : environment.allMods())
            {
                for (String config : mod.getMetadata().getMixinConfigs())
                {
                    try
                    {
                        loadConfig(mod, config);
                    }
                    catch (CoreException | IOException e)
                    {
                        Fabriclipse.LOGGER.error("Loading " + config + " for " + project.getName(), e);
                    }
                }
            }
        });
    }

    void loadConfig(Mod mod, String config) throws CoreException, IOException
    {
        for (String mixinName : readMixinNames(new InputStreamReader(mod.openResource(config))))
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

    private Iterable<String> readMixinNames(Reader configContents)
    {
        JsonObject root = GSON.fromJson(configContents, JsonObject.class);
        String packageName = root.get("package").getAsString();
        return Stream.of("mixins", "client", "server")
            .filter(root::has)
            .flatMap(key -> Streams.stream(root.get(key).getAsJsonArray()))
            .map(localName -> packageName + '.' + localName.getAsString())
            .toList();
    }

    private static <K, V> Multimap<K, V> concurrentMultimap()
    {
        return Multimaps.newMultimap(new ConcurrentHashMap<>(), Sets::newConcurrentHashSet);
    }
}