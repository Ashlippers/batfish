package org.batfish.symbolic.dfa;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.batfish.common.BatfishException;

/**
 * Utility methods for {@link Nfa} and {@link NfaState}.
 */
public final class NfaUtils {
  public static Nfa compile(@Nonnull String pathSpec) {
    String insertedPathSpec = insertExplicitConcatOperator(pathSpec);
    List<Object> postfix = toPostfix(insertedPathSpec);
    MetaNFA eNFA = toEpsilonNFA(postfix);
    Nfa nfa = toNFA(eNFA);
    return nfa;
  }

  private static final char concatOp = '~';

  public static String insertExplicitConcatOperator(String pathSpec) {
    StringBuilder output = new StringBuilder();

    int p = 0;
    while (p < pathSpec.length()) {
      char c = pathSpec.charAt(p);
      if (c != '<') {
        output.append(c);
      }
      if (c == '<') {
        // consume current node-term
        int nodeTermStart = p + 1;
        int nodeTermEnd = pathSpec.indexOf('>', p + 1);
        if (nodeTermEnd == -1) {
          throw new BatfishException(String.format("Bad path-spec: '>' expected for '<' at %d", p));
        }
        output.append(pathSpec.substring(nodeTermStart - 1, nodeTermEnd + 1));
        p = nodeTermEnd;
      }
      // concatOp is reserved
      else if (c == concatOp) {
        throw new BatfishException(String.format("Bad path-spec: '%c' not allowed at %d", concatOp, p));
      }
      // start of a fragment, no need to concat
      else if (c == '(' || c == '|' || c == '^') {
        ++p;
        continue;
      }

      if (p < pathSpec.length() - 1) {
        char peek = pathSpec.charAt(p+1);
        // end of a fragment, no need to concat
        if (peek == '*' || peek == '|' || peek == ')' || peek == '$') {
          ++p;
          continue;
        }
        output.append(concatOp);
      }
      ++p;
    }
    return output.toString();
  }

  private static final Map<Character, Integer> opPrecedence = new ImmutableMap.Builder<Character, Integer>()
      .put('^', -1)
      .put('$', -1)
      .put('|', 0)
      .put(concatOp, 1)
      .put('*', 2)
      .build();

  // convert pathSpec to postfix regular expression
  public static List<Object> toPostfix(String pathSpec) {
    Set<Character> ops = Stream.of('(', ')', '|', '*', '^', '$', concatOp)
        .collect(Collectors.toSet());

    List<Object> postfix = new ArrayList<>();
    Stack<Character> opStack = new Stack<>();

    int p = 0;
    while (p < pathSpec.length()) {
      char c = pathSpec.charAt(p);

      if (c == concatOp || c == '|' || c == '*' || c == '^' || c == '$') {
        while (!opStack.empty() && opStack.peek() != '(' &&
            opPrecedence.get(opStack.peek()) >= opPrecedence.get(c)) {
          postfix.add(opStack.pop());
        }
        opStack.push(c);
      }
      else if (c == '(') {
        opStack.push(c);
      }
      else if (c == ')') {
        boolean canary = false;
        while (!opStack.empty()) {
          char op = opStack.pop();
          if (op == '(') {
            canary = true;
            break;
          }
          postfix.add(op);
        }
        if (!canary) {
          throw new BatfishException(String.format("Bad path-spec: '(' expected for ')' at %d", p));
        }
      }
      else if (c == '<') {
        // consume current node-term
        int nodeTermStart = p + 1;
        int nodeTermEnd = pathSpec.indexOf('>', p + 1);
        if (nodeTermEnd == -1) {
          throw new BatfishException(String.format("Bad path-spec: '>' expected for '<' at %d", p));
        }
        postfix.add(new SingleNodeTerm(pathSpec.substring(nodeTermStart, nodeTermEnd)));
        p = nodeTermEnd;
      }
      else if (c == '.') {
        postfix.add(new NegativeSetNodeTerm());
      }
      ++p;
    }

    while (!opStack.empty()) {
      postfix.add(opStack.pop());
    }

    return postfix;
  }

  private static void addTransition(MetaNFAState from, MetaNFAState to, NodeTerm edge) {
    from.outEdges.put(edge, to);
  }

  private static void addEpsilonTransition(MetaNFAState from, MetaNFAState to) {
    from.outEpsilonEdges.add(to);
  }

  // basic NFA
  private static MetaNFA fromNodeTerm(NodeTerm node) {
    MetaNFAState src = new MetaNFAState(false);
    MetaNFAState snk = new MetaNFAState(true);
    addTransition(src, snk, node);

    return new MetaNFA(src, snk);
  }

  // apply Kleene's closure on an NFA
  private static MetaNFA closure(MetaNFA subNFA) {
    MetaNFAState src = new MetaNFAState(false);
    MetaNFAState snk = new MetaNFAState(true);

    addEpsilonTransition(src, snk);
    addEpsilonTransition(src, subNFA.source);
    addEpsilonTransition(subNFA.sink, snk);
    addEpsilonTransition(subNFA.sink, subNFA.source);
    subNFA.sink.isAccepted = false;

    return new MetaNFA(src, snk);
  }

  // apply concatenation on two NFAs
  private static MetaNFA concat(MetaNFA left, MetaNFA right) {
    addEpsilonTransition(left.sink, right.source);
    left.sink.isAccepted = false;
    return new MetaNFA(left.source, right.sink);
  }

  // apply alternation '|' on two NFAs
  private static MetaNFA alt(MetaNFA a, MetaNFA b) {
    MetaNFAState src = new MetaNFAState(false);
    MetaNFAState snk = new MetaNFAState(true);

    addEpsilonTransition(src, a.source);
    addEpsilonTransition(src, b.source);
    addEpsilonTransition(a.sink, snk);
    addEpsilonTransition(b.sink, snk);
    a.sink.isAccepted = false;
    b.sink.isAccepted = false;

    return new MetaNFA(src, snk);
  }

  // build an e-NFA from postfix expression using basic building blocks
  // and e-transitions
  public static MetaNFA toEpsilonNFA(List<Object> postfix) {
    Stack<MetaNFA> stack = new Stack<>();
    boolean hasCaret = false;
    boolean hasDollar = false;

    for (Object o: postfix)
    {
      if (o instanceof NodeTerm) {
        stack.push(fromNodeTerm((NodeTerm) o));
      }
      else if (o instanceof Character) {
        char c = (char) o;
        if (c == '*') {
          stack.push(closure(stack.pop()));
        }
        else if (c == concatOp) {
          MetaNFA right = stack.pop();
          MetaNFA left = stack.pop();
          stack.push(concat(left, right));
        }
        else if (c == '|') {
          MetaNFA right = stack.pop();
          MetaNFA left = stack.pop();
          stack.push(alt(left, right));
        }
        else if (c == '^') {
          hasCaret = true;
        }
        else if (c == '$') {
          hasDollar = true;
        }
        else {
          throw new BatfishException(String.format("Path-spec operator '%c' not implemented", c));
        }
      }
      else {
        throw new BatfishException(String.format("Path-spec term %s not recognized", o.toString()));
      }
    }

    MetaNFA eNFA = stack.pop();

    // todo
    // process open path start
    // if (!hasCaret) {}
    // process open path end
    // if (!hasDollar) {}
    return eNFA;
  }

  // convert a metaNFA (with epsilon transitions) to NFA format
  public static Nfa toNFA(MetaNFA eNFA) {
    Nfa nfa = new Nfa();

    // find all states
    Map<Integer, MetaNFAState> metaStates = eNFA.getStates().stream()
        .collect(Collectors.toMap(s -> s.id, s -> s));
    Map<Integer, NfaState> states = metaStates.values().stream()
        .map(s -> new NfaState(s.id, s.isAccepted))
        .collect(Collectors.toMap(NfaState::getId, s -> s));

    // compute e-closure of each e-NFA node
    Map<Integer, Set<Integer>> eClosure = new HashMap<>();
    for (int id: metaStates.keySet()) {
      Set<Integer> closure = new HashSet<>();
      // add self to e-closure
      closure.add(id);
      Set<Integer> dirtySet = new HashSet<>(closure);
      while (!dirtySet.isEmpty()) {
        Set<Integer> newDirtySet = new HashSet<>();

        // find all out edges of dirty states
        Set<Integer> downstreams = dirtySet.stream()
            .flatMap(v -> metaStates.get(v).outEpsilonEdges.stream().map(s -> s.id))
            .collect(Collectors.toSet());
        for (int downId: downstreams) {
          if (!closure.contains(downId)) {
            newDirtySet.add(downId);
            closure.add(downId);

            //if a final node is in e-closure, make state final
            if (metaStates.get(downId).isAccepted) {
              metaStates.get(id).isAccepted = true;
              states.get(id).setAccepted();
            }

          }
        }
        dirtySet = newDirtySet;
      }

      eClosure.put(id, closure);
    }

    // compute transitions for NFA
    for (int id: states.keySet()) {
      NfaState state = states.get(id);

      eClosure.get(id).stream()
          // get all out edges that start from state's e-closure
          .flatMap(v -> metaStates.get(v).outEdges.entrySet().stream())
          .forEach(
              // out edge: (transition, neighbor)
              kv -> {
                int neighborId = kv.getValue().id;

                // the edge could transit to all states in neighbor's e-closure
                /*eClosure.get(neighborId).forEach(
                    destId -> {
                      if (!nfa.edges.contains(state, states.get(destId))) {
                        nfa.edges.put(state, states.get(destId), new HashSet<>());
                      }
                      nfa.edges.get(state, states.get(destId)).add(kv.getKey());
                    }
                );*/
                if (!nfa.edges.contains(state, states.get(neighborId))) {
                  nfa.edges.put(state, states.get(neighborId), new HashSet<>());
                }
                nfa.edges.get(state, states.get(neighborId)).add(kv.getKey());
              }
          );
    }

    // simplify NFA by eliminating invalid states that cannot reach final or cannot be reached
    // from start
    // backwards fixpoint from final states
    Set<Integer> backwardsValidStates = states.values().stream()
        .filter(NfaState::isAccepted)
        .map(NfaState::getId)
        .collect(Collectors.toSet());
    Set<Integer> dirtySet = new HashSet<>(backwardsValidStates);
    while (!dirtySet.isEmpty()) {
      Set<Integer> newDirtySet = new HashSet<>();

      for (int dirtyId: dirtySet) {
        // get all incoming edges
        nfa.edges.column(states.get(dirtyId)).keySet()
            .forEach(
                s -> {
                  // reachable from final states, add to valid set
                  if (!backwardsValidStates.contains(s.getId())) {
                    newDirtySet.add(s.getId());
                    backwardsValidStates.add(s.getId());
                  }
                }
            );
      }
      dirtySet = newDirtySet;
    }

    Set<Integer> forwardsValidStates = new HashSet<>();
    forwardsValidStates.add(0);
    dirtySet = new HashSet<>(forwardsValidStates);
    while (!dirtySet.isEmpty()) {
      Set<Integer> newDirtySet = new HashSet<>();

      for (int dirtyId: dirtySet) {
        // get all outgoing edges
        nfa.edges.row(states.get(dirtyId)).keySet()
            .forEach(
                s -> {
                  // reachable from start state, add to valid set
                  if (!forwardsValidStates.contains(s.getId())) {
                    newDirtySet.add(s.getId());
                    forwardsValidStates.add(s.getId());
                  }
                }
            );
      }
      dirtySet = newDirtySet;
    }
    Set <Integer> validStates = new HashSet<>(forwardsValidStates);
    validStates.retainAll(backwardsValidStates);

    // filter invalid edges and states;
    for (NfaState s: states.values()) {
      // drop all incoming and outgoing edges
      if (!validStates.contains(s.getId())) {
        if (nfa.edges.containsRow(s)) {
          nfa.edges.row(s).clear();
        }
        if (nfa.edges.containsColumn(s)) {
          nfa.edges.column(s).clear();
        }
      }
    }
    nfa.states.addAll(validStates.stream().map(states::get).collect(Collectors.toSet()));

    return nfa;
  }
}



