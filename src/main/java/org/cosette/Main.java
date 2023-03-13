package org.cosette;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * The Main logic of Cosette-Parser.
 */
public class Main {

    public static void main(String[] args) {
        for (String filename : args) {
            parseFile(filename);
        }
    }

    /**
     * Parse a file or a directory of files.
     *
     * @param path The input path.
     */

    public static void parseFile(String path) {
        String type = FilenameUtils.getExtension(path);
        if (type.equals("sql")) {
            parseSQLFile(path);
        } else if (type.equals("cos")) {
            parseCOSFile(path);
        } else {
            File object = new File(path);
            if (object.isDirectory()) {
                for (File file : Objects.requireNonNull(object.listFiles())) {
                    parseFile(file.getPath());
                }
            }
        }
    }

    /**
     * Parse a .sql file.
     *
     * @param filename The input filename.
     */

    private static void parseSQLFile(String filename) {
        try {
            Pattern comment = Pattern.compile("--.*(\\r?\\n|$)");
            Scanner scanner = new Scanner(new File(filename));
            SchemaGenerator generator = new SchemaGenerator();
            SQLJSONParser parser = new SQLJSONParser();
            scanner.useDelimiter(Pattern.compile(";"));
            while (scanner.hasNext()) {
                String statement = comment.matcher(scanner.next()).replaceAll("\n").trim();
                if (!statement.isBlank()) {
                    try {
                        if (statement.toUpperCase().startsWith("CREATE")) {
                            generator.applyCreate(statement);
                        } else if (statement.toUpperCase().startsWith("DECLARE")) {
                            generator.applyDeclareFunction(statement);
                        } else {
                            parser.parseDML(generator.extractSchema(), statement);
                        }
                    } catch (Exception e) {
                        throw new Exception("In statement:\n" + statement.replaceAll("(?m)^", "\t") + "\n" + e.getMessage());
                    }
                }
            }
            String outputPath = FilenameUtils.getFullPath(filename) + FilenameUtils.getBaseName(filename);
            parser.pruneWith(generator.getRawSchema());
            parser.dumpOuput(outputPath);
            scanner.close();
        } catch (Exception e) {
            if (e instanceof org.apache.calcite.sql.validate.SqlValidatorException) {
                String msg = e.getMessage();
                Pattern p1 = Pattern.compile("Expression '([\\w_.]+)' is not being grouped");
                Matcher m1 = p1.matcher(msg);
                Pattern p2 = Pattern.compile("Column '(\\w+)' not found in any table");
                Matcher m2 = p2.matcher(msg);

                // Pattern p3 = Pattern.compile("");
                String errorMessage = null;

                if (m1.matches()) {
                    errorMessage = "Used a GROUP BY without an aggregate";
                } else if (m2.matches) {
                    errorMessage = "Referenced an alias before creating it";
                }
            }
            // In this implementation, we wish to append to a file
            File error = new File(outputPath + ".error");
            FileWriter fr;
            if (error.exists()) {
                fr = new FileWriter(error, true);
            } else {
                fr = new FileWriter(error);
            }

            // } else if (e instanceof org.apache.calcite.runtime.CalciteContextException) {

            // }


            // System.err.println("In file:\n\t" + filename);
            // System.err.println(e.toString().trim() + "\n");

        }
    }

    /**
     * Assuming that the .cos file is always in the following format:<br>
     * schema schema_name(column:int, ...);<br>
     * table table_name(schema_name);<br>
     * query _ `query_body`; <br>
     * The .cos file will be translated to a .sql file, which contains the translated SQL statement.
     * Then the .sql file will be passed to parseSQLFile(...) and will not be deleted after it is used.
     *
     * @param filename The input .cos filename
     */
    private static void parseCOSFile(String filename) {
        try {
            Scanner scanner = new Scanner(new File(filename));
            Pattern schemaPattern = Pattern.compile("(?<=schema\\s)(\\w+)\\((.*)\\)$");
            Pattern tablePattern = Pattern.compile("(?<=table\\s)(\\w+)\\((\\w+)\\)$");
            Pattern declarationPattern = Pattern.compile("(\\w+):\\w+,?\\s?");
            Pattern queryPattern = Pattern.compile("(?<=`)[\\s\\S]*(?=`)");
            StringBuilder sqlBuilder = new StringBuilder();
            Map<String, String> schemas = new HashMap<>();
            scanner.useDelimiter(Pattern.compile(";"));
            while (scanner.hasNext()) {
                String line = scanner.next();
                Matcher schemaMatcher = schemaPattern.matcher(line);
                Matcher tableMatcher = tablePattern.matcher(line);
                Matcher queryMatcher = queryPattern.matcher(line);
                if (schemaMatcher.find()) {
                    StringBuilder schema = new StringBuilder();
                    Matcher declarationMatcher = declarationPattern.matcher(schemaMatcher.group(2));
                    schema.append(" (");
                    while (declarationMatcher.find()) {
                        schema.append("\n\t");
                        schema.append(declarationMatcher.group(1).toUpperCase(Locale.ROOT));
                        schema.append(" INTEGER,");
                    }
                    if (declarationMatcher.reset().find()) {
                        schema.deleteCharAt(schema.length() - 1);
                    } else {
                        schema.append("\n\tCOL INTEGER");
                    }
                    schema.append("\n);\n");
                    schemas.put(schemaMatcher.group(1).toUpperCase(Locale.ROOT), schema.toString());
                } else if (tableMatcher.find()) {
                    sqlBuilder.append("CREATE TABLE ");
                    sqlBuilder.append(tableMatcher.group(1).toUpperCase(Locale.ROOT));
                    sqlBuilder.append(schemas.get(tableMatcher.group(2).toUpperCase(Locale.ROOT)));
                } else if (queryMatcher.find()) {
                    sqlBuilder.append(queryMatcher.group().toUpperCase(Locale.ROOT));
                    sqlBuilder.append(";\n");
                }
            }
            scanner.close();
            String intermediate = FilenameUtils.getFullPath(filename) + FilenameUtils.getBaseName(filename) + ".sql";
            File sql = new File(intermediate);
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(sql));
            bufferedWriter.write(sqlBuilder.toString());
            bufferedWriter.close();
            parseSQLFile(intermediate);
        } catch (Exception e) {
            System.err.println("In file:\n\t" + filename);
            System.err.println(e.toString().trim() + "\n");
        }
    }

}
