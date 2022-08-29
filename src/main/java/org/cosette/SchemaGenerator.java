package org.cosette;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.avatica.util.Quoting;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalcitePrepare;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeImpl;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.*;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.ddl.*;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.parser.ddl.SqlDdlParserImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A SchemaGenerator instance can execute DDL statements and generate schemas in the process.
 */
public class SchemaGenerator {

    private static final Map<String, Class<?>> toPrimitive = ImmutableMap.<String, Class<?>>builder()
            .put("BINARY", String.class)
            .put("CHAR", String.class)
            .put("VARBINARY", String.class)
            .put("VARCHAR", String.class)
            .put("BLOB", String.class)
            .put("TINYBLOB", String.class)
            .put("MEDIUMBLOB", String.class)
            .put("LONGBLOB", String.class)
            .put("TEXT", String.class)
            .put("TINYTEXT", String.class)
            .put("MEDIUMTEXT", String.class)
            .put("LONGTEXT", String.class)
            .put("ENUM", String.class)
            .put("SET", String.class)
            .put("BOOL", boolean.class)
            .put("BOOLEAN", boolean.class)
            .put("DEC", double.class)
            .put("DECIMAL", double.class)
            .put("DOUBLE", double.class)
            .put("DOUBLE PRECISION", double.class)
            .put("FLOAT", float.class)
            .put("DATE", int.class)
            .put("DATETIME", int.class)
            .put("TIMESTAMP", int.class)
            .put("TIME", int.class)
            .put("YEAR", int.class)
            .put("INT", int.class)
            .put("TINYINT", int.class)
            .put("SMALLINT", int.class)
            .put("MEDIUMINT", int.class)
            .put("BIGINT", int.class)
            .put("INTEGER", int.class)
            .build();
    private static final Pattern functionPattern = Pattern.compile("(?i)DECLARE\\s+(?<type>SCALAR|AGGREGATE)\\s+FUNCTION\\s+(?<identifier>\\w+)\\s*\\((?<source>.*)\\)\\s+RETURNS\\s+(?<target>.+)");
    private static final SqlParser.Config schemaParserConfig = SqlParser.Config.DEFAULT
            .withParserFactory(SqlDdlParserImpl.FACTORY)
            .withLex(Lex.MYSQL)
            .withQuoting(Quoting.DOUBLE_QUOTE);
    private final CosetteSchema schema;
    private final Map<String, Function> declaredFunctions = new HashMap<>();

    /**
     * Create a SchemaGenerator instance by setting up a connection to JDBC.
     */
    public SchemaGenerator() {
        schema = new CosetteSchema(this);
    }

    /**
     * Execute a CREATE statement.
     *
     * @param create The given CREATE statement.
     */
    public void applyCreate(String create) {
        Pattern supported = Pattern.compile("(?i)CREATE\\s+(VIEW|TABLE)");
        if (!supported.matcher(create).find()) {
            // TODO: Improve error handling
            return;
        }
        SqlParser schemaParser = SqlParser.create(create, schemaParserConfig);
        SqlNode schemaNode;
        try {
            schemaNode = schemaParser.parseStmt();
        } catch (Exception ignored) {
            return;
        }
        switch (schemaNode) {
            case SqlCreateTable sqlCreateTable -> schema.addTable(sqlCreateTable);
            case SqlCreateView sqlCreateView -> {
                try {
                    schema.addView(sqlCreateView, create);
                } catch (Exception ignored) {}
            }
            default -> throw new RuntimeException("Unsupported create statement:\n" + create);
        }
    }

    /**
     * Execute a DECLARE FUNCTION statement.
     *
     * @param declareFunction The given DECLARE FUNCTION statement.
     */
    public void applyDeclareFunction(String declareFunction) throws Exception {
        Matcher matcher = functionPattern.matcher(declareFunction);
        if (!matcher.find()) {
            throw new RuntimeException("Broken function declaration:\n" + declareFunction);
        }
        String identifier = matcher.group("identifier");
        String[] source = matcher.group("source").split(",");
        String target = matcher.group("target").split("\\(")[0].trim().toUpperCase();
        Class<?>[] parameters = new Class[source.length];
        if (!toPrimitive.containsKey(target)) {
            throw new RuntimeException("Invalid return type: " + target);
        }
        for (int i = 0; i < source.length; i += 1) {
            String arg = source[i].split("\\(")[0].trim().toUpperCase();
            if (!toPrimitive.containsKey(arg)) {
                throw new RuntimeException("Invalid argument type: " + arg);
            }
            parameters[i] = toPrimitive.get(arg);
        }
        Function customFunction;
        Constructor<Method> methodConstructor = Method.class.getDeclaredConstructor(Class.class, String.class, Class[].class, Class.class, Class[].class, int.class, int.class, String.class, byte[].class, byte[].class, byte[].class);
        methodConstructor.setAccessible(true);
        if (matcher.group("type").equalsIgnoreCase("SCALAR")) {
            Method scalarFunction = methodConstructor.newInstance(SchemaGenerator.class, "cosetteFunction", parameters, toPrimitive.get(target), null, 0, 0, "", null, null, null);
            customFunction = ScalarFunctionImpl.createUnsafe(scalarFunction);
        } else {
            ReflectiveFunctionBase.ParameterListBuilder sourceParameters =
                    ReflectiveFunctionBase.builder();
            ImmutableList.Builder<Class<?>> sourceTypes = ImmutableList.builder();
            for (Class<?> clazz : parameters) {
                sourceParameters.add(clazz, clazz.getName(), false);
                sourceTypes.add(clazz);
            }
            Method nullFunction = methodConstructor.newInstance(SchemaGenerator.class, "cosetteFunction", parameters, toPrimitive.get(target), null, 0, 0, "", null, null, null);
            Constructor<AggregateFunctionImpl> aggregateFunctionConstructor = AggregateFunctionImpl.class.getDeclaredConstructor(Class.class, List.class, List.class, Class.class, Class.class, Method.class, Method.class, Method.class, Method.class);
            aggregateFunctionConstructor.setAccessible(true);
            customFunction = aggregateFunctionConstructor.newInstance(SchemaGenerator.class, sourceParameters.build(), sourceTypes.build(), toPrimitive.get(target), toPrimitive.get(target), nullFunction, nullFunction, null, null);
        }
        declaredFunctions.put(identifier, customFunction);
    }

    /**
     * @return The current schema.
     */
    public SchemaPlus extractSchema() {
        return schema.plus();
    }

    public CosetteSchema getRawSchema() {
        return schema;
    }

    /**
     * @return The declared custom functions.
     */
    public Map<String, Function> customFunctions() {
        return declaredFunctions;
    }

}

class CosetteTable extends AbstractTable {

    final CosetteSchema owner;
    final MutableList<Boolean> columnNullabilities = MutableList.create();
    final MutableList<String> columnNames = MutableList.create();
    final MutableList<SqlTypeName> columnTypeNames = MutableList.create();
    final MutableList<SqlBasicCall> checkConstraints = MutableList.create();
    final MutableSet<ImmutableBitSet> columnKeys = MutableSet.create();
    final SqlIdentifier id;

    public CosetteTable(CosetteSchema schema, SqlIdentifier name) {
        owner = schema;
        id = name;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        List<RelDataType> fields = new ArrayList<>();
        for (int index = 0; index < columnNames.size(); index += 1) {
            fields.add(typeFactory.createTypeWithNullability(typeFactory.createSqlType(columnTypeNames.get(index)), columnNullabilities.get(index)));
        }
        return typeFactory.createStructType(fields, columnNames.asJava());
    }

    @Override
    public Statistic getStatistic() {
        return Statistics.of(0, new ArrayList<>(columnKeys.asJava()));
    }

    public List<RexNode> deriveCheckConstraint() {
        List<RexNode> derivedConstraints = new ArrayList<>();
        RawPlanner planner = new RawPlanner(owner.plus());
        for (SqlBasicCall check : checkConstraints) {
            SqlSelect wrapper = new SqlSelect(SqlParserPos.ZERO, SqlNodeList.EMPTY, SqlNodeList.SINGLETON_STAR,
                    this.id, check, null, null, SqlNodeList.EMPTY, null, null, null, null);
            try {
                LogicalFilter filter = (LogicalFilter) planner.rel(planner.parse(wrapper.toString())).getInput(0);
                derivedConstraints.add(filter.getCondition());
            } catch (Exception ignore) {

            }
        }
        return derivedConstraints;
    }

}

class CosetteSchema extends AbstractSchema {

    final MutableMap<String, Table> tables = new MutableHashMap<>();
    final SchemaGenerator owner;

    public CosetteSchema(SchemaGenerator source) {
        owner = source;
    }

    public void addTable(SqlCreateTable createTable) {
        if (createTable.columnList == null) {
            throw new RuntimeException("No column in table " + createTable.name);
        }
        CosetteTable cosetteTable = new CosetteTable(this, createTable.name);

        for (SqlNode column : createTable.columnList) {
            switch (column.getKind()) {
                case CHECK -> cosetteTable.checkConstraints.append((SqlBasicCall) ((SqlCheckConstraint) column).getOperandList().get(1));
                case COLUMN_DECL -> {
                    SqlColumnDeclaration decl = (SqlColumnDeclaration) column;
                    cosetteTable.columnNames.append(decl.name.toString());
                    cosetteTable.columnTypeNames.append(SqlTypeName.get(decl.dataType.getTypeName().toString()));
                    cosetteTable.columnNullabilities.append(decl.strategy != ColumnStrategy.NOT_NULLABLE);
                }
                case FOREIGN_KEY -> System.err.println("Foreign key constraint is not implemented in cosette yet.");
                case PRIMARY_KEY, UNIQUE -> {
                    SqlKeyConstraint cons = (SqlKeyConstraint) column;
                    List<Integer> keys = new ArrayList<>();
                    for (SqlNode id : (SqlNodeList) cons.getOperandList().get(1)) {
                        int index = cosetteTable.columnNames.indexOf(id.toString());
                        keys.add(index);
                        if (column.getKind() == SqlKind.PRIMARY_KEY) {
                            cosetteTable.columnNullabilities.set(index, false);
                        }
                    }
                    cosetteTable.columnKeys.add(ImmutableBitSet.of(keys));
                }
                default -> throw new RuntimeException("Unsupported declaration type " + column.getKind() + " in table " + createTable.name);
            }
        }
        tables.put(createTable.name.toString(), cosetteTable);
    }

    public void addView(SqlCreateView sqlCreateView, String rawDef) throws SQLException {
        if (sqlCreateView.columnList == null || sqlCreateView.columnList.getList().isEmpty()) {
            throw new RuntimeException("No field definition in view " + sqlCreateView.name);
        }
        // Some regex hackery to extract the raw definition...
        var matcher = Pattern.compile("(?s).*?\\(.*?\\)\\s+[Aa][Ss](.*)").matcher(rawDef);
        if (!matcher.find()) {
            throw new RuntimeException("Cannot extract definition of view " + sqlCreateView.name);
        }
        var rawQuery = matcher.group(1);
        String fields = sqlCreateView.columnList
                .getList()
                .stream()
                .filter(Objects::nonNull)
                .map(SqlNode::toString)
                .collect(Collectors.joining("\", \""));
        String wrapper = "SELECT * FROM (%s) AS \"_\" (\"%s\")".formatted(rawQuery, fields);
        Properties info = new Properties();
        info.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "FALSE");
        CalciteConnection connection = DriverManager.getConnection("jdbc:calcite:", info)
                .unwrap(CalciteConnection.class);
        CalciteSchema calciteSchema = CalciteSchema.from(plus());
        CalcitePrepare.AnalyzeViewResult parsed = Schemas.analyzeView(connection, calciteSchema, null, wrapper, null, false);
        JavaTypeFactory typeFactory = (JavaTypeFactory) parsed.typeFactory;
        Type elementType = typeFactory.getJavaClass(parsed.rowType);
        Table viewTable = new ViewTable(elementType, RelDataTypeImpl.proto(parsed.rowType), wrapper, calciteSchema.path(null), null);
        tables.put(sqlCreateView.name.toString(), viewTable);
    }

    protected Map<String, Table> getTableMap() {
        return tables.asJava();
    }

    public SchemaPlus plus() {
        SchemaPlus plus = CalciteSchema.createRootSchema(true, false, "Cosette", this).plus();
        for (String fn : owner.customFunctions().keySet()) {
            plus.add(fn, owner.customFunctions().get(fn));
        }
        return plus;
    }

}
