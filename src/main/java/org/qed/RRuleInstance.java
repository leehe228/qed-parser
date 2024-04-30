package org.qed;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;

public record RRuleInstance() {
    record FilterIntoJoin() implements RRule {
        static final RelRN left = RelRN.scan("Left", "Left_Type");
        static final RelRN right = RelRN.scan("Right", "Right_Type");
        static final RexRN joinCond = left.joinPred("join", right);

        @Override
        public RelRN before() {
            var join = left.join(JoinRelType.INNER, joinCond, right);
            return join.filter("outer");
        }

        @Override
        public RelRN after() {
            return left.join(JoinRelType.INNER, RexRN.and(joinCond, left.joinPred("outer", right)), right);
        }
    }

    record FilterMerge() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");
        static final RexRN inner = source.pred("inner");
        static final RexRN outer = source.pred("outer");

        @Override
        public RelRN before() {
            return source.filter(inner).filter(outer);
        }

        @Override
        public RelRN after() {
            return source.filter(RexRN.and(inner, outer));
        }
    }

    record FilterProjectTranspose() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");
        static final RexRN proj = source.proj("proj", "Project_Type");

        @Override
        public RelRN before() {
            return source.filter(proj.pred("pred")).project(proj);
        }

        @Override
        public RelRN after() {
            return source.project(proj).filter("pred");
        }
    }

    record FilterReduceFalse() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");

        @Override
        public RelRN before() {
            return source.filter(RexRN.falseLiteral());
        }

        @Override
        public RelRN after() {
            return source.empty();
        }
    }

    record FilterReduceTrue() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");

        @Override
        public RelRN before() {
            return source.filter(RexRN.trueLiteral());
        }

        @Override
        public RelRN after() {
            return source;
        }
    }

//    record FilterSetOpTransposeRule implements RRule {
//
//    }

//    record IntersectMerge implements RRule {
//
//    }

    record JoinConditionPush() implements RRule {
        static final RelRN left = RelRN.scan("Left", "Left_Type");
        static final RelRN right = RelRN.scan("Right", "Right_Type");
        static final JoinPred joinPred = new JoinPred(left, right);

        @Override
        public RelRN before() {
            return left.join(JoinRelType.INNER, joinPred, right);
        }

        @Override
        public RelRN after() {
            var leftRN = left.filter(joinPred.leftPred());
            var rightRN = right.filter(joinPred.rightPred());
            return leftRN.join(JoinRelType.INNER, joinPred.bothPred(), rightRN);
        }

        public record JoinPred(RelRN left, RelRN right) implements RexRN {

            @Override
            public RexNode semantics() {
                return RexRN.and(left.joinPred(bothPred(), right), left.joinField(0, right).pred(leftPred()),
                        left.joinField(1, right).pred(rightPred())).semantics();
            }

            public String bothPred() {return "both";}

            public String leftPred() {return "left";}

            public String rightPred() {return "right";}

        }
    }

    record JoinAddRedundantSemiJoin() implements RRule {
        static final RelRN left = RelRN.scan("Left", "Left_Type");
        static final RelRN right = RelRN.scan("Right", "Right_Type");
        static final String pred = "pred";

        @Override
        public RelRN before() {
            return left.join(JoinRelType.INNER, pred, right);
        }

        @Override
        public RelRN after() {
            return left.join(JoinRelType.SEMI, pred, right).join(JoinRelType.INNER, pred, right);
        }
    }

    // Todo: explore join types, see line 102 of JoinAssociateRule
    record JoinAssociate() implements RRule {
        static final RelRN a = RelRN.scan("A", "A_Type");
        static final RelRN b = RelRN.scan("B", "A_Type");
        static final RelRN c = RelRN.scan("C", "A_Type");
        static final String pred_a = "pred_a";
        static final String pred_b = "pred_b";
        static final String pred_ab = "pred_ab";
        static final String pred_c = "pred_c";
        static final String pred_ac = "pred_ac";
        static final String pred_bc = "pred_bc";
        static final String pred_abc = "pred_abc";

        @Override
        public RelRN before() {
            var ab = a.join(JoinRelType.INNER, RexRN.and(
                    new RexRN.JoinField(0, a, b).pred(pred_a),
                    new RexRN.JoinField(1, a, b).pred(pred_b),
                    new RexRN.Pred(pred_ab, true, a.joinFields(b))
            ), b);
            return ab.join(JoinRelType.INNER, RexRN.and(
                    new RexRN.JoinField(2, ab, c).pred(pred_c),
                    new RexRN.Pred(pred_ac, true, a.joinFields(b, 0, 2)),
                    new RexRN.Pred(pred_bc, true, a.joinFields(b, 1, 2)),
                    new RexRN.Pred(pred_abc, true, a.joinFields(b))
            ), c);
        }

        @Override
        public RelRN after() {
            var bc = b.join(JoinRelType.INNER, RexRN.and(
                    new RexRN.JoinField(0, a, b).pred(pred_b),
                    new RexRN.JoinField(1, a, b).pred(pred_c),
                    new RexRN.Pred(pred_bc, true, a.joinFields(b))
            ), c);
            return a.join(JoinRelType.INNER, RexRN.and(
                    new RexRN.JoinField(0, bc, c).pred(pred_a),
                    new RexRN.Pred(pred_ab, true, a.joinFields(b, 0, 1)),
                    new RexRN.Pred(pred_ac, true, a.joinFields(b, 0, 2)),
                    new RexRN.Pred(pred_abc, true, a.joinFields(b))
            ), c);
        }
    }

//    record JoinCommute() implements RRule {
//        static final RelRN left = RelRN.scan("Left", "Left_Type");
//        static final RelRN right = RelRN.scan("Right", "Right_Type");
//        static final String pred = "pred";
//
//        @Override
//        public RelRN before() {
//            return left.join(JoinRelType.INNER, pred, right);
//        }
//
//        @Override
//        public RelRN after() {
//            return right.join(JoinRelType.INNER, new RexRN.Pred(
//                    pred, true, right.joinFields(left, 1, 0)
//            ), left).project("?");
//        }
//    }

//    record JoinExtractFilter() implements RRule {
//
//    }

//    record JoinProjectTranspose() implements RRule {
//
//    }

    // JoinConditionPush?
//    record JoinPushExpressions() implements RRule {
//
//    }

    // JoinConditionPush?
//    record JoinPushTransitivePredicates() implements RRule {
//
//    }

//    record JoinToSemiJoin() implements RRule {
//
//    }

//    record JoinLeftUnionTranspose() implements RRule {
//
//    }

//    record JoinRightUnionTranspose() implements RRule {
//
//    }

    record ProjectFilterTranspose() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");

        @Override
        public RelRN before() {
            var pred = new ProjectFilterTranspose.ProjectFilter(source);
            return source.filter(pred).project(pred.proj(), pred.projType());
        }

        @Override
        public RelRN after() {
            var pred = new ProjectFilterTranspose.ProjectFilter(source);
            return source.project(pred.proj(), pred.projType()).filter(pred.pred());
        }

        public record ProjectFilter(RelRN source) implements RexRN {
            @Override
            public RexNode semantics() {
                return source.pred(pred()).proj(proj(), projType()).semantics();
            }

            public String proj() {
                return "proj";
            }

            public String projType() {
                return "Project_Type";
            }

            public String pred() {
                return "pred";
            }
        }
    }

//    record ProjectJoinRemove() implements RRule {
//
//        @Override
//        public RelRN before() {
//            return null;
//        }
//
//        @Override
//        public RelRN after() {
//            return null;
//        }
//    }

//    record ProjectJoinJoinRemove() implements RRule {
//
//    }

//    record ProjectJoinTranspose() implements RRule {
//
//    }

    record ProjectMerge() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");
        static final RexRN inner = source.proj("inner", "Inner_Type");
        static final String outer = "outer";
        static final String outerType = "Outer_Type";

        @Override
        public RelRN before() {
            return source.project(inner).project(outer, outerType);
        }

        @Override
        public RelRN after() {
            return source.project(inner.proj(outer, outerType));
        }
    }

//    record ProjectSetOpTranspose() implements RRule {
//
//    }

    record ProjectRemove() implements RRule {
        static final RelRN source = RelRN.scan("Source", "Source_Type");

        @Override
        public RelRN before() {
            return source.project(source.field(0));
        }

        @Override
        public RelRN after() {
            return null;
        }
    }

//    record SemiJoinFilterTranspose() implements RRule {
//
//    }

//    record SemiJoinJoinTranspose() implements RRule {
//
//    }

//    record SemiJoinProjectTranspose() implements RRule {
//
//    }

//    record SemiJoinRemove() implements RRule {
//
//    }

//    record UnionMerge() implements RRule {
//
//    }

//    record UnionRemove() implements RRule {
//
//    }
}

/*
 * Semantically identical cases:
 * FilterExpandIsNotDistinctFrom
 * FilterScan
 * JoinReduceExpression
 * ProjectReduceExpression
 * ProjectTableScan
 */
