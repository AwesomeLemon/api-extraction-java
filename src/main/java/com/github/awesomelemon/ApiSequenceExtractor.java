package com.github.awesomelemon;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import static com.github.awesomelemon.Util.getShortTypeName;

public class ApiSequenceExtractor extends VoidVisitorAdapter<List<ApiCall>> {
    private TypeSolver typeSolver;
    private String lastReturnType;

    public ApiSequenceExtractor(TypeSolver typeSolver) {
        super();
        this.typeSolver = typeSolver;
    }

    private <N extends Node, T extends Type> void updateLastReturnType(NodeWithType<N, T> nodeWithType) {
        lastReturnType = nodeWithType.getType().toString();
    }

    private void updateLastReturnType(String typeString) {
        lastReturnType = typeString;
    }

    @Override
    public void visit(ObjectCreationExpr oc, List<ApiCall> calls) {
        NodeList<Expression> arguments = oc.getArguments();
        if (arguments.size() > 0) {
            for (Expression argument : arguments) {
                argument.accept(this, calls);
            }
        }
        calls.add(ApiCall.OfConstructor(oc));
        updateLastReturnType(oc);
    }

    @Override
    public void visit(TryStmt tryStmt, List<ApiCall> calls) {
        Stack<ApiCall> resourcesToClose = new Stack<>();
        tryStmt.getResources().forEach(resource -> {
            resource.ifVariableDeclarationExpr(variableDeclarationExpr -> {
                variableDeclarationExpr.getVariables().forEach(v -> {
                    resourcesToClose.push(ApiCall.OfMethodInvocation(v, "close"));
                });
            });
            resource.accept(this, calls);
        });
        tryStmt.getTryBlock().accept(this, calls);
        while (!resourcesToClose.empty()) {
            calls.add(resourcesToClose.pop());
        }
    }

    @Override
    public void visit(StringLiteralExpr n, List<ApiCall> calls) {
        updateLastReturnType("String");
    }

    static final HashSet<String> streamApiFuns = new HashSet<>(List.of("anyMatch", "collect", "count", "distinct",
            "filter", "findAny", "findFirst", "flatMap", "flatMapToDouble", "flatMapToInt",
            "flatMapToLong", "forEach", "forEachOrdered", "limit", "map", "mapToDouble", "mapToInt",
            "mapToLong", "max", "min", "noneMatch", "peek", "reduce", "skip", "sorted"));

    @Override
    public void visit(MethodCallExpr methodCallExpr, List<ApiCall> calls) {
        //stream API calls should be processed differently
        //in normal calls the execution order is scope-arguments-function
        //however, if the function is a stream function, the execution goes scope-function-arguments
        //arguably, for me Stream functions are irrelevant, 'cause they are separate from the functionality-related API calls
        if (streamApiFuns.contains(methodCallExpr.getNameAsString())) {
            Optional<Expression> scope = methodCallExpr.getScope();
            if (scope.isPresent()) {
                Expression scopeExpr = scope.get();
                scopeExpr.accept(this, calls);//suppose that the type of this was written to lastReturnType
                calls.add(ApiCall.OfMethodInvocation(lastReturnType, methodCallExpr.getNameAsString()));
                methodCallExpr.getArguments().forEach(arg -> arg.accept(this, calls));

                MethodUsage methodUsage = JavaParserFacade.get(typeSolver).solveMethodAsUsage(methodCallExpr);
                String shortType = getShortTypeName(methodUsage);
                updateLastReturnType(shortType);
            } else {
                //we're calling a local function. Recording its name is probably useless, so let's visit it instead
                //on the other hand it seems complicated, so I'll postpone working on it for now
            }
        }
        else {
            Optional<Expression> scope = methodCallExpr.getScope();
            if (scope.isPresent()) {
                Expression scopeExpr = scope.get();
                scopeExpr.accept(this, calls);//suppose that the type of this was written to lastReturnType
//            SymbolReference<? extends ResolvedValueDeclaration> scopeSymbol = JavaParserFacade.get(typeSolver).solve(scopeExpr);
//            String s = scopeSymbol.toString();
                String scopeType = lastReturnType;//it may be changed in the next call
                methodCallExpr.getArguments().forEach(arg -> arg.accept(this, calls));
                calls.add(ApiCall.OfMethodInvocation(scopeType, methodCallExpr.getNameAsString()));

                MethodUsage methodUsage = JavaParserFacade.get(typeSolver).solveMethodAsUsage(methodCallExpr);
                String shortType = getShortTypeName(methodUsage);
//            System.out.println(shortType);
                updateLastReturnType(shortType);
            } else {
                //we're calling a local function. Recording its name is probably useless, so let's visit it instead
                //on the other hand it seems complicated, so I'll postpone working on it for now
            }
        }
    }

    @Override
    public void visit(CastExpr n, List<ApiCall> calls) {
        n.getExpression().accept(this, calls);
        updateLastReturnType(n);
    }

    @Override
    public void visit(DoubleLiteralExpr n, List<ApiCall> calls) {
        updateLastReturnType("Double");
    }

    @Override
    public void visit(IntegerLiteralExpr n, List<ApiCall> calls) {
        updateLastReturnType("Integer");
    }

    @Override
    public void visit(IfStmt n, List<ApiCall> calls) {
        n.getCondition().accept(this, calls);
        n.getThenStmt().accept(this, calls);
        n.getElseStmt().ifPresent((l) -> {
            l.accept(this, calls);
        });
    }

    @Override
    public void visit(NameExpr n, List<ApiCall> calls) {
        if (Character.isUpperCase(n.getNameAsString().charAt(0))) {
            //probably this is type, not a variable name.
            updateLastReturnType(n.getNameAsString());
            return;
        }
        ResolvedType type = JavaParserFacade.get(typeSolver).getType(n);
        String shortTypeName = getShortTypeName(type);
        System.out.println(shortTypeName);
        updateLastReturnType(shortTypeName);
    }

    @Override
    public void visit(Name n, List<ApiCall> calls) {
        updateLastReturnType(getShortTypeName(n.asString()));
    }

    //useless, for I do not go inside constructors, and 'super' can be only there
    //also, unfinished
    @Override
    public void visit(SuperExpr n, List<ApiCall> calls) {
        n.getClassExpr().ifPresent((l) -> {
            l.accept(this, calls);
        });
        ResolvedType type = JavaParserFacade.get(typeSolver).getType(n);
    }

    @Override
    public void visit(ThisExpr n, List<ApiCall> calls) {
        updateLastReturnType(JavaParserFacade.get(typeSolver).getType(n).describe());
    }

    //now useless, 'cause I get variable type from the left of the expression, and this allows me to get it from the right.
    //but I don't do it.
    @Override
    public void visit(VariableDeclarationExpr n, List<ApiCall> calls) {
        n.getVariables().forEach((p) -> {
            p.accept(this, calls);
        });
    }

    @Override
    public void visit(WhileStmt n, List<ApiCall> calls) {
        n.getCondition().accept(this, calls);
        n.getBody().accept(this, calls);
    }

    @Override
    public void visit(MethodReferenceExpr n, List<ApiCall> calls) {
        n.getScope().accept(this, calls);
        n.getTypeArguments().ifPresent((l) -> {
            l.forEach((v) -> {
                v.accept(this, calls);
            });
        });
//        System.out.println(n.getScope().toString());
        String type = JavaParserFacade.get(typeSolver).getType(n.getScope()).describe();
        calls.add(ApiCall.OfMethodInvocation(type, n.getIdentifier()));
        updateLastReturnType(type);
//        ApiCall.OfMethodInvocation()
    }

//    @Override
//    public void visit(ObjectCreationExpr n, List<ApiCall> calls) {
//        if (n.getAnonymousClassBody().isPresent()) return;
//        n.getScope().ifPresent((l) -> {
//            //I don't really know why anyone would write "new a().new b()", but whatever.
//            l.accept(this, calls);
//        });
//        n.getType().accept(this, calls);
//    }
}
