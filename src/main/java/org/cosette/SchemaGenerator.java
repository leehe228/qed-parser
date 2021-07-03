package org.cosette;

import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.prepare.PlannerImpl;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.server.ServerDdlExecutor;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class SchemaGenerator {

    private final CalciteConnection calciteConnection;

    public SchemaGenerator() throws SQLException {
        Properties info = new Properties();
        info.setProperty(CalciteConnectionProperty.PARSER_FACTORY.camelName(), ServerDdlExecutor.class.getName() + "#PARSER_FACTORY");
        Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
        calciteConnection = connection.unwrap(CalciteConnection.class);
    }

    public void applyDDL(String ddl) throws SQLException {
        Statement statement = calciteConnection.createStatement();
        statement.execute(ddl);
        statement.close();
    }

    public SchemaPlus extractSchema() {
        return calciteConnection.getRootSchema();
    }

    public PlannerImpl createPlanner() {
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(extractSchema())
                .build();
        return new PlannerImpl(config);
    }

    public void close() throws SQLException {
        calciteConnection.close();
    }

}
