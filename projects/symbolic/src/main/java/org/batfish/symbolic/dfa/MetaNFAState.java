package org.batfish.symbolic.dfa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

public class MetaNFAState {
  public boolean isAccepted;
  public int id = -1;
  @Nonnull public Map<NodeTerm, MetaNFAState> outEdges;
  @Nonnull public Set<MetaNFAState> outEpsilonEdges;

  public MetaNFAState(boolean accepted) {
    isAccepted = accepted;
    outEdges = new HashMap<>();
    outEpsilonEdges = new HashSet<>();
  }

  public Set<Integer> getNeighborIds() {
    return Stream.concat(
          outEdges.values().stream(),
          outEpsilonEdges.stream())
        .map(s -> s.id)
        .collect(Collectors.toSet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetaNFAState)) {
      return false;
    }
    MetaNFAState other = (MetaNFAState) o;
    return isAccepted == other.isAccepted
        && id == other.id
        && outEdges.equals(other.outEdges)
        && outEpsilonEdges.equals(other.outEpsilonEdges);
  }

  @Override
  public int hashCode() { return 23 * Objects.hash(outEdges.keySet()) + 67 * Objects.hash(outEpsilonEdges.size()) +
      Objects.hash(isAccepted); }

  @Override
  public String toString() {
    if (isAccepted) {
      return String.format("eNFAState[%d][Acc]->%s", id, getNeighborIds());
    } else {
      return String.format("eNFAState[%d]->%s", id, getNeighborIds());
    }
  }
}
