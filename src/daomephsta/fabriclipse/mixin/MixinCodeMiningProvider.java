package daomephsta.fabriclipse.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import daomephsta.fabriclipse.util.JdtAnnotations;

public class MixinCodeMiningProvider extends AbstractCodeMiningProvider
{
    @Override
    public CompletableFuture<List<? extends ICodeMining>>
        provideCodeMinings(ITextViewer viewer, IProgressMonitor monitor)
    {
        IClassFile openClass = getAdapter(ITextEditor.class).getEditorInput().getAdapter(IClassFile.class);
        IType openType = openClass.findPrimaryType();
        IProject project = openClass.getJavaProject().getProject();
        return MixinStore.INSTANCE.mixinsFor(project, openType.getFullyQualifiedName())
            .thenApplyAsync(mixins -> computeMinings(mixins, viewer.getDocument(), openType));
    }

    private List<? extends ICodeMining> computeMinings(
        Collection<IType> mixins, IDocument document, IType openType)
    {
        Multimap<IMethod, IMethod> injects = HashMultimap.create();
        List<ICodeMining> minings = new ArrayList<>();
        try
        {
            for (IType mixin : mixins)
            {
                for (IMethod method : mixin.getMethods())
                {
                    IAnnotation inject = method.getAnnotation("Inject");
                    if (inject != null)
                        processInjection(openType, method, inject, injects);
                }
            }
            for (IMethod target : injects.keySet())
                minings.add(MixinCodeMining.create(target, injects.get(target), document, this));
        }
        catch (BadLocationException | JavaModelException e)
        {
            e.printStackTrace();
        }
        return minings;
    }

    private void processInjection(
        IType openType, IMethod handler, IAnnotation inject, Multimap<IMethod, IMethod> injects)
        throws JavaModelException
    {
        for (String method : JdtAnnotations.MemberType.STRING.getArray(inject, "method"))
        {
            String name = method.substring(0, method.indexOf('('));
            IMethod target = openType.getMethod(name, Signature.getParameterTypes(method));
            injects.put(target, handler);
        }
    }

    @Override
    public void dispose()
    {}

    private static class MixinCodeMining extends LineHeaderCodeMining
    {
        private final Collection<IMethod> handlers;

        private MixinCodeMining(
            int beforeLineNumber, IDocument document, ICodeMiningProvider provider, Collection<IMethod> handlers)
            throws BadLocationException
        {
            super(beforeLineNumber, document, provider);
            this.handlers = handlers;
            setLabel(handlers.size() > 1
                ? handlers.size() + " @Injects"
                : "1 @Inject");
        }

        static MixinCodeMining create(
            IMethod target, Collection<IMethod> handlers, IDocument document, ICodeMiningProvider provider)
            throws BadLocationException, JavaModelException
        {
            int line = document.getLineOfOffset(target.getSourceRange().getOffset());
            return new MixinCodeMining(line, document, provider, handlers);
        }

        @Override
        public Consumer<MouseEvent> getAction()
        {
            return event ->
            {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                Menu pop = new Menu(shell, SWT.POP_UP);
                for (IMethod handler : handlers)
                {
                    MenuItem handlerItem = new MenuItem(pop, SWT.PUSH);
                    handlerItem.setText(handler.getDeclaringType().getFullyQualifiedName() +
                        '.' + handler.getElementName() + "(...)");
                    handlerItem.addSelectionListener(SelectionListener.widgetSelectedAdapter(click ->
                    {
                        IWorkbenchPage page = PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow().getActivePage();
                        try
                        {
                            IDE.openEditor(page, (IFile) handler.getResource());
                        }
                        catch (PartInitException e)
                        {
                            e.printStackTrace();
                        }
                    }));
                }
                pop.setLocation(Display.getCurrent().getCursorLocation());
                pop.setVisible(true);
            };
        }
    }
}
