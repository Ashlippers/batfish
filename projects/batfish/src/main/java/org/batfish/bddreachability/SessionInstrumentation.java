package org.batfish.bddreachability;

import static com.google.common.base.Preconditions.checkArgument;
import static org.batfish.bddreachability.transition.Transitions.addLastHopConstraint;
import static org.batfish.bddreachability.transition.Transitions.addSourceInterfaceConstraint;
import static org.batfish.bddreachability.transition.Transitions.compose;
import static org.batfish.bddreachability.transition.Transitions.constraint;
import static org.batfish.bddreachability.transition.Transitions.removeLastHopConstraint;
import static org.batfish.bddreachability.transition.Transitions.removeSourceConstraint;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.bddreachability.transition.Transition;
import org.batfish.bddreachability.transition.Zero;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDSourceManager;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.FirewallSessionInterfaceInfo;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.flow.Accept;
import org.batfish.datamodel.flow.FibLookup;
import org.batfish.datamodel.flow.ForwardOutInterface;
import org.batfish.datamodel.flow.SessionAction;
import org.batfish.datamodel.visitors.SessionActionVisitor;
import org.batfish.symbolic.state.NodeAccept;
import org.batfish.symbolic.state.NodeDropAclIn;
import org.batfish.symbolic.state.NodeDropAclOut;
import org.batfish.symbolic.state.NodeInterfaceDeliveredToSubnet;
import org.batfish.symbolic.state.NodeInterfaceExitsNetwork;
import org.batfish.symbolic.state.NodeInterfaceInsufficientInfo;
import org.batfish.symbolic.state.NodeInterfaceNeighborUnreachable;
import org.batfish.symbolic.state.PostInVrfSession;
import org.batfish.symbolic.state.PreInInterface;
import org.batfish.symbolic.state.PreOutVrfSession;
import org.batfish.symbolic.state.StateExpr;

/**
 * Instruments a {@link BDDReachabilityAnalysis} graph to install {@link BDDFirewallSessionTraceInfo
 * initialized firewall sessions} for the return pass of a bidirectional reachablility analysis.
 * This consists of adding new edges to process session flows, and constraining some existing edges
 * to exclude session flows. In particular, if a router has a session for a particular flow, it must
 * use that session to process it (it cannot use the non-session pipeline for that flow). The "has
 * session" check occurs at the {@link PreInInterface} state. We add new out-edges from {@link
 * PreInInterface} for session flows, and constrain the preexisting out-edges (i.e. the non-session
 * pipeline) to exclude session flows.
 */
public class SessionInstrumentation {
  private final Map<String, Configuration> _configs;
  private final Map<String, BDDSourceManager> _srcMgrs;
  private final LastHopOutgoingInterfaceManager _lastHopMgr;

  // Can be null for ignoreFilters
  private final @Nullable Map<String, Map<String, Supplier<BDD>>> _filterBdds;

  private final BDD _one;

  @VisibleForTesting
  SessionInstrumentation(
      BDDPacket bddPacket,
      Map<String, Configuration> configs,
      Map<String, BDDSourceManager> srcMgrs,
      LastHopOutgoingInterfaceManager lastHopMgr,
      @Nullable Map<String, Map<String, Supplier<BDD>>> filterBdds) {
    _configs = configs;
    _srcMgrs = srcMgrs;
    _lastHopMgr = lastHopMgr;
    _filterBdds = filterBdds;
    _one = bddPacket.getFactory().one();
  }

  public static Stream<Edge> sessionInstrumentation(
      BDDPacket bddPacket,
      Map<String, Configuration> configs,
      Map<String, BDDSourceManager> srcMgrs,
      LastHopOutgoingInterfaceManager lastHopMgr,
      Map<String, Map<String, Supplier<BDD>>> filterBdds,
      Stream<Edge> originalEdges,
      Map<String, List<BDDFirewallSessionTraceInfo>> initializedSessions,
      BDDFibGenerator bddFibGenerator) {
    SessionInstrumentation instrumentation =
        new SessionInstrumentation(bddPacket, configs, srcMgrs, lastHopMgr, filterBdds);
    Stream<Edge> newEdges =
        Stream.concat(
            instrumentation.computeNewEdges(initializedSessions),
            // Create a fib subgraph for each node that has FibLookup sessions
            computeSessionFibLookupSubgraph(initializedSessions, bddFibGenerator));

    /* Instrument the original graph by adding an additional constraint to the out-edges from
     * PreInInterface. Those edges are for non-session flows, and the constraint ensures only
     * non-session flows can traverse those edges.
     */
    Stream<Edge> instrumentedEdges =
        constrainOutEdges(
            originalEdges, computeNonSessionPreInInterfaceOutEdgeConstraints(initializedSessions));

    return Streams.concat(newEdges, instrumentedEdges);
  }

  @Nonnull
  @VisibleForTesting
  static Stream<Edge> computeSessionFibLookupSubgraph(
      Map<String, List<BDDFirewallSessionTraceInfo>> initializedSessions,
      BDDFibGenerator bddFibGenerator) {
    Set<String> nodesWithSessionFibLookup = computeNodesWithFibLookup(initializedSessions);
    return bddFibGenerator.generateForwardingEdges(
        nodesWithSessionFibLookup::contains,
        PostInVrfSession::new,
        // egress ACLs / transformations are currently unsupported for FibLookup sessions, so skip
        // PreOutEdge and go straight to PreInInterface
        (host1, iface1, host2, iface2) -> new PreInInterface(host2, iface2),
        PreOutVrfSession::new,
        NodeInterfaceDeliveredToSubnet::new,
        NodeInterfaceExitsNetwork::new,
        NodeInterfaceInsufficientInfo::new,
        NodeInterfaceNeighborUnreachable::new);
  }

  @Nonnull
  private static Set<String> computeNodesWithFibLookup(
      Map<String, List<BDDFirewallSessionTraceInfo>> initializedSessions) {
    return initializedSessions.entrySet().stream()
        .filter(
            initializedSessionsEntry ->
                initializedSessionsEntry.getValue().stream()
                    .map(BDDFirewallSessionTraceInfo::getAction)
                    .anyMatch(FibLookup.class::isInstance))
        .map(Entry::getKey)
        .collect(ImmutableSet.toImmutableSet());
  }

  /** For each input edge, apply an additional constraint from the map if any. */
  @Nonnull
  private static Stream<Edge> constrainOutEdges(
      Stream<Edge> originalEdges, Map<StateExpr, BDD> existingOutEdgeConstraints) {
    return originalEdges.map(
        edge -> {
          BDD additionalConstraint = existingOutEdgeConstraints.get(edge.getPreState());
          return additionalConstraint == null
              ? edge
              : new Edge(
                  edge.getPreState(),
                  edge.getPostState(),
                  compose(constraint(additionalConstraint), edge.getTransition()));
        });
  }

  /**
   * For each incoming interface of all sessions, compute the non-session flow constraint that
   * should be applied to non-session out-edges from {@link PreInInterface}.
   */
  private static Map<StateExpr, BDD> computeNonSessionPreInInterfaceOutEdgeConstraints(
      Map<String, List<BDDFirewallSessionTraceInfo>> initializedSessions) {
    return initializedSessions.values().stream()
        .flatMap(Collection::stream)
        .flatMap(SessionInstrumentation::computeNonSessionPreInInterfaceOutEdgeConstraints)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, BDD::and));
  }

  /**
   * For each incoming interface of a session, compute the non-session flow constraint that should
   * be applied to non-session out-edges from {@link PreInInterface}.
   */
  private static Stream<Entry<StateExpr, BDD>> computeNonSessionPreInInterfaceOutEdgeConstraints(
      BDDFirewallSessionTraceInfo sessionInfo) {
    BDD nonSessionFlows = sessionInfo.getSessionFlows().not();
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            incomingIface -> {
              String hostname = sessionInfo.getHostname();
              return Maps.immutableEntry(
                  new PreInInterface(hostname, incomingIface), nonSessionFlows);
            });
  }

  /**
   * For each pair of (incoming interface, outgoing interface) of a session, add an edge for session
   * flows from the incoming interface's {@link PreInInterface} to each of the outgoing interface's
   * states corresponding to traffic successfully being forwarded out the node.
   */
  private Stream<Edge> computeNewEdges(
      Map<String, List<BDDFirewallSessionTraceInfo>> initializedSessions) {
    return initializedSessions.values().stream()
        .flatMap(Collection::stream)
        .flatMap(this::computeNewEdges);
  }

  @Nonnull
  private Stream<Edge> computeNewEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    return Streams.concat(
        computeNewSuccessEdges(sessionInfo),
        nodeDropAclInEdges(sessionInfo),
        nodeDropAclOutEdges(sessionInfo),
        postInVrfSessionEdges(sessionInfo));
  }

  @Nonnull
  private Stream<Edge> computeNewSuccessEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    return sessionInfo
        .getAction()
        .accept(
            new SessionActionVisitor<Stream<Edge>>() {
              @Override
              public Stream<Edge> visitAcceptVrf(Accept acceptVrf) {
                // The forward flow originated from the device, so the session delivers it to the
                // device.
                return nodeAcceptEdges(sessionInfo);
              }

              @Override
              public Stream<Edge> visitFibLookup(FibLookup fibLookup) {
                // Does not necessarily lead to success, so handled separately in
                // postInVrfSessionEdges
                return Stream.of();
              }

              @Override
              public Stream<Edge> visitForwardOutInterface(
                  ForwardOutInterface forwardOutInterface) {
                if (forwardOutInterface.getNextHop() == null) {
                  /* Forward query started with OriginateInterfaceLink(hostname, outIface). As long as the
                   * return flow reaches that interface link, we don't care what it's disposition might be.
                   * In every case we say the flow is successfully returned to the origination point. So we can
                   * use any disposition state for that interface link, and choose DELIVERED_TO_SUBNET
                   * arbitrarily.
                   */
                  return nodeInterfaceDeliveredToSubnetEdges(sessionInfo);
                } else {
                  return preInInterfaceEdges(sessionInfo);
                }
              }
            });
  }

  /** Produce PreInInterface->PostInVrfSession edges conditioned on sessionFlows BDD */
  private Stream<Edge> computePostInVrfSessionEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    SessionAction action = sessionInfo.getAction();
    checkArgument(
        action instanceof FibLookup,
        "Unsupported session action for PreInInterface->PostInVrfSession edge: %s",
        action);

    String hostname = sessionInfo.getHostname();
    BDD sessionFlows = sessionInfo.getSessionFlows();
    Map<String, Interface> ifaces = _configs.get(hostname).getAllInterfaces();
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            incomingInterface ->
                new Edge(
                    new PreInInterface(hostname, incomingInterface),
                    new PostInVrfSession(
                        hostname, ifaces.get(incomingInterface).getVrf().getName()),
                    sessionFlows));
  }

  @Nonnull
  @VisibleForTesting
  Stream<Edge> postInVrfSessionEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    return sessionInfo
        .getAction()
        .accept(
            new SessionActionVisitor<Stream<Edge>>() {
              @Override
              public Stream<Edge> visitAcceptVrf(Accept acceptVrf) {
                return Stream.of();
              }

              @Override
              public Stream<Edge> visitFibLookup(FibLookup fibLookup) {
                return computePostInVrfSessionEdges(sessionInfo);
              }

              @Override
              public Stream<Edge> visitForwardOutInterface(
                  ForwardOutInterface forwardOutInterface) {
                return Stream.of();
              }
            });
  }

  @VisibleForTesting
  Stream<Edge> preInInterfaceEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    SessionAction action = sessionInfo.getAction();
    checkArgument(
        action instanceof ForwardOutInterface,
        "Unsupported session action for PreInInterface->PreInInterface edge: %s",
        action);

    ForwardOutInterface forwardOutInterface = (ForwardOutInterface) action;
    NodeInterfacePair nextHop = forwardOutInterface.getNextHop();

    checkArgument(nextHop != null, "Not a PreInInterface session");

    String outIface = forwardOutInterface.getOutgoingInterface();
    String hostname = sessionInfo.getHostname();
    BDDSourceManager srcMgr = _srcMgrs.get(hostname);
    StateExpr postState = new PreInInterface(nextHop.getHostname(), nextHop.getInterface());

    Transition outAcl = constraint(getOutgoingSessionFilterBdd(hostname, outIface));

    Transition addSourceIfaceConstraint =
        addSourceInterfaceConstraint(_srcMgrs.get(nextHop.getHostname()), nextHop.getInterface());
    Transition addLastHop =
        addLastHopConstraint(
            _lastHopMgr, hostname, outIface, nextHop.getHostname(), nextHop.getInterface());
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            inIface -> {
              StateExpr preState = new PreInInterface(hostname, inIface);
              BDD sessionFlows = sessionInfo.getSessionFlows();
              BDD inAclBdd = getIncomingSessionFilterBdd(hostname, inIface);

              Transition transition =
                  compose(
                      constraint(sessionFlows.and(inAclBdd)),
                      sessionInfo.getTransformation(),
                      outAcl,
                      removeSourceConstraint(srcMgr),
                      removeLastHopConstraint(_lastHopMgr, hostname),
                      addSourceIfaceConstraint,
                      addLastHop);
              if (transition == Zero.INSTANCE) {
                return null;
              }
              return new Edge(preState, postState, transition);
            })
        .filter(Objects::nonNull);
  }

  @VisibleForTesting
  Stream<Edge> nodeDropAclOutEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    if (_filterBdds == null) {
      // ignoreAcls
      return Stream.of();
    }

    return sessionInfo
        .getAction()
        .accept(
            new SessionActionVisitor<Stream<Edge>>() {
              @Override
              public Stream<Edge> visitAcceptVrf(Accept acceptVrf) {
                return Stream.of();
              }

              @Override
              public Stream<Edge> visitFibLookup(FibLookup fibLookup) {
                return Stream.of();
              }

              @Override
              public Stream<Edge> visitForwardOutInterface(
                  ForwardOutInterface forwardOutInterface) {
                String outIface = forwardOutInterface.getOutgoingInterface();
                String hostname = sessionInfo.getHostname();
                BDDSourceManager srcMgr = _srcMgrs.get(hostname);
                StateExpr postState = new NodeDropAclOut(hostname);
                BDD sessionFlows = sessionInfo.getSessionFlows();

                Transition denyOutAcl =
                    constraint(getOutgoingSessionFilterBdd(hostname, outIface).not());

                return sessionInfo.getIncomingInterfaces().stream()
                    .map(
                        inIface -> {
                          StateExpr preState = new PreInInterface(hostname, inIface);
                          BDD inAclBdd = getIncomingSessionFilterBdd(hostname, inIface);

                          Transition transition =
                              compose(
                                  constraint(sessionFlows.and(inAclBdd)),
                                  sessionInfo.getTransformation(),
                                  denyOutAcl,
                                  removeSourceConstraint(srcMgr),
                                  removeLastHopConstraint(_lastHopMgr, hostname));
                          if (transition == Zero.INSTANCE) {
                            return null;
                          }
                          return new Edge(preState, postState, transition);
                        })
                    .filter(Objects::nonNull);
              }
            });
  }

  @VisibleForTesting
  Stream<Edge> nodeInterfaceDeliveredToSubnetEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    SessionAction action = sessionInfo.getAction();

    checkArgument(
        action instanceof ForwardOutInterface,
        "Unsupported session action for PreInInterface->NodeInterfaceDeliveredToSubnet edge: %s",
        action);

    ForwardOutInterface forwardOutInterface = (ForwardOutInterface) action;

    checkArgument(forwardOutInterface.getNextHop() == null, "Not a delivered to subnet session");

    String hostname = sessionInfo.getHostname();
    String outIface = forwardOutInterface.getOutgoingInterface();
    StateExpr postState = new NodeInterfaceDeliveredToSubnet(hostname, outIface);
    Transition outAcl = constraint(getOutgoingSessionFilterBdd(hostname, outIface));
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            inIface -> {
              StateExpr preState = new PreInInterface(hostname, inIface);
              BDD inAclBdd = getIncomingSessionFilterBdd(hostname, inIface);
              BDD sessionFlows = sessionInfo.getSessionFlows();

              /* Don't remove the source interface/last hop constraints here. They get removed in
               * the transition from NodeInterfaceDeliveredToSubnet to DeliveredToSubnet.
               */
              Transition transition =
                  compose(
                      constraint(sessionFlows.and(inAclBdd)),
                      sessionInfo.getTransformation(),
                      outAcl);
              if (transition == Zero.INSTANCE) {
                return null;
              }
              return new Edge(preState, postState, transition);
            })
        .filter(Objects::nonNull);
  }

  @VisibleForTesting
  Stream<Edge> nodeAcceptEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    SessionAction action = sessionInfo.getAction();
    checkArgument(
        action instanceof Accept,
        "Unsupported session action for PreInInterface->NodeAccept edge: %s",
        action);

    String hostname = sessionInfo.getHostname();
    BDDSourceManager srcMgr = _srcMgrs.get(hostname);

    StateExpr postState = new NodeAccept(hostname);
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            inIface -> {
              StateExpr preState = new PreInInterface(hostname, inIface);
              BDD inAclBdd = getIncomingSessionFilterBdd(hostname, inIface);

              Transition transition =
                  compose(
                      constraint(sessionInfo.getSessionFlows().and(inAclBdd)),
                      sessionInfo.getTransformation(),
                      removeSourceConstraint(srcMgr),
                      removeLastHopConstraint(_lastHopMgr, hostname));
              if (transition == Zero.INSTANCE) {
                return null;
              }
              return new Edge(preState, postState, transition);
            })
        .filter(Objects::nonNull);
  }

  @VisibleForTesting
  Stream<Edge> nodeDropAclInEdges(BDDFirewallSessionTraceInfo sessionInfo) {
    if (_filterBdds == null) {
      // ignoreAcls
      return Stream.of();
    }

    String hostname = sessionInfo.getHostname();
    BDDSourceManager srcMgr = _srcMgrs.get(hostname);

    StateExpr postState = new NodeDropAclIn(hostname);
    BDD sessionFlows = sessionInfo.getSessionFlows();
    return sessionInfo.getIncomingInterfaces().stream()
        .map(
            inIface -> {
              StateExpr preState = new PreInInterface(hostname, inIface);
              BDD inAclPermitBdd = getIncomingSessionFilterBdd(hostname, inIface);

              Transition transition =
                  compose(
                      constraint(sessionFlows.diff(inAclPermitBdd)),
                      removeSourceConstraint(srcMgr),
                      removeLastHopConstraint(_lastHopMgr, hostname));
              if (transition == Zero.INSTANCE) {
                return null;
              }
              return new Edge(preState, postState, transition);
            })
        .filter(Objects::nonNull);
  }

  private BDD getIncomingSessionFilterBdd(String hostname, String inIface) {
    return getSessionFilterBDD(hostname, inIface, FirewallSessionInterfaceInfo::getIncomingAclName);
  }

  private BDD getOutgoingSessionFilterBdd(String hostname, String outIface) {
    return getSessionFilterBDD(
        hostname, outIface, FirewallSessionInterfaceInfo::getOutgoingAclName);
  }

  private BDD getSessionFilterBDD(
      String hostname, String outIface, Function<FirewallSessionInterfaceInfo, String> getter) {
    if (_filterBdds == null) {
      // ignoreFilters
      return _one;
    }
    FirewallSessionInterfaceInfo sessionInfo =
        _configs.get(hostname).getAllInterfaces().get(outIface).getFirewallSessionInterfaceInfo();
    if (sessionInfo == null) {
      // interface does not have session configuration
      return _one;
    }
    String aclName = getter.apply(sessionInfo);
    return aclName == null ? _one : _filterBdds.get(hostname).get(aclName).get();
  }
}
