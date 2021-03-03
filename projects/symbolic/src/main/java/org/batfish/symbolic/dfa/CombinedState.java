package org.batfish.symbolic.dfa;

import javax.annotation.Nonnull;
import org.batfish.symbolic.state.StateExpr;

/** Representing the cross-product state of a DFA and a fixpoint engine */
public final class CombinedState {
  public final @Nonnull StateExpr stateExpr;

  public final @Nonnull DfaState dfaState;

  public CombinedState(@Nonnull StateExpr stateExpr, @Nonnull DfaState dfaState) {
    this.stateExpr = stateExpr;
    this.dfaState = dfaState;}
}
