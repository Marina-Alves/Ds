package br.ufg.inf.billing.infra;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionFactory {

    Connection open() throws SQLException;

    static ConnectionFactory fromEnvironment() {
        return () -> DriverManager.getConnection(
                System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://db.billingco.internal:5432/billing"),
                System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
    }
}
