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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException, SQLException, InterruptedException {
//        storeEdingburghJavaDataset();
//        String javaParserTestMain = "D:\\Users\\Alexander\\Documents\\JavaparserTest\\src\\Main.java";
        String databasePath = "D:\\YandexDisk\\DeepApiJava.sqlite";
//        String databasePath = "/media/jet/HDD/DeepApiJava.sqlite";
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        connection.setAutoCommit(false);
        RepoPathProvider repoPathProvider = new RepoPathProvider(connection);
        ResultWriter resultWriter = new ResultWriter(connection);
        while (true) {
            Pair<String, Integer> repo = repoPathProvider.getNext();
            while (repo != null) {
                resultWriter.markSolutionProcessed(repo.getValue());
                List<Method> methods = ProcessRepo(repo.getKey());
                resultWriter.write(methods, repo.getValue());
                deleteDir(new File(repo.getKey()));
                repo = repoPathProvider.getNext();
            }
            System.out.println("Sleeping...");
            Thread.sleep(100000);
        }

    }

    private static void storeEdingburghJavaDataset() throws SQLException {
        final String datasetPath="D:\\Users\\Alexander\\Downloads\\java_projects\\java_projects";
        List<String> dirPaths = Arrays.stream(new File(datasetPath).listFiles()).map(dirFile -> dirFile.getAbsolutePath()).collect(Collectors.toList());

        String databasePath = "D:\\YandexDisk\\DeepApiJava.sqlite";
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        connection.setAutoCommit(false);
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "insert into Solution(Path) values (?)");
            preparedStatement.setQueryTimeout(30);
            for (String dirPath : dirPaths) {
                preparedStatement.setString(1, dirPath);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            preparedStatement.close();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static List<Method> ProcessRepo(String repoPath) throws IOException {
        List<File> javaFiles = findJavaFiles(repoPath);
        List<Method> repoMethods = new ArrayList<>();
        for (File javaFile : javaFiles) {
            if (javaFile.isDirectory()) continue;//yep, there're dirs ending in '.java' E.g. in Wala
//            if (javaFile.getName().equals(
//                    "Editor.java")
//                    || javaFile.getAbsolutePath().startsWith("/media/jet/HDD/DeepApiJava/SlimRoms/frameworks_base/core/java/android/widget/")
//                    || javaFile.getAbsolutePath().startsWith("/media/jet/HDD/DeepApiJava/SlimRoms/frameworks_base/core/java/com/android/internal/view")
//                    || javaFile.getAbsolutePath().startsWith("/media/jet/HDD/DeepApiJava/SlimRoms/frameworks_base/core/java/com/android/internal/widget")
//                    || javaFile.getName().equals("PackageInstaller.java")
//                    || javaFile.getName().equals("FastScroller.java")
//                    || javaFile.getName().equals("LauncherApps.java")) {
//                continue;//stack overflow in java parser. 1gb stack is not enough
//            }
//            if (javaFile.getAbsolutePath().startsWith("/media/jet/HDD/DeepApiJava/SlimRoms/frameworks_base/media/java/android/media/AudioManager.java")) {
//                break;
//            }
            File rootDir = findProperRootDir(javaFile);
            CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
            combinedTypeSolver.add(new ReflectionTypeSolver());
            combinedTypeSolver.add(new JavaParserTypeSolver(rootDir));

            ParserConfiguration parserConfiguration = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver))
                    .setLexicalPreservationEnabled(false);
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
                try {
                    apiSequenceExtractor.visit(method, apiCalls);
                }
                catch (StackOverflowError | OutOfMemoryError e) {// I'm in no mood to debug Javaparser
                    System.gc();
                    System.out.println("Stack overflowed. Or memory. Deal with it.");
                }
//                System.out.println(apiCalls);
                if (apiCalls.size() == 0) continue;
                repoMethods.add(new Method(
                        method.getJavadocComment().get().getContent(),
                        ApiCall.createStringSequence(apiCalls),
                        getFullMethodName(method)));
            }
            JavaParserFacade.clearInstances();
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

    private static List<String> findJavaFilePaths(String path) {
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

    private static List<File> findJavaFiles(String path) {
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
