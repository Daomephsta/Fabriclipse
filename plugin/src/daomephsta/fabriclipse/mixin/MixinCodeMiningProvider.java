package daomephsta.fabriclipse.mixin;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.osgi.service.prefs.BackingStoreException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import daomephsta.fabriclipse.Fabriclipse;
import daomephsta.fabriclipse.mixin.MixinStore.MixinInfo;
import daomephsta.fabriclipse.util.JdtAnnotations;
import daomephsta.fabriclipse.util.codemining.ToggleableCodeMining;

public class MixinCodeMiningProvider extends AbstractCodeMiningProvider
{
    private static final Set<String> INJECTORS = Stream.of(
        "Inject", "ModifyArg", "ModifyArgs", "ModifyConstant", "ModifyVariable", "Redirect")
        .map("org.spongepowered.asm.mixin.injection."::concat).collect(toSet());
    private static final Pattern INVOKER_TARGET = Pattern.compile("(?:call|invoke)([\\w$\\-])([\\w$\\-]+)"),
                                 ACCESSOR_TARGET = Pattern.compile("(?:get|set|is)([\\w$\\-])([\\w$\\-]+)");
    private static final String
        PREF_QUALIFIER = "daomephsta.fabriclipse.mixin",
        PREF_KEY = "minings",
        FULL_PREF_KEY = PREF_QUALIFIER + '.' + PREF_KEY;

    @Override
    public CompletableFuture<List<? extends ICodeMining>>
        provideCodeMinings(ITextViewer viewer, IProgressMonitor monitor)
    {
        IClassFile openClass = getAdapter(ITextEditor.class).getEditorInput().getAdapter(IClassFile.class);
        IType openType = openClass.findPrimaryType();
        IProject project = openClass.getJavaProject().getProject();
        return MixinStore.INSTANCE.mixinsFor(project, openType.getFullyQualifiedName('.'))
            .thenApplyAsync(mixins -> computeMinings(mixins, viewer.getDocument(), openType));
    }

    private List<? extends ICodeMining> computeMinings(
        Collection<MixinInfo> mixins, IDocument document, IType openType)
    {
        Multimap<MethodMiningKey, IMethod> methodMinings = HashMultimap.create();
        Multimap<FieldMiningKey, IMethod> fieldMinings = HashMultimap.create();
        List<ICodeMining> minings = new ArrayList<>();
        try
        {
            for (MixinInfo info : mixins)
            {
                for (IMethod method : info.mixin().getMethods())
                {
                    if (JdtAnnotations.get(method, "org.spongepowered.asm.mixin.Overwrite").exists())
                        processOverwrite(openType, method, methodMinings);
                    IAnnotation accessor = JdtAnnotations.get(method, "org.spongepowered.asm.mixin.gen.Accessor");
                    if (accessor.exists())
                        processAccessor(openType, accessor, method, fieldMinings);
                    IAnnotation invoker = JdtAnnotations.get(method, "org.spongepowered.asm.mixin.gen.Invoker");
                    if (invoker.exists())
                        processInvoker(openType, invoker, method, methodMinings);
                    for (String injectorName : INJECTORS)
                    {
                        IAnnotation injector = JdtAnnotations.get(method, injectorName);
                        if (injector.exists())
                            processInjector(openType, method, injector, methodMinings);
                    }
                }
            }
            for (Entry<MethodMiningKey, Collection<IMethod>> entry : methodMinings.asMap().entrySet())
            {
                Collection<IMethod> handlers = entry.getValue();
                if (SourceRange.isAvailable(entry.getKey().target.getSourceRange()))
                {
                    minings.add(createMethodCodeMining(entry.getKey().target, entry.getKey().type,
                        handlers, document, this));
                }
                else
                    Fabriclipse.LOGGER.error("No source range for " + entry.getKey().target);
            }
            for (Entry<FieldMiningKey, Collection<IMethod>> entry : fieldMinings.asMap().entrySet())
            {
                Collection<IMethod> handlers = entry.getValue();
                ISourceRange sourceRange = entry.getKey().target.getSourceRange();
                if (SourceRange.isAvailable(sourceRange) && entry.getKey().type.equals("@Accessor"))
                {
                    StringBuilder labelBuilder = new StringBuilder(" @Accessor: ");
                    int getters = 0, setters = 0;
                    for (IMethod handler : handlers)
                    {
                        if (handler.getNumberOfParameters() == 0)
                            getters += 1;
                        else
                            setters +=1;
                    }
                    if (getters > 0)
                    {
                        labelBuilder.append(getters + " get");
                        if (setters > 0) labelBuilder.append(' ');
                    }
                    if (setters > 0) labelBuilder.append(setters + " set");

                    IRegion lineInfo = document.getLineInformationOfOffset(sourceRange.getOffset());
                    int lineEnd = lineInfo.getOffset() + lineInfo.getLength();
                    var mining = ToggleableCodeMining.inline(new Position(lineEnd, labelBuilder.length()),
                        document, this, event -> showHandlerMenu(handlers), FULL_PREF_KEY);
                    mining.setLabel(labelBuilder.toString());
                    minings.add(mining);
                }
                else
                    Fabriclipse.LOGGER.error("No source range for " + entry.getKey().target);
            }
        }
        catch (BadLocationException | JavaModelException e)
        {
            Fabriclipse.LOGGER.error("Computing minings for " + openType.getFullyQualifiedName(), e);
        }
        return minings;
    }

    private void processOverwrite(
        IType openType, IMethod method, Multimap<MethodMiningKey, IMethod> injectors)
        throws JavaModelException
    {
        IMethod target = openType.getMethod(method.getElementName(),
            Signature.getParameterTypes(method.getSignature()));
        if (target.exists())
            injectors.put(new MethodMiningKey(target, "@Overwrite"), method);
        else
        {
            Fabriclipse.LOGGER.error("Target " + target.getElementName() +
                '(' + String.join("", target.getParameterTypes()) + ')' +
                " not found in " + openType.getElementName());
        }
    }

    private void processAccessor(
        IType openType, IAnnotation accessor, IMethod method, Multimap<FieldMiningKey, IMethod> accessors)
        throws JavaModelException
    {
        String targetName = getAccessorTarget(accessor, method);
        if (targetName.isEmpty())
            return;
        IField target = openType.getField(targetName);
        if (target.exists())
            accessors.put(new FieldMiningKey(target, "@Accessor"), method);
        else
        {
            Fabriclipse.LOGGER.error("Target " + target.getElementName() +
                " not found in " + openType.getElementName());
        }
    }

    private String getAccessorTarget(IAnnotation invoker, IMethod method)
        throws JavaModelException
    {
        String targetDesc;
        IMemberValuePair value = JdtAnnotations.member(invoker, "value");
        if (value != null)
            targetDesc = (String) value.getValue();
        else
        {
            Matcher matcher = ACCESSOR_TARGET.matcher(method.getElementName());
            if (!matcher.matches())
                return "";
            targetDesc = matcher.group(1).toLowerCase() + matcher.group(2);
        }
        return targetDesc;
    }


    private void processInvoker(
        IType openType, IAnnotation invoker, IMethod method, Multimap<MethodMiningKey, IMethod> injectors)
        throws JavaModelException
    {
        String targetDesc = getInvokerTarget(invoker, method);
        if (targetDesc.isEmpty())
            return;
        IMethod target = findMethod(openType, targetDesc);
        if (target != null)
            injectors.put(new MethodMiningKey(target, "Invoker"), method);
        else
        {
            Fabriclipse.LOGGER.error("Target " + targetDesc +
                " not found in " + openType.getElementName());
        }
    }

    private String getInvokerTarget(IAnnotation invoker, IMethod method)
        throws JavaModelException
    {
        String targetDesc;
        IMemberValuePair value = JdtAnnotations.member(invoker, "value");
        if (value != null)
            targetDesc = (String) value.getValue();
        else
        {
            Matcher matcher = INVOKER_TARGET.matcher(method.getElementName());
            if (!matcher.matches())
                return "";
            targetDesc = matcher.group(1).toLowerCase() + matcher.group(2) + method.getSignature();
        }
        return targetDesc;
    }

    private void processInjector(
        IType openType, IMethod handler, IAnnotation injector, Multimap<MethodMiningKey, IMethod> injects)
        throws JavaModelException
    {
        for (String method : JdtAnnotations.MemberType.STRING.getArray(injector, "method"))
        {
            String injectorType = "@" + injector.getElementName().substring(
                injector.getElementName().lastIndexOf('.') + 1);
            IMethod target = findMethod(openType, method);
            if (target != null)
                injects.put(new MethodMiningKey(target, injectorType), handler);
            else
            {
                Fabriclipse.LOGGER.error("Target " + method +
                    " not found in " + openType.getElementName());
            }
        }
    }

    private IMethod findMethod(IType type, String descriptor) throws JavaModelException
    {
        int parametersStart = descriptor.indexOf('(');
        if (parametersStart != -1)
        {
            String name = descriptor.substring(0, parametersStart);
            // Parameters types must be dot format, or the method isn't found
            String[] parameterTypes = Signature.getParameterTypes(descriptor.replace('/', '.'));
            IMethod method = type.getMethod(name, parameterTypes);
            return method.exists() ? method : null;
        }
        else
        {
            return Arrays.stream(type.getMethods())
                .filter(candidate -> candidate.getElementName().equals(descriptor))
                .reduce((a, b) -> {throw new IllegalArgumentException(
                    "Target " + descriptor + " is ambiguous");})
                .orElse(null);
        }
    }

    static ICodeMining createMethodCodeMining(IMethod target, String type, Collection<IMethod> handlers,
        IDocument document, ICodeMiningProvider provider)
        throws BadLocationException, JavaModelException
    {
        int line = document.getLineOfOffset(target.getSourceRange().getOffset());
        var mining = ToggleableCodeMining.header(line, document, provider,
            event -> showHandlerMenu(handlers), FULL_PREF_KEY);
        mining.setLabel(String.format("%d x %s", handlers.size(), type));
        return mining;
    }

    private static void showHandlerMenu(Collection<IMethod> handlers)
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
                try
                {
                    JavaUI.openInEditor(handler);
                }
                catch (CoreException e)
                {
                    Fabriclipse.LOGGER.error("Revealing " + handlerItem.getText(), e);
                }
            }));
        }
        pop.setLocation(Display.getCurrent().getCursorLocation());
        pop.setVisible(true);
    }

    public static class ToggleMiningsHandler extends AbstractHandler
    {
        @Override
        public Object execute(ExecutionEvent event) throws ExecutionException
        {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREF_QUALIFIER);
            prefs.putBoolean(PREF_KEY, !prefs.getBoolean(PREF_KEY, false));
            try // Force save
            {
                prefs.flush();
            }
            catch (BackingStoreException e)
            {
                Fabriclipse.LOGGER.error("Flushing preferences", e);
            }
            // Update and redraw code minings. Cursed, but I can find no other way
            if (HandlerUtil.getActiveEditor(event) instanceof ITextEditor editor &&
                editor.getSelectionProvider().getSelection() instanceof ITextSelection selection)
            {
                TextSelection jogCaret = new TextSelection(
                    editor.getDocumentProvider().getDocument(null),
                    selection.getOffset() + 1, selection.getLength());
                editor.getSelectionProvider().setSelection(jogCaret);
                editor.getSelectionProvider().setSelection(selection);
            }
            return null;
        }
    }

    private record MethodMiningKey(IMethod target, String type)
    {
        @Override
        public int hashCode()
        {
            return Objects.hash(target.getKey(), type);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (obj instanceof MethodMiningKey other)
            {
                return this.target.getKey().equals(other.target.getKey()) &&
                    this.type.equals(other.type);
            }
            return false;
        }
    }

    private record FieldMiningKey(IField target, String type)
    {
        @Override
        public int hashCode()
        {
            return Objects.hash(target.getKey(), type);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (obj instanceof FieldMiningKey other)
            {
                return this.target.getKey().equals(other.target.getKey()) &&
                    this.type.equals(other.type);
            }
            return false;
        }
    }
}
