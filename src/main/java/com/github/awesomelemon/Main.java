package com.github.awesomelemon;

import com.github.awesomelemon.database.RepoPathProvider;
import com.github.awesomelemon.database.ResultWriter;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import javafx.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final String databasePath = "/media/jet/HDD/DeepApiJava (1).sqlite";

    public static void main(String[] args) throws SQLException, InterruptedException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        connection.setAutoCommit(false);
        RepoPathProvider repoPathProvider = new RepoPathProvider(connection);
        ResultWriter resultWriter = new ResultWriter(connection);
        int cnt = 0;
        //as repos will be downloading, repo path provider will give paths to them
        while (true) {
            Pair<String, Integer> repo = repoPathProvider.getNext();

            while (repo != null) {
                final String repoPath = repo.getKey();
                final Integer repoId = repo.getValue();
                resultWriter.markSolutionProcessed(repoId);

                //new thread every time because:
                //1) it does not affect performance much
                //2) due to bugs in JavaParser there're StackOverflowErrors which
                //     halt the collection process until I look at it and restart
                Thread thread = new Thread(() -> {
                    try {
                        List<Method> methods = ProcessRepoUsingSourceRoot(repoPath);
                        resultWriter.write(methods, repoId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                thread.start();
                thread.join();
                deleteDir(new File(repoPath));
                repo = repoPathProvider.getNext();
            }
            System.out.println("Sleeping...");
            Thread.sleep(100000);
        }

    }

    private static void mainMeasureSourceRootPerformance(String[] args) throws IOException, SQLException, InterruptedException {
        File dir = new File("/media/jet/HDD/DeepApiJava");
        List<String> files = Arrays.stream(dir.listFiles()).map(File::getAbsolutePath).collect(Collectors.toList());
        measureSourceRootPerformance(files);
    }

    private static void measureSourceRootPerformance(List<String> repos) {
        int seen = 0;
        int seenCp = seen;
        int diffsSum = 0;
        try {
            for (String repo : repos) {
                if (seenCp-- > 0) continue;
                long startTime = System.currentTimeMillis();
                List<Method> methods = ProcessRepo(repo);
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;

                long startTime2 = System.currentTimeMillis();
                methods = ProcessRepoUsingSourceRoot(repo);
                long stopTime2 = System.currentTimeMillis();
                long elapsedTime2 = stopTime2 - startTime2;

                long diff = elapsedTime - elapsedTime2;
                diffsSum += diff;

                seen++;
                if (seen % 10 == 0) {
                    System.out.println(diffsSum);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Method> ProcessRepoUsingSourceRoot(String repoPath) throws IOException {
        List<Method> repoMethods = new ArrayList<>();
        Path rootDir = Paths.get(repoPath);
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());

        File srcDir = new File(repoPath);
        if (!srcDir.exists()) return repoMethods;
        combinedTypeSolver.add(new JavaParserTypeSolver(srcDir));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver))
                .setLexicalPreservationEnabled(false);
//        JavaParser parser = new JavaParser(parserConfiguration);
        SourceRoot sourceRoot = new SourceRoot(rootDir, parserConfiguration);
        List<ParseResult<CompilationUnit>> parseResults = null;
        System.out.println(repoPath);
        try {
            parseResults = sourceRoot.tryToParse();
        } catch (NoSuchFileException e) {
            e.printStackTrace();
            System.out.println("Try to pass the job to ProcessRepo");
            return ProcessRepo(repoPath);//temporary solution because of wrong
            //handling of dirs with dots by JavaParser
        }
        if (parseResults == null) return repoMethods;
        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            Optional<CompilationUnit> maybeCu = parseResult.getResult();
            if (!maybeCu.isPresent()) {
                continue;
            }
            CompilationUnit cu = maybeCu.get();
            ArrayList<MethodDeclaration> methods = new ArrayList<>();
            MethodCollector methodCollector = new MethodCollector();
            methodCollector.visit(cu, methods);

            //create better type solver, using different dir
            CombinedTypeSolver combinedTypeSolver2 = new CombinedTypeSolver();
            combinedTypeSolver2.add(new ReflectionTypeSolver());
            File javaFile = new File(String.valueOf(cu.getStorage().get().getPath()));
            System.out.println(javaFile.getAbsolutePath());
            combinedTypeSolver2.add(new JavaParserTypeSolver(new File(repoPath)));
            combinedTypeSolver2.add(new JavaParserTypeSolver(findProperRootDir(javaFile)));

            ApiSequenceExtractor apiSequenceExtractor = new ApiSequenceExtractor(
                    combinedTypeSolver2
            );
//            if (methods.size() == 0) continue;
//            System.out.println(1);

            for (MethodDeclaration method : methods) {
                ArrayList<ApiCall> apiCalls = new ArrayList<>();
                apiSequenceExtractor.visit(method, apiCalls);
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

    private static List<Method> ProcessRepo(String repoPath) throws IOException {
        List<File> javaFiles = findJavaFiles(repoPath);
        List<Method> repoMethods = new ArrayList<>();
        System.out.println(repoPath);
        for (File javaFile : javaFiles) {
            if (javaFile.isDirectory()) continue;//yep, there're dirs ending in '.java' E.g. in Wala
            File rootDir = findProperRootDir(javaFile);
            CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
            combinedTypeSolver.add(new ReflectionTypeSolver());
            combinedTypeSolver.add(new JavaParserTypeSolver(rootDir));

            Optional<CompilationUnit> maybeCu;

            ParserConfiguration parserConfiguration = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver))
                    .setLexicalPreservationEnabled(false);

            JavaParser parser = new JavaParser(parserConfiguration);
            try {
                maybeCu = parser.parse(
                        ParseStart.COMPILATION_UNIT,
                        new StreamProvider(new FileInputStream(javaFile))
                ).getResult();
            } catch (FileNotFoundException e) {
                continue;
            }
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

    //for easier JavaParser bug tracking
    private static List<Method> ProcessFile(String filePath) throws IOException {
        File javaFile = new File(filePath);
        List<Method> repoMethods = new ArrayList<>();
        System.out.println(filePath);
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
        CompilationUnit cu = maybeCu.get();

        ArrayList<MethodDeclaration> methods = new ArrayList<>();
        MethodCollector methodCollector = new MethodCollector();
        methodCollector.visit(cu, methods);

        ApiSequenceExtractor apiSequenceExtractor = new ApiSequenceExtractor(combinedTypeSolver);
//            if (methods.size() == 0) continue;
        System.out.println(javaFile);

        for (MethodDeclaration method : methods) {
            ArrayList<ApiCall> apiCalls = new ArrayList<>();
            System.out.println(method.getName().asString());
            apiSequenceExtractor.visit(method, apiCalls);
            if (apiCalls.size() == 0) continue;
            repoMethods.add(new Method(
                    method.getJavadocComment().get().getContent(),
                    ApiCall.createStringSequence(apiCalls),
                    getFullMethodName(method)));
        }
        JavaParserFacade.clearInstances();
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

    private static void testFailOnFoldersWithDots() throws IOException {
        Path rootDir = Paths.get("/media/jet/HDD/DeepApiJava/erahal");
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/media/jet/HDD/DeepApiJava/erahal")));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver))
                .setLexicalPreservationEnabled(false);
//        JavaParser parser = new JavaParser(parserConfiguration);
        SourceRoot sourceRoot = new SourceRoot(rootDir, parserConfiguration);
        Files.walkFileTree(Paths.get("/media/jet/HDD/DeepApiJava/erahal"), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isDirectory() && file.toString().endsWith(".java") && file.toString().contains("etch")) {
                    Path root = Paths.get("/media/jet/HDD/DeepApiJava/erahal");
                    Path relative = root.relativize(file.getParent());
                    System.out.println(relative);
                    sourceRoot.tryToParse(relative.toString(), file.getFileName().toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }


    private static void storeEdingburghJavaDataset() throws SQLException {
        final String datasetPath = "D:\\Users\\Alexander\\Downloads\\java_projects\\java_projects";
        List<String> dirPaths = Arrays.stream(new File(datasetPath).listFiles())
                .map(dirFile -> dirFile.getAbsolutePath())
                .collect(Collectors.toList());

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        connection.setAutoCommit(false);
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO Solution(Path) VALUES (?)");
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
}
