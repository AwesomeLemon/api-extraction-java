package com.github.awesomelemon;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.Type;

import java.beans.Expression;

public class ApiCall {
    private String Call;

    public final String getCall() {
        return Call;
    }

    private ApiCall(String call) {
        Call = call;
    }

    @Override
    public String toString() {
        return getCall();
    }

    public static ApiCall OfMethodInvocation(String className, String methodName) {
        if (className == null) className = "";
        if (className.equals("")) {
            return new ApiCall(methodName);
        }
        return new ApiCall(className + "." + methodName);
    }

    public static <N extends Node, T extends Type> ApiCall  OfMethodInvocation(NodeWithType<N,T> nodeWithType, String methodName) {
        String typeName = nodeWithType.getType().toString();
        if (typeName.equals("")) {
            return new ApiCall(methodName);
        }
        return new ApiCall(typeName + "." + methodName);
    }

    public static ApiCall OfConstructor(String className) {
        return new ApiCall(className + ".new");
    }

    public static <N extends Node, T extends Type> ApiCall  OfConstructor(NodeWithType<N,T> nodeWithType) {
        return new ApiCall(Util.getShortTypeName(nodeWithType.getType()) + ".new");
    }
}