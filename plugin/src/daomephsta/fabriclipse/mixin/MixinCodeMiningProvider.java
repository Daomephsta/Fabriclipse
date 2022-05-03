package daomephsta.fabriclipse.mixin;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
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
import org.eclipse.ui.IEditorPart;
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
        Optional<IClassFile> openClass = Optional.ofNullable(getAdapter(ITextEditor.class))
            .map(IEditorPart::getEditorInput)
            .map(input -> Adapters.adapt(input, IClassFile.class));
        if (openClass.isEmpty())
            return CompletableFuture.completedFuture(Collections.emptyList());
        IType openType = openClass.get().findPrimaryType();
        IJavaProject javaProject = openClass.get().getJavaProject();
        if (openType == null || javaProject == null)
            return CompletableFuture.completedFuture(Collections.emptyList());
        return MixinStore.INSTANCE.mixinsFor(javaProject.getProject(), openType.getFullyQualifiedName('.'))
            .thenApplyAsync(mixins -> computeMinings(mixins, viewer.getDocument(), openType));
    }

    record Injection(String type, IMethod handler, MethodSpec target) {}

    private List<? extends ICodeMining> computeMinings(
        Collection<MixinInfo> mixins, IDocument document, IType openType)
    {
        Multimap<String, Injection> injections = HashMultimap.create();
        Multimap<MethodMiningKey, IMethod> methodMinings = HashMultimap.create();
        Multimap<FieldMiningKey, IMethod> fieldMinings = HashMultimap.create();
        processMixins(mixins, openType, injections, methodMinings, fieldMinings);
        matchInjectionTargets(openType, injections, methodMinings);
        List<ICodeMining> minings = new ArrayList<>();
        for (Map.Entry<MethodMiningKey, Collection<IMethod>> entry : methodMinings.asMap().entrySet())
        {
            try
            {
                Collection<IMethod> handlers = entry.getValue();
                if (SourceRange.isAvailable(entry.getKey().target.getSourceRange()))
                {
                    minings.add(createMethodCodeMining(entry.getKey().target, entry.getKey().type,
                        handlers, document, this));
                }
                else if (!Flags.isSynthetic(entry.getKey().target.getFlags()))
                    Fabriclipse.LOGGER.error("No source range for " + entry.getKey().target);
            }
            catch (JavaModelException | BadLocationException e)
            {
                Fabriclipse.LOGGER.error("Creating code mining for " + entry.getKey(), e);
            }
        }
        for (Map.Entry<FieldMiningKey, Collection<IMethod>> entry : fieldMinings.asMap().entrySet())
        {
            try
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
                else if (!Flags.isSynthetic(entry.getKey().target.getFlags()))
                    Fabriclipse.LOGGER.error("No source range for " + entry.getKey().target);
            }
            catch (JavaModelException | BadLocationException e)
            {
                Fabriclipse.LOGGER.error("Creating code mining for " + entry.getKey(), e);
            }
        }
        return minings;
    }

    private void matchInjectionTargets(IType openType, Multimap<String, Injection> injections,
        Multimap<MethodMiningKey, IMethod> methodMinings)
    {
        try
        {
            for (IMethod method : openType.getMethods())
            {
                String name = method.getElementName().equals(openType.getElementName())
                    ? "<init>" : method.getElementName();
                for (Injection injection : injections.get(name))
                {
                    if (injection.target.matches(openType, method))
                        methodMinings.put(new MethodMiningKey(method, injection.type), injection.handler);
                }
            }
        }
        catch (JavaModelException e)
        {
            Fabriclipse.LOGGER.error("Matching mixin targets for " + openType.getFullyQualifiedName('.'), e);
        }
        for (Injection injection : injections.values())
        {
            injection.target.assertSatisfied(
                injection.target.raw + "." + openType.getFullyQualifiedName('.') + " from " +
                injection.handler.getDeclaringType().getFullyQualifiedName('.'));
        }
    }

    private void processMixins(Collection<MixinInfo> mixins, IType openType,
        Multimap<String, Injection> injections, Multimap<MethodMiningKey, IMethod> methodMinings,
        Multimap<FieldMiningKey, IMethod> fieldMinings)
    {
        for (MixinInfo info : mixins)
        {
            try
            {
                for (IMethod method : info.mixin().getMethods())
                {
                    if (JdtAnnotations.get(method, "org.spongepowered.asm.mixin.Overwrite").exists())
                        processOverwrite(openType, method, methodMinings);
                    var accessor = JdtAnnotations.get(method, "org.spongepowered.asm.mixin.gen.Accessor");
                    if (accessor.exists())
                        processAccessor(openType, accessor, method, fieldMinings);
                    var invoker = JdtAnnotations.get(method, "org.spongepowered.asm.mixin.gen.Invoker");
                    if (invoker.exists())
                        processInvoker(openType, invoker, method, methodMinings);
                    for (String injectorName : INJECTORS)
                    {
                        IAnnotation injector = JdtAnnotations.get(method, injectorName);
                        if (injector.exists())
                        {
                            processInjector(openType, method, injector, injection ->
                                injections.put(injection.target.name, injection));
                        }
                    }
                }
            }
            catch (JavaModelException e)
            {
                Fabriclipse.LOGGER.error("Gathering mixin handlers for " + openType.getFullyQualifiedName('.') +
                    " from " + info.mixin().getFullyQualifiedName('.'), e);
            }
        }
    }

    private void processOverwrite(IType openType, IMethod method,
        Multimap<MethodMiningKey, IMethod> injectors)
        throws JavaModelException
    {
        IMethod target = openType.getMethod(method.getElementName(),
            Signature.getParameterTypes(method.getSignature()));
        if (target.exists())
            injectors.put(new MethodMiningKey(target, "@Overwrite"), method);
        else
        {
            Fabriclipse.LOGGER.error("Overwrite target " + target.getElementName() +
                '(' + String.join("", target.getParameterTypes()) + ')' +
                " not found in " + openType.getFullyQualifiedName('.'));
        }
    }

    private void processAccessor(IType openType, IAnnotation accessor, IMethod method,
        Multimap<FieldMiningKey, IMethod> accessors)
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
            Fabriclipse.LOGGER.error("Accessor target " + target.getElementName() +
                " not found in " + openType.getFullyQualifiedName('.'));
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


    private void processInvoker(IType openType, IAnnotation invoker, IMethod method,
        Multimap<MethodMiningKey, IMethod> injectors)
        throws JavaModelException
    {
        String targetDesc = getInvokerTarget(invoker, method);
        if (targetDesc.isEmpty())
            return;
        if (!visitTargets(openType, targetDesc,
            target -> injectors.put(new MethodMiningKey(target, "Invoker"), method)))
        {
            Fabriclipse.LOGGER.error("Invoker target " + targetDesc +
                " not found in " + openType.getFullyQualifiedName('.'));
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

    private void processInjector(IType openType, IMethod handler, IAnnotation injector,
        Consumer<Injection> injections)
        throws JavaModelException
    {
        String injectorType = "@" + injector.getElementName().substring(
            injector.getElementName().lastIndexOf('.') + 1);
        for (String method : JdtAnnotations.MemberType.STRING.getArray(injector, "method"))
            injections.accept(new Injection(injectorType, handler, MethodSpec.parse(method)));
    }

    private boolean visitTargets(IType type, String descriptor, Consumer<IMethod> visitor)
        throws JavaModelException
    {
        return false;
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
