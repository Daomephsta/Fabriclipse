package daomephsta.fabriclipse.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MethodSpecTests
{
    private static Stream<Arguments> parseArgumentSource()
    {
        return Stream.of(
            // selects a method or field called func_1234_a, if there are multiple
            // members with the same signature, matches the first occurrence
            Arguments.arguments("func_1234_a", null, "func_1234_a", null, null, Quantifier.parse(null)),
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches all occurrences
            Arguments.arguments("func_1234_a*", null, "func_1234_a", null, null, Quantifier.parse("*")),
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches all occurrences, matching less than 1
            // occurrence is an error condition
            Arguments.arguments("func_1234_a+", null, "func_1234_a", null, null, Quantifier.parse("+")),
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches up to 3 occurrences
            Arguments.arguments("func_1234_a{,3}", null, "func_1234_a", null, null, Quantifier.parse("{,3}")),
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches exactly 3 occurrences, matching fewer
            // than 3 occurrences is an error condition
            Arguments.arguments("func_1234_a{3}", null, "func_1234_a", null, null, Quantifier.parse("{3}")),
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches at least 3 occurrences, matching fewer
            // than 3 occurrences is an error condition
            Arguments.arguments("func_1234_a{3,}", null, "func_1234_a", null, null, Quantifier.parse("{3,}")),
            // selects all members of any type and descriptor
            Arguments.arguments("*", null, null, null, null, Quantifier.parse("*")),
            // selects the first member of any type and descriptor
            Arguments.arguments("{1}", null, null, null, null, Quantifier.parse("{1}")),
            // selects all methods which take 3 ints and return a bool
            Arguments.arguments("*(III)Z", null, null, new String[] {"I", "I", "I"}, "Z", Quantifier.parse("*")),
            // selects the first 2 methods which take a bool and return void
            Arguments.arguments("{2}(Z)V", null, null, new String[] {"Z"}, "V", Quantifier.parse("{2}")),
            // selects a method called func_1234_a which takes 3 ints and returns a bool
            Arguments.arguments("func_1234_a(III)Z", null, "func_1234_a", new String[] {"I", "I", "I"}, "Z", Quantifier.parse(null)),
         // selects a method called func_1234_a which takes no arguments
            Arguments.arguments("func_1234_a()Z", null, "func_1234_a", new String[0], "Z", Quantifier.parse(null)),
            // selects a ctor which takes a single String argument
            Arguments.arguments("<init>(Ljava/lang/String;)V", null, "<init>", new String[] {"Ljava/lang/String;"}, "V", Quantifier.parse(null)),
            // selects a method called func_1234_a in class foo.bar.Baz
            Arguments.arguments("Lfoo/bar/Baz;func_1234_a", "Lfoo/bar/Baz;", "func_1234_a", null, null, Quantifier.parse(null)),
            // selects a method called func_1234_a in class foo.bar.Baz which
            // takes three doubles and returns void
            Arguments.arguments("Lfoo/bar/Baz;func_1234_a(DDD)V", "Lfoo/bar/Baz;", "func_1234_a", new String[] {"D", "D", "D"}, "V", Quantifier.parse(null)),
            // alternate syntax for the same
            Arguments.arguments("foo.bar.Baz.func_1234_a(DDD)V", "foo.bar.Baz", "func_1234_a", new String[] {"D", "D", "D"}, "V", Quantifier.parse(null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parseArgumentSource")
    public void parse(
        String specification, String owner, String name, String[] parameterTypes, String returnType, Quantifier quantifier)
    {
        var result = MethodSpec.parse(specification);
        assertEquals(owner, result.owner);
        assertEquals(name, result.name);
        assertArrayEquals(parameterTypes, result.parameterTypes);
        assertEquals(returnType, result.returnType);
        assertEquals(quantifier, result.quantifier);
    }

    public static void main(String[] args)
    {
        String[] specs = {
            "func_1234_a",
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches all occurrences
            "func_1234_a*",
            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches all occurrences, matching less than 1
            // occurrence is an error condition
            "func_1234_a+",

            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches up to 3 occurrences
            "func_1234_a{,3}",

            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches exactly 3 occurrences, matching fewer
            // than 3 occurrences is an error condition
            "func_1234_a{3}",

            // selects a method or field called func_1234_a, if there are multiple
            // members with matching name, matches at least 3 occurrences, matching fewer
            // than 3 occurrences is an error condition
            "func_1234_a{3,}",
            // selects all members of any type and descriptor
            "*",
            // selects the first member of any type and descriptor
            "{1}",
            // selects all methods which take 3 ints and return a bool
            "*(III)Z",
            // selects the first 2 methods which take a bool and return void
            "{2}(Z)V",
            // selects a method called func_1234_a which takes 3 ints and returns a bool
            "func_1234_a(III)Z",
            // selects a ctor which takes a single String argument
            "<init>(Ljava/lang/String;)V",
            // selects a method called func_1234_a in class foo.bar.Baz
            "Lfoo/bar/Baz;func_1234_a",
            // selects a method called func_1234_a in class foo.bar.Baz which
            // takes three doubles and returns void
            "Lfoo/bar/Baz;func_1234_a(DDD)V",
            // alternate syntax for the same
            "foo.bar.Baz.func_1234_a(DDD)V"
        };
        for (String spec : specs)
            System.out.println(spec + " -> " + MethodSpec.parse(spec));
    }
}
