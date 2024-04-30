package org.qed.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;

public class FilterProjectTranspose extends RelRule<FilterProjectTranspose.Config> {
    protected FilterProjectTranspose(Config config) {
        super(config);
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        var var_3 = call.builder();
        call.transformTo(var_3.filter(((LogicalFilter) call.rel(1)).getCondition()).build());
    }

    public interface Config extends EmptyConfig {
        Config DEFAULT = new Config() {};

        @Override
        default FilterProjectTranspose toRule() {
            return new FilterProjectTranspose(this);
        }

        @Override
        default String description() {
            return "FilterProjectTranspose";
        }

        @Override
        default RelRule.OperandTransform operandSupplier() {
            return s_2 -> s_2.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_0 -> s_0.operand(RelNode.class).anyInputs()));
        }

    }
}
