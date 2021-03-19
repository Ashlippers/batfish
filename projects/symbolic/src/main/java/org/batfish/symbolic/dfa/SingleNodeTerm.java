package org.batfish.symbolic.dfa;

import javax.annotation.Nonnull;
import org.batfish.symbolic.state.NodeStateExpr;

public class SingleNodeTerm implements NodeTerm {
  @Nonnull private final String _node;

  public SingleNodeTerm(@Nonnull String node) {_node = node;}

  @Override public boolean contains(@Nonnull String node) {
    return node.equals(_node);
  }

  @Override public NodeTerm complement() {
    return new NegativeSetNodeTerm(_node);
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof SingleNodeTerm)) {
      return false;
    }

    return _node.equals(((SingleNodeTerm) other)._node);
  }

  @Override
  public final int hashCode() {
    return 31 * getClass().hashCode() + _node.hashCode();
  }

  @Override
  public final String toString() {
    return String.format("<%s>", _node);
  }
}
