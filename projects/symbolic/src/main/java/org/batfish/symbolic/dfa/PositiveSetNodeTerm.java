package org.batfish.symbolic.dfa;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

public class PositiveSetNodeTerm implements NodeTerm {
  private final Set<String> _nodes = new HashSet<>();

  public PositiveSetNodeTerm() {}

  public PositiveSetNodeTerm(Set<String> nodes) {
    _nodes.addAll(nodes);
  }

  @Override public boolean contains(@Nonnull String node) {
    return _nodes.contains(node);
  }

  @Override public NodeTerm complement() {
    return new NegativeSetNodeTerm(_nodes);
  }
}