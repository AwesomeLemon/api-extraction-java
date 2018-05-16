package com.github.awesomelemon.database;

import javafx.util.Pair;

import java.sql.*;
import java.util.List;

public class RepoPathProvider {
    private Connection connection;

    public RepoPathProvider(Connection connection) {
        this.connection = connection;
    }

    public Pair<String, Integer> getNext() {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);  // set timeout to 30 sec.

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, path FROM Solution WHERE ProcessedTime ISNULL LIMIT 1")) {
                if (!resultSet.isBeforeFirst()) {
                    resultSet.close();
                    return null;//result set is empty
                }
                resultSet.next();
                int id = resultSet.getInt("id");
                String path = resultSet.getString("path");
                return new Pair<>(path, id);
            }
        }
        catch(SQLException e)
        {
            e.printStackTrace();
        }
        try {
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
