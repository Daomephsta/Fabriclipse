package daomephsta.fabriclipse.util;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public class BytecodeTypeView
{
    private final IType delegate;

    public BytecodeTypeView(IType delegate)
    {
        this.delegate = delegate;
    }

    public String getFullSourceName()
    {
        return delegate.getFullyQualifiedName('.');
    }

    public String getSimpleSourceName()
    {
        return delegate.getElementName();
    }

    public IMethod getMethod(String name, String[] parameterTypes) throws JavaModelException
    {
        // IType.getMethod() requires the simple type name instead of <init>
        if (name.equals("<init>"))
            name = delegate.getElementName();
        // <clinit> works with IType.getMethod() as is
        IMethod method = delegate.getMethod(name, parameterTypes);
        if (!method.exists())
        {
            methods:
            for (IMethod candidate : delegate.getMethods())
            {
                if (!candidate.getElementName().equals(name) ||
                    candidate.getNumberOfParameters() != parameterTypes.length ||
                    (candidate.getTypeParameters().length == 0 &&
                        delegate.getTypeParameters().length == 0))
                {
                    continue methods;
                }
                for (int i = 0; i < parameterTypes.length; i++)
                {
                    String erased = erase(candidate, candidate.getParameterTypes()[i]);
                    if (!erased.equals(parameterTypes[i]))
                        continue methods;
                }
                return candidate;
            }
            return null;
        }
        return method;
    }

    private String erase(IMethod candidate, String signature) throws JavaModelException
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

    public IField getField(String name)
    {
        return delegate.getField(name);
    }

    public Stream<IMethod> getOverloads(String name) throws JavaModelException
    {
        return Arrays.stream(delegate.getMethods())
            .filter(candidate -> candidate.getElementName().equals(name));
    }
}
