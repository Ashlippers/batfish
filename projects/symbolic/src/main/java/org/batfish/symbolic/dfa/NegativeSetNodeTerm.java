package org.batfish.symbolic.dfa;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

public class NegativeSetNodeTerm implements NodeTerm {
  private final Set<String> _excludedNodes = new HashSet<>();

  // this implements dot '.' operator
  public NegativeSetNodeTerm() {}

  public NegativeSetNodeTerm(String negateNode) {
    _excludedNodes.add(negateNode);
  }

  public NegativeSetNodeTerm(Set<String> negateNodes) {
    _excludedNodes.addAll(negateNodes);
  }

  @Override public boolean contains(@Nonnull String node) {
    return !_excludedNodes.contains(node);
  }

  @Override public NodeTerm complement() {
    return new PositiveSetNodeTerm(_excludedNodes);
  }

  @Override
  public final String toString() {
    if (_excludedNodes.isEmpty()) {
      return ".";
    }
    return String.format("[^%s]", _excludedNodes);
  }
}