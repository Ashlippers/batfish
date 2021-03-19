package org.batfish.symbolic.dfa;

import java.util.Objects;

public final class NfaState {
  private final int _id;

  private boolean _isAccepted;

  public NfaState(int id) {
    _id = id;
    _isAccepted = false;
  }

  public NfaState(int id, boolean isAccepted) {
    _id = id;
    _isAccepted = isAccepted;
  }

  public static NfaState startState() {
    return new NfaState(0);
  }

  public boolean isAccepted() {
    return _isAccepted;
  }

  public void setAccepted() { _isAccepted = true; }

  public int getId() { return _id; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NfaState)) {
      return false;
    }
    NfaState other = (NfaState) o;
    return _id == other._id;
  }

  @Override
  public int hashCode() { return Objects.hash(_id); }

  @Override
  public String toString() {
    if (_isAccepted) {
      return String.format("NFA@%d[Acc]", _id);
    } else {
      return String.format("NFA@%d", _id);
    }
  }
}
