package daomephsta.fabriclipse.util;

import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public class Mixins
{
    public static Set<String> getTargetClasses(IType mixinClass)
    {
        IAnnotation mixinAnnotation = mixinClass.getAnnotation(
            mixinClass.isBinary() ? "org.spongepowered.asm.mixin.Mixin" : "Mixin");
        try
        {
            return Stream.concat(
                JdtAnnotations.MemberType.CLASS.stream(mixinAnnotation, "value"),
                JdtAnnotations.MemberType.STRING.stream(mixinAnnotation, "targets"))
                    .map(type -> mixinClass.isBinary() ? type : resolveType(mixinClass, type))
                    .collect(toSet());
        }
        catch (JavaModelException e)
        {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    private static String resolveType(IType against, String type)
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
}
