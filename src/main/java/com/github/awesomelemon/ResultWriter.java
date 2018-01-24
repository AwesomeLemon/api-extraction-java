package com.github.awesomelemon;

import java.sql.*;
import java.util.List;

public class ResultWriter {
    private String databasePath;

    public ResultWriter(String databasePath) {
        this.databasePath = databasePath;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void write(List<Method> methods, int repoId) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" +databasePath)) {
            // create a database connection
            PreparedStatement statement = connection.prepareStatement("insert into Method(name, comment, calls, repo_id) values(?, ?, ?, "+repoId+")");
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            for (Method method : methods) {
                statement.setString(1, method.getName());
                statement.setString(2, method.getJavadocComment());
                statement.setString(3, method.getCallSequence());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        catch(SQLException e)
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            System.err.println(e.getMessage());
        }
    }

    public void createMethodTable() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" +databasePath)) {
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
