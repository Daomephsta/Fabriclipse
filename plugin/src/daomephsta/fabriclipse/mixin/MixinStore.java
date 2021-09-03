package daomephsta.fabriclipse.mixin;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension5;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import daomephsta.fabriclipse.metadata.Mod;
import daomephsta.fabriclipse.util.Mixins;

public class MixinStore implements IResourceChangeListener
{
    public static final MixinStore INSTANCE = new MixinStore();
    private final Map<IProject, CompletableFuture<ProjectMixins>> mixinsByProject = new ConcurrentHashMap<>();

    public CompletableFuture<Collection<MixinInfo>> mixinsFor(IProject project, String targetClass)
    {
        return byProject(project)
            .thenApply(m -> m.byTarget.get(targetClass));
    }

    public CompletableFuture<Void> loadConfig(IProject project, Mod mod, String config)
    {
        return byProject(project).thenAcceptAsync(mixins ->
        {
            try
            {
                mixins.loadConfig(mod, config);
            }
            catch (CoreException | IOException e)
            {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Void> removeByConfig(IProject project, String config)
    {
        return MixinStore.INSTANCE.byProject(project)
            .thenAccept(mixins -> mixins.removeByConfig(config));
    }

    private CompletableFuture<ProjectMixins> byProject(IProject project)
    {
        return mixinsByProject.computeIfAbsent(project,
            k -> CompletableFuture.supplyAsync(() -> ProjectMixins.forProject(k)));
    }

    public record MixinInfo(String target, IType mixin) {}

    @Override
    public void resourceChanged(IResourceChangeEvent event)
    {
        if (event.getType() == IResourceChangeEvent.POST_CHANGE)
        {
            try
            {
                for (IResourceDelta projectDelta :
                    event.getDelta().getAffectedChildren())
                {
                    IProject project = (IProject) projectDelta.getResource();
                    event.getDelta().accept(delta ->
                        visitProjectDelta(project, delta));
                }
            }
            catch (CoreException e)
            {
                e.printStackTrace();
            }
        }
    }

    private boolean visitProjectDelta(IProject project, IResourceDelta delta) throws JavaModelException
    {
        if (delta.getResource() instanceof IFile file &&
            delta.getFullPath().getFileExtension().equals("java"))
        {
            var javaFile = (ICompilationUnit) JavaCore.create(file);
            for (IType type : javaFile.getAllTypes())
            {
                byProject(project).thenAccept(mixins ->
                {
                    if (mixins.all.contains(type))
                        processMixin(type);
                });
            }
        }
        return true;
    }

    private void processMixin(IType mixin)
    {
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        for (IEditorReference editor : activePage.getEditorReferences())
        {
            try
            {
                ITextViewer textViewer = editor.getEditor(false).getAdapter(ITextViewer.class);
                String openClassName = editor.getEditorInput().getAdapter(IClassFile.class)
                    .findPrimaryType().getFullyQualifiedName();
                if (Mixins.getTargetClasses(mixin).contains(openClassName) &&
                    textViewer instanceof ISourceViewerExtension5 sve5)
                {
                    sve5.updateCodeMinings();
                }
            }
            catch (PartInitException e)
            {
                e.printStackTrace();
            }
        }
    }
}
