package org.qed;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableList;
import org.apache.calcite.rel.core.CorrelationId;

import java.util.Set;

record Env(int base, int delta, ImmutableMap<CorrelationId, Integer> globals, MutableList<QedTable> tables) {
    static Env empty() {
        return new Env(0, 0, ImmutableMap.empty(), MutableList.create());
    }

    Env recorded(Set<CorrelationId> ids) {
        return new Env(base, delta, Seq.from(ids).foldLeft(globals, (g, id) -> g.putted(id, base)), tables);
    }

    Env advanced(int d) {
        return new Env(base + delta, d, globals, tables);
    }

    Env lifted(int d) {
        return new Env(base + d, delta, globals, tables);
    }

    int resolve(QedTable table) {
        var idx = tables.indexOf(table);
        if (idx == -1) {
            idx = tables.size();
            tables.append(table);
        }
        return idx;
    }

    int resolve(CorrelationId id) {
        return globals.getOrThrow(id, () -> new RuntimeException("Correlation ID not declared"));
    }
}
