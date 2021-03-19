package org.batfish.symbolic.dfa;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

public class MetaNFA {
  @Nonnull public MetaNFAState source;
  @Nonnull public MetaNFAState sink;

  public MetaNFA(@Nonnull MetaNFAState src, @Nonnull MetaNFAState snk) {
    source = src;
    sink = snk;
  }

  public Set<MetaNFAState> getStates() {
    Set<MetaNFAState> states = new HashSet<>();
    Set<MetaNFAState> dirtyStates = new HashSet<>();
    dirtyStates.add(source);
    states.add(source);
    source.id = 0;
    int nextId = 1;
    int cnt;
    do{
      cnt = states.size();
      Set<MetaNFAState> newDirtyStates = new HashSet<>();
      for (MetaNFAState s: dirtyStates)
      {
        Set<MetaNFAState> neighbors = new HashSet<>(s.outEpsilonEdges);
        neighbors.addAll(s.outEdges.values());
        for (MetaNFAState ns: neighbors)
        {
          if (!states.contains(ns)) {
            newDirtyStates.add(ns);
            states.add(ns);
            ns.id = nextId++;
          }
        }
      }
      dirtyStates = newDirtyStates;
    } while (states.size() > cnt);
    return states;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof MetaNFA)) {
      return false;
    }

    return source.equals(((MetaNFA) other).source) &&
        sink.equals(((MetaNFA) other).sink);
  }

  @Override
  public final int hashCode() {
    return 31 * Objects.hash(source) + Objects.hash(sink);
  }

  @Override
  public final String toString() {
    return String.format("MetaNFA{%s}", getStates());
  }
}
