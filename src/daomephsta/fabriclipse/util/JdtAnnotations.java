package daomephsta.fabriclipse.util;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.JavaModelException;

public class JdtAnnotations
{
    public static IMemberValuePair member(IAnnotation annotation, String name) throws JavaModelException
    {
        for (IMemberValuePair member : annotation.getMemberValuePairs())
        {
            if (member.getMemberName().equals(name))
                return member;
        }
        return null;
    }

    public record MemberType<T>(Class<T> tClass, IntFunction<T[]> arrayFactory, int kind)
    {
        public static final MemberType<Integer> INT =
            new MemberType<>(Integer.class, Integer[]::new, IMemberValuePair.K_INT);
        public static final MemberType<Byte> BYTE =
            new MemberType<>(Byte.class, Byte[]::new, IMemberValuePair.K_BYTE);
        public static final MemberType<Short> SHORT =
            new MemberType<>(Short.class, Short[]::new, IMemberValuePair.K_SHORT);
        public static final MemberType<Character> CHAR =
            new MemberType<>(Character.class, Character[]::new, IMemberValuePair.K_CHAR);
        public static final MemberType<Float> FLOAT =
            new MemberType<>(Float.class, Float[]::new, IMemberValuePair.K_FLOAT);
        public static final MemberType<Double> DOUBLE =
            new MemberType<>(Double.class, Double[]::new, IMemberValuePair.K_DOUBLE);
        public static final MemberType<Long> LONG =
            new MemberType<>(Long.class, Long[]::new, IMemberValuePair.K_LONG);
        public static final MemberType<Boolean> BOOLEAN =
            new MemberType<>(Boolean.class, Boolean[]::new, IMemberValuePair.K_BOOLEAN);
        public static final MemberType<String> STRING =
            new MemberType<>(String.class, String[]::new, IMemberValuePair.K_STRING);
        public static final MemberType<IAnnotation> ANNOTATION =
            new MemberType<>(IAnnotation.class, IAnnotation[]::new, IMemberValuePair.K_ANNOTATION);
        public static final MemberType<String> CLASS =
            new MemberType<>(String.class, String[]::new, IMemberValuePair.K_CLASS);
        public static final MemberType<String> QUALIFIED_NAME =
            new MemberType<>(String.class, String[]::new, IMemberValuePair.K_QUALIFIED_NAME);
        public static final MemberType<String> SIMPLE_NAME =
            new MemberType<>(String.class, String[]::new, IMemberValuePair.K_SIMPLE_NAME);
        public static final MemberType<Object> UNKNOWN =
            new MemberType<>(Object.class, Object[]::new, IMemberValuePair.K_UNKNOWN);

        public T get(IAnnotation annotation, String name) throws JavaModelException
        {
            IMemberValuePair member = member(annotation, name);
            return member != null ? tClass.cast(member.getValue()) : null;
        }

        public T[] getArray(IAnnotation annotation, String name) throws JavaModelException
        {
            IMemberValuePair member = member(annotation, name);
            if (member != null)
            {
                var source = member.getValue() instanceof Object[] values
                    ? Arrays.stream(values)
                    : Stream.of(member.getValue());
                return source.map(tClass::cast).toArray(arrayFactory);
            }
            return null;
        }
    }
}
