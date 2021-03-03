package org.batfish.symbolic.dfa;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Deterministic Finite Automata that encodes a path specifier */
public final class Dfa<R> {
  private String pathSpec;

  public final Set<DfaState> states;

  public final Table<DfaState, R, DfaState> edges;

  public Dfa() {
    states = new HashSet<>();
    states.add(DfaState.StartState());
    edges = HashBasedTable.create();
  }

  public Optional<DfaState> transit(DfaState oldState, R input) {
    return Optional.ofNullable(edges.get(oldState, input));
  }
}
