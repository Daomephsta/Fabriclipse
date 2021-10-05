package daomephsta.fabriclipse.mixin;

import java.util.regex.Pattern;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public class MethodSpec
{
    private static final Pattern PATTERN = Pattern.compile(
        "(?<owner>L[A-z$_0-9\\x{00C0}-\\x{FFFF}\\/]+;)?" +
        "(?<name>[A-z$_0-9\\/\\x{00C0}-\\x{FFFF}<>]+)?" +
        "(?<quantifier>\\*|\\+|\\{\\d?,?\\d?\\})?" +
        "(?<desc>\\([A-z$_0-9\\/\\x{00C0}-\\x{FFFF};]+\\)[A-z$_0-9\\/\\x{00C0}-\\x{FFFF};]+)?");
    final String owner, name;
    final String[] parameterTypes;
    final String returnType;
    final Quantifier quantifier;
    final String raw;

    private MethodSpec(String owner, String name, Quantifier quantifier, String descriptor, String raw)
    {
        this.owner = owner;
        this.name = name;
        if (descriptor != null)
        {
            this.parameterTypes = Signature.getParameterTypes(descriptor);
            this.returnType = Signature.getReturnType(descriptor);
        }
        else
        {
            this.parameterTypes = null;
            this.returnType = null;
        }
        this.quantifier = quantifier;
        this.raw = raw;
    }

    public boolean matches(IType candidateOwner, IMethod candidate) throws JavaModelException
    {
        if (name != null && !name.equals(candidate.getElementName()) &&
            !name.equals("<init>") && !candidate.getElementName().equals(candidateOwner.getElementName()))
        {
            return false;
        }
        if (parameterTypes != null && candidate.getNumberOfParameters() == parameterTypes.length)
        {
            for (int i = 0; i < parameterTypes.length; i++)
            {
                String erased = erase(candidate, candidate.getParameterTypes()[i]);
                if (!areTypesEqual(erased, parameterTypes[i]))
                    return false;
            }
        }
        if (!quantifier.matches())
            return false;
        return true;
    }

    public void assertSatisfied(String context)
    {
        quantifier.assertSatisfied(context);
    }

    private static String erase(IMethod candidate, String signature) throws JavaModelException
    {
        if (Signature.getTypeSignatureKind(signature) == Signature.TYPE_VARIABLE_SIGNATURE)
        {
            var typeParameter = candidate.getTypeParameter(Signature.getSignatureSimpleName(signature));
            String[] bounds = typeParameter.getBoundsSignatures();
            if (bounds.length == 0)
                return Signature.createTypeSignature(Object.class.getName(), true);
            // Erasure always uses the first bound
            return bounds[0];
        }
        return Signature.getTypeErasure(signature);
    }

    private static boolean areTypesEqual(String typeA, String typeB)
    {
        if (typeA.length() != typeB.length())
            return false;
        for (int i = 0; i < typeA.length(); i++)
        {
            char a = typeA.charAt(i),
                 b = typeB.charAt(i);
            if ((a == '/' && b == '.') || (a == '.' && b == '/'))
                continue;
            if (a != b)
                return false;
        }
        return true;
    }

    public static MethodSpec parse(String methodSpec)
    {
        // Easiest way to handle dot-separated owners
        String toParse = methodSpec,
               owner = null;
        int lastDot = methodSpec.lastIndexOf('.');
        if (lastDot != -1)
        {
            toParse = methodSpec.substring(lastDot + 1);
            owner = methodSpec.substring(0, lastDot);
        }
        var matcher = PATTERN.matcher(toParse);
        if (matcher.matches())
        {
            if (owner == null)
                owner = matcher.group("owner");
            return new MethodSpec(
                owner,
                matcher.group("name"),
                Quantifier.parse(matcher.group("quantifier")),
                matcher.group("desc"),
                methodSpec);
        }
        throw new IllegalArgumentException("Invalid target method " + methodSpec);
    }
}
