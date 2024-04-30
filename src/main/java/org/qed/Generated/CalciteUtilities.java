package org.qed.Generated;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rex.RexNode;
import org.qed.RuleBuilder;

import java.util.List;

public record CalciteUtilities() {
    public List<RexNode> compose(RelNode base, List<RexNode> inner, List<RexNode> outer) {
        var builder = RuleBuilder.create();
        return RelOptUtil.pushPastProject(outer, (Project) builder.push(base).project(inner).build());
    }
}
