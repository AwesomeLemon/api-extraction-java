package com.github.awesomelemon;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.types.ResolvedType;

public class Util {
    private Util() {
    }

    static String getTypeName(MethodUsage methodUsage) {
        return methodUsage.returnType().describe();
    }

    static String getTypeName(ResolvedType resolvedType) {
        return resolvedType.describe();
    }

    static <T extends Type> String getTypeName(T type) {
        return type.asString();
    }
}
