package org.batfish.symbolic.dfa;

import java.util.Objects;
import javax.annotation.Nonnull;

/** State representation of DFA */
public final class DfaState{

  private final int _id;

  private final boolean _isFinal;

  private final boolean _isAccepted;

  public DfaState(int id) {
    _id = id;
    _isFinal = false;
    _isAccepted = false;
  }

  public DfaState(int id, boolean isFinal) {
    _id = id;
    _isFinal = isFinal;
    _isAccepted = false;
  }

  public DfaState(int id, boolean isFinal, boolean isAccepted) {
    _id = id;
    _isFinal = isFinal;
    _isAccepted = isAccepted;
  }

  public static DfaState StartState() {
    return new DfaState(0);
  }

  public boolean isBad() {
    return _isFinal && !_isAccepted;
  }

  public boolean isAccepted() {
    return _isFinal && _isAccepted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DfaState)) {
      return false;
    }
    DfaState other = (DfaState) o;
    return _id == other._id;
  }

  @Override
  public int hashCode() { return Objects.hash(_id); }

  @Override
  public String toString() {
    if (_isFinal) {
      String finalState = _isAccepted ? "Accepted" : "Rejected";
      return String.format("DFA@%d[%s]", _id, finalState);
    } else {
      return String.format("DFA@%d", _id);
    }
  }
}
