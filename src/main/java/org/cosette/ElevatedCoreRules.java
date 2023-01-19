package org.cosette;

import kala.collection.Seq;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ElevatedCoreRules {

    public static Tuple2<RelNode, RelNode> calcMerge() {
        // A Calc is equivalent to a project above a filter
        RuleBuilder builder = RuleBuilder.create();
        CosetteTable input = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("INPUT", true), false)));
        SqlOperator bottomFilter = builder.genericPredicateOp("bottom", true);
        SqlOperator bottomProject = builder.genericProjectionOp("bottom", new RelType.VarType("INTER", true));
        SqlOperator topFilter = builder.genericPredicateOp("top", true);
        SqlOperator topProject = builder.genericProjectionOp("top", new RelType.VarType("RESULT", true));
        builder.addTable(input);
        builder.scan(input.getName());
        builder.filter(builder.call(bottomFilter, builder.fields()));
        builder.project(builder.call(bottomProject, builder.fields()));
        builder.filter(builder.call(topFilter, builder.fields()));
        builder.project(builder.call(topProject, builder.fields()));
        RelNode before = builder.build();
        builder.scan(input.getName());
        builder.filter(builder.call(SqlStdOperatorTable.AND,
                builder.call(bottomFilter, builder.fields()),
                builder.call(topFilter, builder.call(bottomProject, builder.fields()))));
        builder.project(builder.call(topProject, builder.call(bottomProject, builder.fields())));
        RelNode after = builder.build();
        return Tuple.of(before, after);
    }

    public static Tuple2<RelNode, RelNode> filterIntoJoin() {
        // TODO: carefully think about join types
        RuleBuilder builder = RuleBuilder.create();
        CosetteTable left = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("LEFT", true), false)));
        CosetteTable right = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("RIGHT", true), false)));
        builder.addTable(left).addTable(right);
        builder.scan(left.getName()).scan(right.getName());
        SqlOperator joinCondition = builder.genericPredicateOp("join", true);
        SqlOperator filterCondition = builder.genericPredicateOp("filter", true);
        builder.join(JoinRelType.INNER, builder.call(joinCondition, Seq.from(builder.fields(2, 0)).concat(builder.fields(2, 1))));
        builder.filter(builder.call(filterCondition, builder.fields()));
        RelNode before = builder.build();
        builder.scan(left.getName()).scan(right.getName());
        builder.join(JoinRelType.FULL, builder.call(SqlStdOperatorTable.AND,
                builder.call(joinCondition, Seq.from(builder.fields(2, 0)).concat(builder.fields(2, 1))),
                builder.call(filterCondition, Seq.from(builder.fields(2, 0)).concat(builder.fields(2, 1)))));
        RelNode after = builder.build();
        return Tuple.of(before, after);
    }

    public static Tuple2<RelNode, RelNode> filterProjectTranspose() {
        RuleBuilder builder = RuleBuilder.create();
        CosetteTable input = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("INPUT", true), false)));
        builder.addTable(input);
        SqlOperator project = builder.genericProjectionOp("select", new RelType.VarType("PROJECT", true));
        SqlOperator filter = builder.genericPredicateOp("filter", true);
        builder.scan(input.getName()).project(builder.call(project, builder.fields()));
        builder.filter(builder.call(filter, builder.fields()));
        RelNode before = builder.build();
        builder.scan(input.getName()).filter(builder.call(filter, builder.call(project, builder.fields())));
        builder.project(builder.call(project, builder.fields()));
        RelNode after = builder.build();
        return Tuple.of(before, after);
    }

    public static Tuple2<RelNode, RelNode> filterCorrelate() {
        return null;
    }

    public static Option<Tuple2<RelNode, RelNode>> filterSetOpTranspose(SqlOperator kind) {
        RuleBuilder builder = RuleBuilder.create();
        CosetteTable one = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("SHARED", true), false)));
        CosetteTable other = builder.createSimpleTable(Seq.of(Tuple.of(new RelType.VarType("SHARED", true), false)));
        SqlOperator filter = builder.genericPredicateOp("filter", true);
        builder.addTable(one).addTable(other);
        builder.scan(one.getName()).scan(other.getName());
        if (kind == SqlStdOperatorTable.UNION_ALL) {
            builder.union(true);
        } else if (kind == SqlStdOperatorTable.EXCEPT_ALL) {
            builder.minus(true);
        } else if (kind == SqlStdOperatorTable.INTERSECT_ALL) {
            builder.intersect(true);
        } else {
            return Option.none();
        }
        builder.filter(builder.call(filter, builder.fields()));
        RelNode before = builder.build();
        builder.scan(one.getName()).filter(builder.call(filter, builder.fields()));
        builder.scan(other.getName()).filter(builder.call(filter, builder.fields()));
        if (kind == SqlStdOperatorTable.UNION_ALL) {
            builder.union(true);
        } else if (kind == SqlStdOperatorTable.EXCEPT_ALL) {
            builder.minus(true);
        } else {
            builder.intersect(true);
        }
        RelNode after = builder.build();
        return Option.some(Tuple.of(before, after));
    }

    public static Tuple2<RelNode, RelNode> filterUnionTranspose() {
        return filterSetOpTranspose(SqlStdOperatorTable.UNION_ALL).get();
    }

    public static Tuple2<RelNode, RelNode> filterExceptTranspose() {
        return filterSetOpTranspose(SqlStdOperatorTable.EXCEPT_ALL).get();
    }

    /**
     * Currently Intersect is not supported by prover
     * public static Tuple2<RelNode, RelNode> filterIntersectTranspose() {
     *     return filterSetOpTranspose(SqlStdOperatorTable.INTERSECT_ALL).get();
     * }
     */

    public static Tuple2<RelNode, RelNode> projectCorrelateTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectFilterTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectSubQueryToCorrelate() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> filterSubQueryToCorrelate() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinSubQueryToCorrelate() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectToSemiJoin() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectJoinRemove() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectJoinTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectSetOpTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinConditionPush() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinAddRedundantSemiJoin() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinAssociate() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinCommute() {
        // Inner/Outer joins
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinExtractFilter() {
        // Inner/Outer joins
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinProjectBothTranspose() {
        // Inner/Outer joins
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinProjectLeftTranspose() {
        // Inner/Outer joins
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinProjectRightTranspose() {
        // Inner/Outer joins
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinPushExpressions() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinPushTransitivePredicates() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinDeriveIsNotNullFilter() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinToCorrelate() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinToSemiJoin() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinLeftUnionTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> joinRightUnionTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> semiJoinFilterTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> semiJoinProjectTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> semiJoinJoinTranspose() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> semiJoinRemove() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> unionMerge() {
        return null;
    }

    public static Tuple2<RelNode, RelNode> projectFilterValueMerge() {
        return null;
    }

    public static void dumpElevatedRules(Path dumpFolder, boolean verbose) throws IOException {
        Files.createDirectories(dumpFolder);
        Seq.of(ElevatedCoreRules.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()) && method.getReturnType().getName().equals("kala.tuple.Tuple2"))
                .forEachUnchecked(method -> {
                    String ruleName = method.getName();
                    Tuple2<RelNode, RelNode> rewrite = (Tuple2<RelNode, RelNode>) method.invoke(null);
                    if (verbose) {
                        System.out.println(">>>>>> " + ruleName + " <<<<<<");
                        System.out.println("Before:");
                        System.out.println(rewrite._1.explain());
                        System.out.println("After:");
                        System.out.println(rewrite._2.explain());
                    }
                    File dump = Paths.get(dumpFolder.toAbsolutePath().toString(), ruleName + ".json").toFile();
                    RelJSONShuttle.dumpToJSON(List.of(rewrite._1, rewrite._2), dump);
                });
    }

    public static void main(String[] args) throws IOException {
        Path dumpFolder = Paths.get("ElevatedRules");
        dumpElevatedRules(dumpFolder, true);
    }

    /**
     * Ignored rules:
     * - Aggregation related rules: unsupported for now
     *   - Aggregate*
     *   - CalcToWindow
     *   - FilterAggregateTranspose
     *   - ProjectAggregateMerge
     *   - Aggregate values
     * - Multi-join related rules: unsupported for now:
     *   - FilterMultiJoinRule
     *   - ProjectMultiJoinMerge
     *   - JoinToMultiJoin
     *   - MultiJoin*
     * - Sort related rules: unsupported for now
     *   - Sort*
     * - CalcRemove: trivially true
     * - CalcReduceDecimal: casting is not understood by the prover
     * - CalcReduceExpression: constant reduction is trivial
     * - CalcSplit: split calc into project above filter, which is exactly how calc is represented in cosette
     * - CalcToWindow: window not supported
     * - CoerceInputs: casting is not understood by the prover
     * - ExchangeRemoveConstantKeys: exchange not supported
     * - SortExchangeRemoveConstantKeys: exchange not supported
     * - FilterIntoJoinDumb: special case of FilterIntoJoin
     * - FilterMerge: special case of CalcMerge
     * - FilterCalcMerge: special case of CalcMerge
     * - FilterToCalc: special case of CalcMerge
     * - FilterTableFunctionTranspose: functionScan is not understood by prover
     * - FilterScan: filterScan not supported
     * - FilterInterpreterScan: filterScan not supported
     * - FilterExpandIsNotDistinctFrom: case when is not understood by prover
     * - FilterReduceExpression: constant reduction is trivial
     * - IntersectMerge: intersect not supported
     * - IntersectToDistinct: intersect not supported
     * - Match: match not supported
     * - MinusMerge: minus with multiple inputs not supported
     * - ProjectCalcMerge: special case of CalcMerge
     * - ProjectReduceExpressions: constant reduction is trivial
     * - ProjectToLogicalProjectAndWindow: window not supported
     * - ProjectMerge: special case of CalcMerge
     * - ProjectRemove: trivially true
     * - ProjectTableScan: bindable table-scan not supported
     * - ProjectInterpreterTableScan: bindable table-scan not supported
     * - ProjToCalc special case of CalcMerge
     * - ProjectWindowTranspose: window not supported
     * - JoinCommuteOuter: special case of JoinCommute
     * - JoinProject*TransposeIncludeOuter: special cases of JoinProject*Transpose
     * - JoinReduceExpressions: constant reduction is trivial
     * - UnionRemove: trivially true
     * - UnionPullUpConstants: trivially true
     * - UnionToDistinct: trivially true
     * - FilterValuesMerge: special case of ProjectFilterValuesMerge
     * - ProjectValuesMerge: special case of ProjectFilterValuesMerge
     * - WindowReduceExpressions: constant reduction is trivial
     */

}
