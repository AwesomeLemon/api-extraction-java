package com.github.awesomelemon;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import javafx.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException, SQLException, InterruptedException {
        String javaParserTestMain = "D:\\Users\\Alexander\\Documents\\JavaparserTest\\src\\Main.java";
        String srcPath = "D:\\Users\\Alexander\\Documents\\JavaparserTest\\src";
        String retrofit = "D:\\DeepJavaReps\\retrofit";
        String retrofitDeeper = "D:\\DeepJavaReps\\retrofit\\retrofit\\src";
        String retrofitWayDeeper = "D:\\DeepJavaReps\\retrofit\\retrofit\\src\\main\\java";
//        String databasePath = "D:\\YandexDisk\\DeepApiJava.sqlite";
        String databasePath = "/media/jet/HDD/DeepApiJava.sqlite";
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        connection.setAutoCommit(false);
//        Thread.sleep(10000);
//        System.out.println("slept 1");
        RepoPathProvider repoPathProvider = new RepoPathProvider(connection);
        ResultWriter resultWriter = new ResultWriter(connection);
//        Thread.sleep(10000);
//        System.out.println("slept 2");
        while (true) {
            Pair<String, Integer> repo = repoPathProvider.getNext();
            while (repo != null) {
                List<Method> methods = ProcessRepo(repo.getKey());
//            Thread.sleep(10000);
//            System.out.println("slept 3");
                resultWriter.write(methods, repo.getValue());
                deleteDir(new File(repo.getKey()));
                repo = repoPathProvider.getNext();
            }
            System.out.println("Sleeping...");
            Thread.sleep(100000);
        }

    }

    private static List<Method> ProcessRepo(String repoPath) throws IOException {
        List<File> javaFiles1 = findJavaFiles(repoPath);
        List<Method> repoMethods = new ArrayList<>();
//        int methodCount = 0;
        for (File javaFile : javaFiles1) {
            if (javaFile.isDirectory()) continue;//yep, there're dirs ending in '.java' E.g. in Wala
//            javaFile = new File("D:\\DeepApiReps\\sciruela_android\\src\\org\\apache\\bcel\\generic\\FieldGen.java");
            File rootDir = findProperRootDir(javaFile);
            CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
            combinedTypeSolver.add(new ReflectionTypeSolver());
            combinedTypeSolver.add(new JavaParserTypeSolver(rootDir));

            ParserConfiguration parserConfiguration = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));
            JavaParser parser = new JavaParser(parserConfiguration);
            Optional<CompilationUnit> maybeCu = parser.parse(
                    ParseStart.COMPILATION_UNIT,
                    new StreamProvider(new FileInputStream(javaFile))
            ).getResult();
            if (!maybeCu.isPresent()) continue;
            CompilationUnit cu = maybeCu.get();

            ArrayList<MethodDeclaration> methods = new ArrayList<>();
            MethodCollector methodCollector = new MethodCollector();
            methodCollector.visit(cu, methods);

            ApiSequenceExtractor apiSequenceExtractor = new ApiSequenceExtractor(combinedTypeSolver);
//            if (methods.size() == 0) continue;
            System.out.println(javaFile);

            for (MethodDeclaration method : methods) {
                ArrayList<ApiCall> apiCalls = new ArrayList<>();
                apiSequenceExtractor.visit(method, apiCalls);
                System.out.println(apiCalls);
                if (apiCalls.size() == 0) continue;
//                JavaParserFacade.get(combinedTypeSolver).get
                repoMethods.add(new Method(method.getJavadocComment().get().getContent(), ApiCall.createStringSequence(apiCalls), getFullMethodName(method)));
            }

//            methodCount += methods.size();
        }
        return repoMethods;
    }

    private static String getFullMethodName(MethodDeclaration method) {
        String methodName = method.getNameAsString();
        Optional<Node> parentNode = method.getParentNode();
        if (parentNode.isPresent()) {
            String parentName = "";
            if (parentNode.get() instanceof ClassOrInterfaceDeclaration) {
                parentName = ((ClassOrInterfaceDeclaration) parentNode.get()).getNameAsString();
            }
            if (parentNode.get() instanceof EnumDeclaration) {
                parentName = ((EnumDeclaration) parentNode.get()).getNameAsString();
            }
            methodName = parentName + "." + methodName;
        }
        return methodName;
    }

    private static File findProperRootDir(File javaFile) {
        File folderName = javaFile.getParentFile();
        while (!folderName.getName().equals("src") && !folderName.getName().equals("java")) {
            folderName = folderName.getParentFile();
            if (folderName == null) break;
        }
        return folderName == null ? javaFile.getParentFile() : folderName;
    }

    static List<String> findJavaFilePaths(String path) {
        File dir = new File(path);
        List<String> javaFiles = new ArrayList<>();
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                javaFiles.addAll(findJavaFilePaths(file.getAbsolutePath()));
            }
            if (file.getName().endsWith(".java")) {
                javaFiles.add(file.getAbsolutePath());
            }
        }
        return javaFiles;
    }

    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    static List<File> findJavaFiles(String path) {
        File dir = new File(path);
        List<File> javaFiles = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                javaFiles.addAll(findJavaFiles(file.getAbsolutePath()));
            }
            if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
        return javaFiles;
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
