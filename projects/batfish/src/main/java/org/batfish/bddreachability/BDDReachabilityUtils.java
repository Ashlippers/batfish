package org.batfish.bddreachability;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableTable.toImmutableTable;
import static org.batfish.common.util.CollectionUtil.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Streams;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.bddreachability.transition.Transition;
import org.batfish.bddreachability.transition.Transitions;
import org.batfish.common.BatfishException;
import org.batfish.common.bdd.BDDIpProtocol;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.Flow.Builder;
import org.batfish.datamodel.transformation.AssignPortFromPool;
import org.batfish.symbolic.IngressLocation;
import org.batfish.symbolic.dfa.CombinedState;
import org.batfish.symbolic.dfa.Dfa;
import org.batfish.symbolic.dfa.DfaState;
import org.batfish.symbolic.dfa.Nfa;
import org.batfish.symbolic.dfa.NfaCombinedStateExpr;
import org.batfish.symbolic.dfa.NfaState;
import org.batfish.symbolic.state.NodeAccept;
import org.batfish.symbolic.state.OriginateInterfaceLink;
import org.batfish.symbolic.state.OriginateVrf;
import org.batfish.symbolic.state.StateExpr;

/**
 * Utility methods for {@link BDDReachabilityAnalysis} and {@link BDDReachabilityAnalysisFactory}.
 */
public final class BDDReachabilityUtils {
  public static Table<StateExpr, StateExpr, Transition> computeForwardEdgeTable(
      Iterable<Edge> edges) {
    return computeForwardEdgeTable(Streams.stream(edges));
  }

  static Table<StateExpr, StateExpr, Transition> computeForwardEdgeTable(Stream<Edge> edges) {
    return edges.collect(
        toImmutableTable(
            Edge::getPreState,
            Edge::getPostState,
            Edge::getTransition,
            (t1, t2) -> Transitions.or(t1, t2)));
  }

  /** Apply edges to the reachableSets until a fixed point is reached. */
  @VisibleForTesting
  static void fixpoint(
      Map<StateExpr, BDD> reachableSets,
      Table<StateExpr, StateExpr, Transition> edges,
      BiFunction<Transition, BDD, BDD> traverse) {
    Span span = GlobalTracer.get().buildSpan("BDDReachabilityAnalysis.fixpoint").start();
    try (Scope scope = GlobalTracer.get().scopeManager().activate(span)) {
      assert scope != null; // avoid unused warning
      Set<StateExpr> dirtyStates = ImmutableSet.copyOf(reachableSets.keySet());

      while (!dirtyStates.isEmpty()) {
        Set<StateExpr> newDirtyStates = new HashSet<>();

        dirtyStates.forEach(
            dirtyState -> {
              Map<StateExpr, Transition> dirtyStateEdges = edges.row(dirtyState);
              if (dirtyStateEdges == null) {
                // dirtyState has no edges
                return;
              }

              BDD dirtyStateBDD = reachableSets.get(dirtyState);
              dirtyStateEdges.forEach(
                  (neighbor, edge) -> {
                    BDD result = traverse.apply(edge, dirtyStateBDD);
                    if (result.isZero()) {
                      return;
                    }

                    // update neighbor's reachable set
                    BDD oldReach = reachableSets.get(neighbor);
                    BDD newReach = oldReach == null ? result : oldReach.or(result);
                    if (oldReach == null || !oldReach.equals(newReach)) {
                      reachableSets.put(neighbor, newReach);
                      newDirtyStates.add(neighbor);
                    }
                  });
            });

        dirtyStates = newDirtyStates;
      }
    } finally {
      span.finish();
    }
  }

  @Nullable private static CombinedState computeInitState(Dfa<StateExpr> dfa, StateExpr state) {
    if (state instanceof NodeAccept) {
      Optional<DfaState> result = dfa.transit(DfaState.StartState(), state);
      return result.map(dfaState -> new CombinedState(state, dfaState)).orElse(null);
    }
    return new CombinedState(state, DfaState.StartState());
  }

  @VisibleForTesting
  static void prunedFixpoint(
      Map<StateExpr, BDD> reachableSets,
      Table<StateExpr, StateExpr, Transition> edges,
      BiFunction<Transition, BDD, BDD> traverse,
      Dfa<StateExpr> dfa) {
    Span span = GlobalTracer.get().buildSpan("BDDReachabilityAnalysis.prunedFixpoint").start();
    try (Scope scope = GlobalTracer.get().scopeManager().activate(span)) {
      assert scope != null; // avoid unused warning

      Set<CombinedState> dirtyStates = reachableSets.keySet().stream()
          .map(v -> computeInitState(dfa, v))
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

      Map<CombinedState, BDD> reachableIR = dirtyStates.stream()
          .collect(Collectors.toMap(v -> v, v -> reachableSets.get(v.stateExpr)));

      while (!dirtyStates.isEmpty()) {
        Set<CombinedState> newDirtyStates = new HashSet<>();

        dirtyStates.forEach(
            dirtyState -> {
              Map<StateExpr, Transition> dirtyStateEdges = edges.row(dirtyState.stateExpr);
              if (dirtyStateEdges == null) {
                // dirtyState has no edges
                return;
              }

              BDD dirtyStateBDD = reachableIR.get(dirtyState);
              dirtyStateEdges.forEach(
                  (neighbor, edge) -> {
                    BDD result = traverse.apply(edge, dirtyStateBDD);
                    if (result.isZero()) {
                      return;
                    }

                    // update DFA state according to input (if neighbor is a new node)
                    DfaState newDfaState = dirtyState.dfaState;
                    if (neighbor instanceof NodeAccept) {
                      Optional<DfaState> resultDfaState = dfa.transit(dirtyState.dfaState, neighbor);

                      // pruning if get null state (indicates invalid path)
                      if (!resultDfaState.isPresent()) {
                        return;
                      } else {
                        newDfaState = resultDfaState.get();
                      }
                    }

                    // pruning if DFA reaches a bad final state
                    if (newDfaState.isBad()) {
                      return;
                    }

                    CombinedState newState = new CombinedState(neighbor, newDfaState);
                    // update neighbor's reachable set
                    BDD oldReach = reachableIR.get(newState);
                    BDD newReach = oldReach == null ? result : oldReach.or(result);
                    if (oldReach == null || !oldReach.equals(newReach)) {
                      reachableIR.put(newState, newReach);
                      newDirtyStates.add(newState);
                    }
                  });
            });

        dirtyStates = newDirtyStates;
      }

      // convert IR to reachable sets
      Map<StateExpr, BDD> newReachableSets = reachableIR.entrySet().stream()
          .filter(kv -> kv.getKey().dfaState.isAccepted())
          .collect(Collectors.toMap(kv -> kv.getKey().stateExpr, Map.Entry::getValue, BDD::or));
      reachableSets.clear();
      reachableSets.putAll(newReachableSets);
    } finally {
      span.finish();
    }
  }

  private static Stream<NfaCombinedStateExpr> firstStep(Nfa nfa, StateExpr location) {
    List<NfaState> result;
    if (location instanceof NodeAccept) {
      result = nfa.transit(NfaState.startState(), ((NodeAccept) location).getHostname());
    } else {
      result = new ArrayList<>();
      result.add(NfaState.startState());
    }
    return result.stream()
        .map(nfaState -> new NfaCombinedStateExpr(location, nfaState));
  }

  private static Stream<NfaCombinedStateExpr> backwardFirstStep(Nfa nfa,
      NfaState acceptedState, StateExpr location) {
    List<NfaState> result;
    if (location instanceof NodeAccept) {
      result = nfa.transitBackwards(acceptedState, ((NodeAccept) location).getHostname());
    } else {
      result = new ArrayList<>();
      result.add(acceptedState);
    }
    return result.stream()
        .map(nfaState -> new NfaCombinedStateExpr(location, nfaState));
  }

  @VisibleForTesting
  static void nfaFixpoint(
      Map<StateExpr, BDD> reachableSets,
      Table<StateExpr, StateExpr, Transition> edges,
      Nfa nfa) {
    Span span = GlobalTracer.get().buildSpan("BDDReachabilityAnalysis.nfaFixpoint").start();
    try (Scope scope = GlobalTracer.get().scopeManager().activate(span)) {
      assert scope != null; // avoid unused warning

      Set<NfaCombinedStateExpr> dirtyStates = reachableSets.keySet().stream()
          .flatMap(v -> firstStep(nfa, v))
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

      Map<NfaCombinedStateExpr, BDD> fixpointIR = dirtyStates.stream()
          .collect(Collectors.toMap(v -> v, v -> reachableSets.get(v.stateExpr)));

      final BiFunction<Transition, BDD, BDD> forward = Transition::transitForward;
      while (!dirtyStates.isEmpty()) {
        Set<NfaCombinedStateExpr> newDirtyStates = new HashSet<>();

        dirtyStates.forEach(
            dirtyState -> {
              Map<StateExpr, Transition> dirtyStateEdges = edges.row(dirtyState.stateExpr);
              if (dirtyStateEdges == null) {
                // dirtyState has no edges
                return;
              }

              BDD dirtyStateBDD = fixpointIR.get(dirtyState);
              dirtyStateEdges.forEach(
                  (neighbor, edge) -> {
                    BDD result = forward.apply(edge, dirtyStateBDD);
                    if (result.isZero()) {
                      return;
                    }

                    // transit NFA state (to avoid duplicated transitions on single device,
                    // transit only at StateExpr subtype NodeAccept)
                    List<NfaState> newNfaStates;
                    if (neighbor instanceof NodeAccept) {
                      newNfaStates = nfa.transit(dirtyState.nfaState, ((NodeAccept) neighbor).getHostname());
                    } else {
                      newNfaStates = new ArrayList<>();
                      newNfaStates.add(dirtyState.nfaState);
                    }

                    // if newNfaStates is empty due to invalid path, the branch gets pruned
                    newNfaStates.forEach(
                        newNfaState -> {
                          NfaCombinedStateExpr newState = new NfaCombinedStateExpr(neighbor,
                              newNfaState);
                          // update neighbor's reachable set
                          BDD oldReach = fixpointIR.get(newState);
                          BDD newReach = oldReach == null ? result : oldReach.or(result);
                          if (oldReach == null || !oldReach.equals(newReach)) {
                            fixpointIR.put(newState, newReach);
                            newDirtyStates.add(newState);
                          }
                        }
                    );
                  });
            });

        dirtyStates = newDirtyStates;
      }

      // keep BDD with accepted states only
      Map<NfaCombinedStateExpr, BDD> acceptedFinalSets = fixpointIR.entrySet().stream()
          .filter(kv -> kv.getKey().nfaState.isAccepted())
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue, BDD::or));

      // propagate accepted final BDDs backwards
      final BiFunction<Transition, BDD, BDD> backward = Transition::transitBackward;
      Table<StateExpr, StateExpr, Transition> backwardEdges = transposeAndMaterialize(edges);

      Map<NfaCombinedStateExpr, BDD> acceptedSets = new HashMap<>();
      acceptedFinalSets.forEach(
          (acceptedState, set) -> {
            backwardFirstStep(nfa, acceptedState.nfaState, acceptedState.stateExpr)
                .forEach(
                    steppedState -> {
                      acceptedSets.put(steppedState, set);
                    }
                );
          }
      );

      dirtyStates = ImmutableSet.copyOf(acceptedSets.keySet());
      while (!dirtyStates.isEmpty()) {
        Set<NfaCombinedStateExpr> newDirtyStates = new HashSet<>();

        dirtyStates.forEach(
            dirtyState -> {
              Map<StateExpr, Transition> dirtyStateEdges = backwardEdges.row(dirtyState.stateExpr);
              if (dirtyStateEdges == null) {
                // dirtyState has no edges
                return;
              }

              BDD dirtyStateBDD = acceptedSets.get(dirtyState);
              dirtyStateEdges.forEach(
                  (neighbor, edge) -> {
                    BDD result = backward.apply(edge, dirtyStateBDD);
                    if (result.isZero()) {
                      return;
                    }

                    // transit NFA state (to avoid duplicated transitions on single device,
                    // transit only at StateExpr subtype NodeAccept)
                    List<NfaState> newNfaStates;
                    if (neighbor instanceof NodeAccept) {
                      newNfaStates = nfa.transitBackwards(dirtyState.nfaState, ((NodeAccept) neighbor).getHostname());
                    } else {
                      newNfaStates = new ArrayList<>();
                      newNfaStates.add(dirtyState.nfaState);
                    }

                    newNfaStates.forEach(
                        newNfaState -> {
                          NfaCombinedStateExpr newState = new NfaCombinedStateExpr(neighbor,
                              newNfaState);
                          // update neighbor's reachable set
                          BDD oldReach = acceptedSets.get(newState);
                          BDD newReach = oldReach == null ? result : oldReach.or(result);
                          if (oldReach == null || !oldReach.equals(newReach)) {
                            acceptedSets.put(newState, newReach);
                            newDirtyStates.add(newState);
                          }
                        }
                    );
                  });
            });

        dirtyStates = newDirtyStates;
      }

      // converting to result format, drop NFA states in entries
      Map<StateExpr, BDD> resultReachableSets = acceptedSets.entrySet().stream()
          .collect(Collectors.toMap(kv -> kv.getKey().stateExpr, Map.Entry::getValue, BDD::or));

      // store result
      reachableSets.clear();
      reachableSets.putAll(resultReachableSets);
    } finally {
      span.finish();
    }
  }

  @VisibleForTesting
  public static IngressLocation toIngressLocation(StateExpr stateExpr) {
    checkArgument(stateExpr instanceof OriginateVrf || stateExpr instanceof OriginateInterfaceLink);

    if (stateExpr instanceof OriginateVrf) {
      OriginateVrf originateVrf = (OriginateVrf) stateExpr;
      return IngressLocation.vrf(originateVrf.getHostname(), originateVrf.getVrf());
    } else {
      OriginateInterfaceLink originateInterfaceLink = (OriginateInterfaceLink) stateExpr;
      return IngressLocation.interfaceLink(
          originateInterfaceLink.getHostname(), originateInterfaceLink.getInterface());
    }
  }

  /**
   * Runs a fixpoint through the given graph backwards from the given states.
   *
   * <p>If this function will be called more than once on the same edge table, prefer {@link
   * #backwardFixpointTransposed(Table, Map)} on a transposed, materialized edge table (see {@link
   * BDDReachabilityUtils#transposeAndMaterialize(Table)}) to save redundant computations.
   */
  public static void backwardFixpoint(
      Table<StateExpr, StateExpr, Transition> forwardEdgeTable,
      Map<StateExpr, BDD> reverseReachable) {
    backwardFixpointTransposed(transposeAndMaterialize(forwardEdgeTable), reverseReachable);
  }

  /** See {@link #backwardFixpoint(Table, Map)}. */
  public static void backwardFixpointTransposed(
      Table<StateExpr, StateExpr, Transition> transposedEdgeTable,
      Map<StateExpr, BDD> reverseReachable) {
    fixpoint(reverseReachable, transposedEdgeTable, Transition::transitBackward);
  }

  /**
   * Returns an immutable copy of the input table that has been materialized in transposed form.
   *
   * <p>Use this instead of {@link Tables#transpose(Table)} if the result will be iterated on in
   * row-major order. Transposing the table alone does not change the row-major vs column-major
   * internal representation so the performance of row-oriented operations is abysmal. Instead, we
   * need to actually materialize the transposed representation.
   */
  public static <R, C, V> Table<C, R, V> transposeAndMaterialize(Table<R, C, V> edgeTable) {
    return ImmutableTable.copyOf(Tables.transpose(edgeTable));
  }

  public static void forwardFixpoint(
      Table<StateExpr, StateExpr, Transition> forwardEdgeTable, Map<StateExpr, BDD> reachable) {
    fixpoint(reachable, forwardEdgeTable, Transition::transitForward);
  }

  static Map<IngressLocation, BDD> getIngressLocationBdds(
      Map<StateExpr, BDD> stateReachableBdds, Set<StateExpr> ingressLocationStates, BDD zero) {
    return toImmutableMap(
        ingressLocationStates,
        BDDReachabilityUtils::toIngressLocation,
        stateExpr -> stateReachableBdds.getOrDefault(stateExpr, zero));
  }

  public static BDD computePortTransformationProtocolsBdd(BDDIpProtocol ipProtocol) {
    return AssignPortFromPool.PORT_TRANSFORMATION_PROTOCOLS.stream()
        .map(ipProtocol::value)
        .reduce(BDD::or)
        .get();
  }

  public static Set<Flow> constructFlows(BDDPacket pkt, Map<IngressLocation, BDD> reachableBdds) {
    return reachableBdds.entrySet().stream()
        .flatMap(
            entry -> {
              IngressLocation loc = entry.getKey();
              BDD headerSpace = entry.getValue();
              Optional<Builder> optionalFlow = pkt.getFlow(headerSpace);
              if (!optionalFlow.isPresent()) {
                return Stream.of();
              }
              Flow.Builder flow = optionalFlow.get();
              flow.setIngressNode(loc.getNode());
              switch (loc.getType()) {
                case INTERFACE_LINK:
                  flow.setIngressInterface(loc.getInterface());
                  break;
                case VRF:
                  flow.setIngressVrf(loc.getVrf());
                  break;
                default:
                  throw new BatfishException(
                      "Unexpected IngressLocation Type: " + loc.getType().name());
              }
              return Stream.of(flow.build());
            })
        .collect(ImmutableSet.toImmutableSet());
  }
}
