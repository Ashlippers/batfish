package org.batfish.symbolic.dfa;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Non-deterministic Finite Automata that encodes a path specifier */
public final class Nfa<R> {
  private String _pathSpec;

  public final Set<NfaState> states;

  public final Table<NfaState, NfaState, R> edges;

  public Nfa() {
    states = new HashSet<>();
    states.add(NfaState.startState());
    edges = HashBasedTable.create();
  }

  public List<NfaState> transit(NfaState oldState, R input) {
    Map<NfaState, R> neighbors = edges.row(oldState);
    List<NfaState> newStates = new ArrayList<>();
    if (neighbors != null) {
      neighbors.forEach(
          (neighbor, e) -> {
            if (e.equals(input)) {
              newStates.add(neighbor);
            }
          }
      );
    }
    return newStates;
  }

  public List<NfaState> transitBackwards(NfaState oldState, R input) {
    Map<NfaState, R> neighbors = edges.column(oldState);
    List<NfaState> newStates = new ArrayList<>();
    if (neighbors != null) {
      neighbors.forEach(
          (neighbor, e) -> {
            if (e.equals(input)) {
              newStates.add(neighbor);
            }
          }
      );
    }
    return newStates;
  }
}
