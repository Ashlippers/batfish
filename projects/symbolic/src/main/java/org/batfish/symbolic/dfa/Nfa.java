package org.batfish.symbolic.dfa;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Non-deterministic Finite Automata that encodes a path specifier */
public final class Nfa {
  private String _pathSpec;

  private int _stateCnt = 0;

  public final Set<NfaState> states;

  public final Table<NfaState, NfaState, Set<NodeTerm>> edges;

  public Nfa() {
    states = new HashSet<>();
    // states.add(NfaState.startState());
    // _stateCnt = 1;
    edges = HashBasedTable.create();
  }

  public int nextId () {
    return _stateCnt++;
  }

  public List<NfaState> transit(NfaState oldState, String input) {
    Map<NfaState, Set<NodeTerm>> neighbors = edges.row(oldState);
    List<NfaState> newStates = new ArrayList<>();
    if (neighbors != null) {
      neighbors.forEach(
          (neighbor, edges) -> {
            boolean transits = edges.stream()
                .map(edge -> edge.contains(input))
                .reduce(Boolean.FALSE, Boolean::logicalOr);
            if (transits) {
              newStates.add(neighbor);
            }
          }
      );
    }
    return newStates;
  }

  public List<NfaState> transitBackwards(NfaState oldState, String input) {
    Map<NfaState, Set<NodeTerm>> neighbors = edges.column(oldState);
    List<NfaState> newStates = new ArrayList<>();
    if (neighbors != null) {
      neighbors.forEach(
          (neighbor, edges) -> {
            boolean transits = edges.stream()
                .map(edge -> edge.contains(input))
                .reduce(Boolean.FALSE, Boolean::logicalOr);
            if (transits) {
              newStates.add(neighbor);
            }
          }
      );
    }
    return newStates;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Nfa)) {
      return false;
    }
    Nfa other = (Nfa) o;
    return states.equals(other.states) && edges.equals(other.edges);
  }

  @Override
  public int hashCode() { return Objects.hash(edges); }

  @Override
  public String toString() {
    return states.toString() + edges.toString();
  }
}
