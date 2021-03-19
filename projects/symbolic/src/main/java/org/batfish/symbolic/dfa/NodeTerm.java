package org.batfish.symbolic.dfa;

public interface NodeTerm {
  public boolean contains(String node);
  public NodeTerm complement();
}
