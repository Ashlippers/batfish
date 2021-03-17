package org.batfish.symbolic.dfa;

import javax.annotation.Nonnull;
import org.batfish.symbolic.state.StateExpr;

/** Representing the cross-product state of a NFA and a fixpoint engine */
public final class NfaCombinedStateExpr {
  public final @Nonnull StateExpr stateExpr;

  public final @Nonnull NfaState nfaState;

  public NfaCombinedStateExpr(@Nonnull StateExpr stateExpr, @Nonnull NfaState nfaState) {
    this.stateExpr = stateExpr;
    this.nfaState = nfaState;}
}