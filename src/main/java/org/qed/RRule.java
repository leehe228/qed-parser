package org.qed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;

import java.io.File;
import java.io.IOException;

public interface RRule {
    interface RRuleFamily {
        Seq<RRule> family();
    }

    RelRN before();

    RelRN after();

    default String explain() {
        return STR."\{getClass().getName()}\n\{before().semantics().explain()}=>\n\{after().semantics().explain()}";
    }

    default String name() {
        return getClass().getSimpleName();
    }

    default ObjectNode toJson() {
        return JSONSerializer.serialize(Seq.of(before().semantics(), after().semantics()));
    }

    default void dump(String path) throws IOException {
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(new File(path), toJson());
    }
}

