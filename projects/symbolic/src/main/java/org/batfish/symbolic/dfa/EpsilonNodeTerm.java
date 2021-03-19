package org.batfish.symbolic.dfa;

public class EpsilonNodeTerm implements NodeTerm {
  public EpsilonNodeTerm() {}

  @Override public boolean contains(String node) {
    return false;
  }

  @Override public NodeTerm complement() {
    return new NegativeSetNodeTerm();
  }
}
