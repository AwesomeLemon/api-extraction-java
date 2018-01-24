package com.github.awesomelemon;

import java.sql.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultWriter {
    private Connection connection;

    public ResultWriter(Connection connection) {
        this.connection = connection;
    }

    public void write(List<Method> methods, int repoId) {
        markSolutionProcessed(repoId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                // create a database connection
                connection.setAutoCommit(false);
                PreparedStatement preparedStatement = connection.prepareStatement("insert into Method(name, comment, calls, repo_id) values(?, ?, ?, " + repoId + ")");
                preparedStatement.setQueryTimeout(30);  // set timeout to 30 sec.
                for (Method method : methods) {
                    preparedStatement.setString(1, method.getName());
                    preparedStatement.setString(2, method.getJavadocComment());
                    preparedStatement.setString(3, method.getCallSequence());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                // if the error message is "out of memory",
                // it probably means no database file is found
                System.out.println(e.getMessage());
            }
        });
    }

    public void markSolutionProcessed(int repoId) {
            try {
                Statement statement = connection.createStatement();
                statement.executeUpdate("update Solution set ProcessedTime=CURRENT_TIMESTAMP where id = " + repoId);
            } catch (SQLException e) {
                // if the error message is "out of memory",
                // it probably means no database file is found
                System.out.println(e.getMessage());
            }
    }

    public void createMethodTable() {
        try {
            // create a database connection
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.

            statement.executeUpdate("create table Method (id integer primary key, name varchar, comment varchar, calls varchar, repo_id integer)");
        }
        catch(SQLException e)
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            System.err.println(e.getMessage());
        }
    }
}
