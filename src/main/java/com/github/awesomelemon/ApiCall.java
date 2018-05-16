package com.github.awesomelemon;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;

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

    public static ApiCall ofMethodInvocation(String className, String methodName) {
        if (className == null) className = "";
        if (className.equals("")) {
            return new ApiCall(methodName);
        }
        return new ApiCall(className + "." + methodName);
    }

    public static <N extends Node, T extends Type> ApiCall ofMethodInvocation(NodeWithType<N,T> nodeWithType, String methodName) {
        String typeName = nodeWithType.getType().toString();
        if (typeName.equals("")) {
            return new ApiCall(methodName);
        }
        return new ApiCall(typeName + "." + methodName);
    }

    public static ApiCall ofConstructor(String className) {
        return new ApiCall(className + ".new");
    }

    public static <N extends Node, T extends Type> ApiCall ofConstructor(NodeWithType<N,T> nodeWithType) {
        return new ApiCall(Util.getTypeName(nodeWithType.getType()) + ".new");
    }

    public static ApiCall ofConstructor(ResolvedType type) {
        return new ApiCall(Util.getTypeName(type) + ".new");
    }

    public static String createStringSequence(List<ApiCall> calls) {
        StringBuilder sb = new StringBuilder();
        for (ApiCall call : calls) {
            sb.append(call.toString());
            sb.append(' ');
        }
        sb.delete(sb.length() - 1, sb.length());
        return sb.toString();
    }


}