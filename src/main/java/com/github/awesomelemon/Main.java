package com.github.awesomelemon;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.beans.Expression;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String javaParserTestMain = "D:\\Users\\Alexander\\Documents\\JavaparserTest\\src\\Main.java";
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(new File("D:\\Users\\Alexander\\Documents\\JavaparserTest\\src")));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));
        JavaParser parser = new JavaParser(parserConfiguration);
        CompilationUnit cu = parser.parse(
                ParseStart.COMPILATION_UNIT,
                new StreamProvider(new FileInputStream(javaParserTestMain))
        ).getResult().get();

        ArrayList<MethodDeclaration> methods = new ArrayList<>();
        MethodCollector methodCollector = new MethodCollector();
        methodCollector.visit(cu, methods);

        ApiSequenceExtractor apiSequenceExtractor = new ApiSequenceExtractor(combinedTypeSolver);
        ArrayList<ApiCall> apiCalls = new ArrayList<>();

        for (MethodDeclaration method : methods) {
            apiSequenceExtractor.visit(method, apiCalls);
            System.out.println(apiCalls);
        }
    }

    public static void noSolverMain(String[] args) throws Exception {
        String javaParserTestMain = "D:\\Users\\Alexander\\Documents\\JavaparserTest\\src\\Main.java";
        CompilationUnit cu = JavaParser.parse(new FileInputStream(javaParserTestMain));

        ArrayList<MethodDeclaration> methods = new ArrayList<>();
        MethodCollector methodCollector = new MethodCollector();
        methodCollector.visit(cu, methods);

        ApiSequenceExtractor apiSequenceExtractor = new ApiSequenceExtractor(null);
        ArrayList<ApiCall> apiCalls = new ArrayList<>();

        for (MethodDeclaration method : methods) {
            apiSequenceExtractor.visit(method, apiCalls);
            System.out.println(apiCalls);
        }
    }

    public static void oldMain() throws Exception {
        String myFile = "D:\\DeepJavaReps\\retrofit\\samples\\src\\main\\java\\com\\example\\retrofit\\ChunkingConverter.java";
        CompilationUnit cu = JavaParser.parse(new FileInputStream(myFile));
//        TypeSolver typeSolver = new CombinedTypeSolver();
//        ParserConfiguration parserConfiguration =
//                new ParserConfiguration().setSymbolResolver(
//                        new JavaSymbolSolver(typeSolver));
//        JavaParser parser = new JavaParser(parserConfiguration);
//        CompilationUnit cu =
//                parser.parse(ParseStart.COMPILATION_UNIT,
//                        new StreamProvider(new FileInputStream(myFile)))
//                        .getResult().get();

        VoidVisitor<List<String>> methodNameVisitor = new MethodNamePrinter();
        ArrayList<String> methodNames = new ArrayList<>();
//        methodNameVisitor.visit(cu, methodNames);
//        System.out.println(methodNames);
        MethodCollector methodCollector = new MethodCollector();
        ArrayList<MethodDeclaration> methods = new ArrayList<>();
        methodCollector.visit(cu, methods);
        for (MethodDeclaration method : methods) {
            methodNameVisitor.visit(method, methodNames);
        }
        System.out.println(methodNames);

//        TypeSolver typeSolver = new CombinedTypeSolver();
//        ParserConfiguration parserConfiguration =
//                new ParserConfiguration().setSymbolResolver(
//                        new JavaSymbolSolver(typeSolver));
//        JavaParser parser = new JavaParser(parserConfiguration);
//        CompilationUnit compilationUnit =
//                parser.parse(ParseStart.COMPILATION_UNIT,
//                        new StreamProvider(new FileInputStream(myFile)))
//                        .getResult().get();
//        List<Node> childNodes = cu.getChildNodes();
//
//        Type type = JavaParserFacade.get(typeSolver).getType(childNodes.get(21).getChildNodes().get(5).getChildNodes().get(5).getChildNodes().get(0).getChildNodes().get(0).getChildNodes().get(0).getChildNodes().get(2));
//        System.out.println(childNodes);

    }
}
