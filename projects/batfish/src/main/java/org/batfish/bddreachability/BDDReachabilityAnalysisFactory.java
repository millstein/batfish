package org.batfish.bddreachability;

import static org.batfish.common.util.CommonUtil.toImmutableMap;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchDst;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.common.BatfishException;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDSourceManager;
import org.batfish.common.bdd.IpAccessListToBDD;
import org.batfish.common.bdd.IpSpaceToBDD;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.SourceNat;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.specifier.InterfaceLinkLocation;
import org.batfish.specifier.InterfaceLocation;
import org.batfish.specifier.IpSpaceAssignment;
import org.batfish.specifier.Location;
import org.batfish.specifier.LocationVisitor;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.Accept;
import org.batfish.z3.state.DropAclIn;
import org.batfish.z3.state.DropAclOut;
import org.batfish.z3.state.DropNoRoute;
import org.batfish.z3.state.DropNullRoute;
import org.batfish.z3.state.NeighborUnreachable;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeDropAclIn;
import org.batfish.z3.state.NodeDropAclOut;
import org.batfish.z3.state.NodeDropNoRoute;
import org.batfish.z3.state.NodeDropNullRoute;
import org.batfish.z3.state.NodeInterfaceNeighborUnreachable;
import org.batfish.z3.state.OriginateInterfaceLink;
import org.batfish.z3.state.OriginateVrf;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreInInterface;
import org.batfish.z3.state.PreOutEdge;
import org.batfish.z3.state.PreOutEdgePostNat;
import org.batfish.z3.state.PreOutVrf;
import org.batfish.z3.state.Query;

/**
 * Constructs a the reachability graph for {@link BDDReachabilityAnalysis}. The graph is very
 * similar to the NOD programs generated by {@link
 * org.batfish.z3.state.visitors.DefaultTransitionGenerator}. The public API is very simple: it
 * provides two methods for constructing {@link BDDReachabilityAnalysis}, depending on whether or
 * not you have a destination Ip constraint.
 *
 * <p>The core of the implementation is the {@code generateEdges()} method and its many helpers,
 * which generate the {@link StateExpr nodes} and {@link Edge edges} of the reachability graph. Each
 * node represents a step of the routing process within some network device or between devices. The
 * edges represent the flow of traffic between these steps. Each edge is labeled with a {@link BDD}
 * that represents the set of packets that can traverse that edge. If the edge represents a source
 * NAT, the edge will be labeled with the NAT rules (match conditions and set of pool IPs).
 *
 * <p>To support {@link org.batfish.datamodel.acl.MatchSrcInterface} and {@link
 * org.batfish.datamodel.acl.OriginatingFromDevice} {@link
 * org.batfish.datamodel.acl.AclLineMatchExpr ACL expressions}, we maintain the invariant that
 * whenever a packet is inside a node, it has a valid source (according to the BDDSourceManager of
 * that node). For forward edges this is established by constraining to a single source. For
 * backward edges it's established using {@link BDDSourceManager#isValidValue}. When we exit the
 * node (e.g. forward into another node or a disposition state, or backward into another node or an
 * origination state), we erase the contraint on source by existential quantification.
 */
public final class BDDReachabilityAnalysisFactory {
  // node name --> acl name --> set of packets denied by the acl.
  private final Map<String, Map<String, BDD>> _aclDenyBDDs;

  // node name --> acl name --> set of packets permitted by the acl.
  private final Map<String, Map<String, BDD>> _aclPermitBDDs;

  /*
   * edge --> set of packets that will flow out the edge successfully, including that the
   * neighbor will respond to ARP.
   */
  private final Map<org.batfish.datamodel.Edge, BDD> _arpTrueEdgeBDDs;

  /*
   * Symbolic variables corresponding to the different packet header fields. We use these to
   * generate new BDD constraints on those fields. Each constraint can be understood as the set
   * of packet headers for which the constraint is satisfied.
   */
  private final BDDPacket _bddPacket;

  private final Map<String, BDDSourceManager> _bddSourceManagers;

  // node name -> node
  private final Map<String, Configuration> _configs;

  private final ForwardingAnalysis _forwardingAnalysis;

  private IpSpaceToBDD _dstIpSpaceToBDD;

  /*
   * node --> vrf --> interface --> set of packets that get routed out the interface but do not
   * reach the neighbor
   */
  private final Map<String, Map<String, Map<String, BDD>>> _neighborUnreachableBDDs;

  private final BDD _one;

  // node --> vrf --> set of packets routable by the vrf
  private final Map<String, Map<String, BDD>> _routableBDDs;

  // conjunction of the BDD vars encoding source IP. Used for existential quantification in source
  // NAT.
  private final BDD _sourceIpVars;

  // node --> vrf --> set of packets accepted by the vrf
  private final Map<String, Map<String, BDD>> _vrfAcceptBDDs;

  // node --> vrf --> set of packets not accepted by the vrf
  private final Map<String, Map<String, BDD>> _vrfNotAcceptBDDs;

  private BDD _zero;

  public BDDReachabilityAnalysisFactory(
      BDDPacket packet, Map<String, Configuration> configs, ForwardingAnalysis forwardingAnalysis) {
    _bddPacket = packet;
    _one = packet.getFactory().one();
    _zero = packet.getFactory().zero();
    _bddSourceManagers = BDDSourceManager.forNetwork(_bddPacket, configs);
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _dstIpSpaceToBDD = new IpSpaceToBDD(_bddPacket.getFactory(), _bddPacket.getDstIp());

    _aclPermitBDDs = computeAclBDDs(_bddPacket, _bddSourceManagers, configs);
    _aclDenyBDDs = computeAclDenyBDDs(_aclPermitBDDs);

    _arpTrueEdgeBDDs = computeArpTrueEdgeBDDs(forwardingAnalysis, _dstIpSpaceToBDD);
    _neighborUnreachableBDDs = computeNeighborUnreachableBDDs(forwardingAnalysis, _dstIpSpaceToBDD);
    _routableBDDs = computeRoutableBDDs(forwardingAnalysis, _dstIpSpaceToBDD);
    _vrfAcceptBDDs = computeVrfAcceptBDDs(configs, _dstIpSpaceToBDD);
    _vrfNotAcceptBDDs = computeVrfNotAcceptBDDs(_vrfAcceptBDDs);

    _sourceIpVars = Arrays.stream(_bddPacket.getSrcIp().getBitvec()).reduce(_one, BDD::and);
  }

  private static Map<String, Map<String, BDD>> computeAclBDDs(
      BDDPacket bddPacket,
      Map<String, BDDSourceManager> bddSourceManagers,
      Map<String, Configuration> configs) {
    return toImmutableMap(
        configs,
        Entry::getKey,
        nodeEntry -> {
          Configuration config = nodeEntry.getValue();
          IpAccessListToBDD aclToBdd =
              IpAccessListToBDD.create(
                  bddPacket,
                  bddSourceManagers.get(config.getHostname()),
                  config.getIpAccessLists(),
                  config.getIpSpaces());
          return toImmutableMap(
              config.getIpAccessLists(),
              Entry::getKey,
              aclEntry -> aclToBdd.toBdd(aclEntry.getValue()));
        });
  }

  private static Map<String, Map<String, BDD>> computeAclDenyBDDs(
      Map<String, Map<String, BDD>> aclBDDs) {
    return toImmutableMap(
        aclBDDs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(), Entry::getKey, aclEntry -> aclEntry.getValue().not()));
  }

  private static Map<StateExpr, Map<StateExpr, Edge>> computeEdges(Stream<Edge> edgeStream) {
    Map<StateExpr, Map<StateExpr, Edge>> edges = new HashMap<>();

    edgeStream.forEach(
        edge ->
            edges
                .computeIfAbsent(edge.getPreState(), k -> new HashMap<>())
                .put(edge.getPostState(), edge));

    // freeze
    return toImmutableMap(
        edges,
        Entry::getKey,
        preStateEntry -> toImmutableMap(preStateEntry.getValue(), Entry::getKey, Entry::getValue));
  }

  private static Map<String, Map<String, BDD>> computeRoutableBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getRoutableIps(),
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry -> vrfEntry.getValue().accept(ipSpaceToBDD)));
  }

  private static Map<String, Map<String, BDD>> computeVrfNotAcceptBDDs(
      Map<String, Map<String, BDD>> vrfAcceptBDDs) {
    return toImmutableMap(
        vrfAcceptBDDs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(), Entry::getKey, vrfEntry -> vrfEntry.getValue().not()));
  }

  IpSpaceToBDD getIpSpaceToBDD() {
    return _dstIpSpaceToBDD;
  }

  Map<String, Map<String, BDD>> getVrfAcceptBDDs() {
    return _vrfAcceptBDDs;
  }

  private static Map<org.batfish.datamodel.Edge, BDD> computeArpTrueEdgeBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getArpTrueEdge(),
        Entry::getKey,
        entry -> entry.getValue().accept(ipSpaceToBDD));
  }

  private static Map<String, Map<String, Map<String, BDD>>> computeNeighborUnreachableBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getNeighborUnreachable(),
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry ->
                    toImmutableMap(
                        vrfEntry.getValue(),
                        Entry::getKey,
                        ifaceEntry -> ifaceEntry.getValue().accept(ipSpaceToBDD))));
  }

  private Stream<Edge> generateRootEdges(Map<StateExpr, BDD> rootBdds) {
    return Streams.concat(
        generateRootEdges_OriginateInterfaceLink_PreInInterface(rootBdds),
        generateRootEdges_OriginateVrf_PostInVrf(rootBdds));
  }

  private Stream<Edge> generateQueryEdges(Set<FlowDisposition> actions) {
    return actions
        .stream()
        .map(
            action -> {
              switch (action) {
                case ACCEPTED:
                  return new Edge(Accept.INSTANCE, Query.INSTANCE, _one);
                case DENIED_IN:
                  return new Edge(DropAclIn.INSTANCE, Query.INSTANCE, _one);
                case DENIED_OUT:
                  return new Edge(DropAclOut.INSTANCE, Query.INSTANCE, _one);
                case LOOP:
                  throw new BatfishException("FlowDisposition LOOP is unsupported");
                case NEIGHBOR_UNREACHABLE_OR_EXITS_NETWORK:
                  return new Edge(NeighborUnreachable.INSTANCE, Query.INSTANCE, _one);
                case NO_ROUTE:
                  return new Edge(DropNoRoute.INSTANCE, Query.INSTANCE, _one);
                case NULL_ROUTED:
                  return new Edge(DropNullRoute.INSTANCE, Query.INSTANCE, _one);
                default:
                  throw new BatfishException("Unknown FlowDisposition " + action.toString());
              }
            });
  }

  private Stream<Edge> generateRootEdges_OriginateInterfaceLink_PreInInterface(
      Map<StateExpr, BDD> rootBdds) {
    return rootBdds
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() instanceof OriginateInterfaceLink)
        .map(
            entry -> {
              OriginateInterfaceLink originateInterfaceLink =
                  (OriginateInterfaceLink) entry.getKey();
              String hostname = originateInterfaceLink.getHostname();
              String iface = originateInterfaceLink.getIface();
              PreInInterface preInInterface = new PreInInterface(hostname, iface);

              BDD rootBdd = entry.getValue();
              BDD sourceConstraint = _bddSourceManagers.get(hostname).getSourceInterfaceBDD(iface);
              BDD constraint = rootBdd.and(sourceConstraint);
              return new Edge(
                  originateInterfaceLink,
                  preInInterface,
                  eraseSourceAfter(constraint, hostname),
                  constraint::and);
            });
  }

  private Stream<Edge> generateRootEdges_OriginateVrf_PostInVrf(Map<StateExpr, BDD> rootBdds) {
    return rootBdds
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() instanceof OriginateVrf)
        .map(
            entry -> {
              OriginateVrf originateVrf = (OriginateVrf) entry.getKey();
              String hostname = originateVrf.getHostname();
              String vrf = originateVrf.getVrf();
              PostInVrf postInVrf = new PostInVrf(hostname, vrf);
              BDD rootBdd = entry.getValue();
              BDD sourceConstraint = _bddSourceManagers.get(hostname).getOriginatingFromDeviceBDD();
              BDD constraint = rootBdd.and(sourceConstraint);
              return new Edge(
                  originateVrf, postInVrf, eraseSourceAfter(constraint, hostname), constraint::and);
            });
  }

  /*
   * These edges do not depend on the query. Compute them separately so that we can later cache them
   * across queries if we want to.
   */
  private Stream<Edge> generateEdges(Set<String> finalNodes) {
    return Streams.concat(
        generateRules_NodeAccept_Accept(finalNodes),
        generateRules_NodeDropAclIn_DropAclIn(finalNodes),
        generateRules_NodeDropNoRoute_DropNoRoute(finalNodes),
        generateRules_NodeDropNullRoute_DropNullRoute(finalNodes),
        generateRules_NodeDropAclOut_DropAclOut(finalNodes),
        generateRules_NodeInterfaceNeighborUnreachable_NeighborUnreachable(finalNodes),
        generateRules_PreInInterface_NodeDropAclIn(),
        generateRules_PreInInterface_PostInVrf(),
        generateRules_PostInVrf_NodeAccept(),
        generateRules_PostInVrf_NodeDropNoRoute(),
        generateRules_PostInVrf_PreOutVrf(),
        generateRules_PreOutEdge_PreOutEdgePostNat(),
        generateRules_PreOutEdgePostNat_NodeDropAclOut(),
        generateRules_PreOutEdgePostNat_PreInInterface(),
        generateRules_PreOutVrf_NodeDropAclOut(),
        generateRules_PreOutVrf_NodeDropNullRoute(),
        generateRules_PreOutVrf_NodeInterfaceNeighborUnreachable(),
        generateRules_PreOutVrf_PreOutEdge());
  }

  private Stream<Edge> generateRules_NodeAccept_Accept(Set<String> finalNodes) {
    return finalNodes.stream().map(node -> new Edge(new NodeAccept(node), Accept.INSTANCE, _one));
  }

  private Stream<Edge> generateRules_NodeDropAclIn_DropAclIn(Set<String> finalNodes) {
    return finalNodes
        .stream()
        .map(node -> new Edge(new NodeDropAclIn(node), DropAclIn.INSTANCE, _one));
  }

  private Stream<Edge> generateRules_NodeDropAclOut_DropAclOut(Set<String> finalNodes) {
    return finalNodes
        .stream()
        .map(node -> new Edge(new NodeDropAclOut(node), DropAclOut.INSTANCE, _one));
  }

  private Stream<Edge> generateRules_NodeDropNoRoute_DropNoRoute(Set<String> finalNodes) {
    return finalNodes
        .stream()
        .map(node -> new Edge(new NodeDropNoRoute(node), DropNoRoute.INSTANCE, _one));
  }

  private Stream<Edge> generateRules_NodeDropNullRoute_DropNullRoute(Set<String> finalNodes) {
    return finalNodes
        .stream()
        .map(node -> new Edge(new NodeDropNullRoute(node), DropNullRoute.INSTANCE, _one));
  }

  private Stream<Edge> generateRules_NodeInterfaceNeighborUnreachable_NeighborUnreachable(
      Set<String> finalNodes) {
    return finalNodes
        .stream()
        .map(_configs::get)
        .flatMap(c -> c.getAllInterfaces().values().stream())
        .map(
            iface -> {
              String nodeNode = iface.getOwner().getHostname();
              String ifaceName = iface.getName();
              return new Edge(
                  new NodeInterfaceNeighborUnreachable(nodeNode, ifaceName),
                  NeighborUnreachable.INSTANCE,
                  _one);
            });
  }

  private Stream<Edge> generateRules_PostInVrf_NodeAccept() {
    return _vrfAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD acceptBDD = vrfEntry.getValue();
                          return new Edge(
                              new PostInVrf(node, vrf),
                              new NodeAccept(node),
                              validSource(acceptBDD, node),
                              eraseSourceAfter(acceptBDD, node));
                        }));
  }

  private Stream<Edge> generateRules_PostInVrf_NodeDropNoRoute() {
    return _vrfNotAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD notAcceptBDD = vrfEntry.getValue();
                          BDD notRoutableBDD = _routableBDDs.get(node).get(vrf).not();
                          BDD edgeBdd = notAcceptBDD.and(notRoutableBDD);
                          return new Edge(
                              new PostInVrf(node, vrf),
                              new NodeDropNoRoute(node),
                              validSource(edgeBdd, node),
                              eraseSourceAfter(edgeBdd, node));
                        }));
  }

  private Stream<Edge> generateRules_PostInVrf_PreOutVrf() {
    return _vrfNotAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD notAcceptBDD = vrfEntry.getValue();
                          BDD routableBDD = _routableBDDs.get(node).get(vrf);
                          return new Edge(
                              new PostInVrf(node, vrf),
                              new PreOutVrf(node, vrf),
                              notAcceptBDD.and(routableBDD));
                        }));
  }

  private Stream<Edge> generateRules_PreInInterface_NodeDropAclIn() {
    return _configs
        .values()
        .stream()
        .map(Configuration::getVrfs)
        .map(Map::values)
        .flatMap(Collection::stream)
        .flatMap(vrf -> vrf.getInterfaces().values().stream())
        .filter(iface -> iface.getIncomingFilter() != null)
        .map(
            i -> {
              String acl = i.getIncomingFilterName();
              String node = i.getOwner().getHostname();
              String iface = i.getName();

              BDD aclDenyBDD = _aclDenyBDDs.get(node).get(acl);
              return new Edge(
                  new PreInInterface(node, iface),
                  new NodeDropAclIn(node),
                  validSource(aclDenyBDD, node),
                  eraseSourceAfter(aclDenyBDD, node));
            });
  }

  private Stream<Edge> generateRules_PreInInterface_PostInVrf() {
    return _configs
        .values()
        .stream()
        .map(Configuration::getVrfs)
        .map(Map::values)
        .flatMap(Collection::stream)
        .flatMap(vrf -> vrf.getInterfaces().values().stream())
        .map(
            iface -> {
              String aclName = iface.getIncomingFilterName();
              String nodeName = iface.getOwner().getHostname();
              String vrfName = iface.getVrfName();
              String ifaceName = iface.getName();

              BDD inAclBDD = aclName == null ? _one : _aclPermitBDDs.get(nodeName).get(aclName);
              return new Edge(
                  new PreInInterface(nodeName, ifaceName),
                  new PostInVrf(nodeName, vrfName),
                  inAclBDD);
            });
  }

  private Stream<Edge> generateRules_PreOutEdge_PreOutEdgePostNat() {
    return _forwardingAnalysis
        .getArpTrueEdge()
        .keySet()
        .stream()
        .map(
            edge -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              PreOutEdge preOutEdge = new PreOutEdge(node1, iface1, node2, iface2);
              PreOutEdgePostNat preOutEdgePostNat =
                  new PreOutEdgePostNat(node1, iface1, node2, iface2);

              List<SourceNat> sourceNats =
                  _configs.get(node1).getAllInterfaces().get(iface1).getSourceNats();

              if (sourceNats == null) {
                return new Edge(preOutEdge, preOutEdgePostNat, _one);
              }

              List<BDDSourceNat> bddSourceNats =
                  sourceNats
                      .stream()
                      .map(
                          sourceNat -> {
                            String aclName = sourceNat.getAcl().getName();
                            BDD match = _aclPermitBDDs.get(node1).get(aclName);
                            BDD setSrcIp =
                                _bddPacket
                                    .getSrcIp()
                                    .geq(sourceNat.getPoolIpFirst().asLong())
                                    .and(
                                        _bddPacket
                                            .getSrcIp()
                                            .leq(sourceNat.getPoolIpLast().asLong()));
                            return new BDDSourceNat(match, setSrcIp);
                          })
                      .collect(ImmutableList.toImmutableList());

              return new Edge(
                  preOutEdge,
                  preOutEdgePostNat,
                  sourceNatBackwardEdge(bddSourceNats),
                  sourceNatForwardEdge(bddSourceNats));
            });
  }

  private Stream<Edge> generateRules_PreOutEdgePostNat_NodeDropAclOut() {
    return _forwardingAnalysis
        .getArpTrueEdge()
        .keySet()
        .stream()
        .flatMap(
            edge -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              String aclName =
                  _configs.get(node1).getAllInterfaces().get(iface1).getOutgoingFilterName();
              BDD aclDenyBDD = _aclDenyBDDs.get(node1).get(aclName);

              return aclDenyBDD != null
                  ? Stream.of(
                      new Edge(
                          new PreOutEdgePostNat(node1, iface1, node2, iface2),
                          new NodeDropAclOut(node1),
                          validSource(aclDenyBDD, node1),
                          eraseSourceAfter(aclDenyBDD, node1)))
                  : Stream.of();
            });
  }

  private Stream<Edge> generateRules_PreOutEdgePostNat_PreInInterface() {
    return _forwardingAnalysis
        .getArpTrueEdge()
        .keySet()
        .stream()
        .map(
            edge -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              String aclName =
                  _configs.get(node1).getAllInterfaces().get(iface1).getOutgoingFilterName();
              BDD aclPermitBDD = aclName == null ? _one : _aclPermitBDDs.get(node1).get(aclName);
              assert aclPermitBDD != null;

              return new Edge(
                  new PreOutEdgePostNat(node1, iface1, node2, iface2),
                  new PreInInterface(node2, iface2),
                  preInInterfaceBackward(aclPermitBDD, node1, node2, iface2),
                  preInInterfaceForward(aclPermitBDD, node2, iface2));
            });
  }

  private Stream<Edge> generateRules_PreOutVrf_NodeDropAclOut() {
    return _neighborUnreachableBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry -> {
              String node = nodeEntry.getKey();
              return nodeEntry
                  .getValue()
                  .entrySet()
                  .stream()
                  .flatMap(
                      vrfEntry -> {
                        String vrf = vrfEntry.getKey();
                        return vrfEntry
                            .getValue()
                            .entrySet()
                            .stream()
                            .flatMap(
                                ifaceEntry -> {
                                  String iface = ifaceEntry.getKey();
                                  BDD ipSpaceBDD = ifaceEntry.getValue();
                                  String outAcl =
                                      _configs
                                          .get(node)
                                          .getAllInterfaces()
                                          .get(iface)
                                          .getOutgoingFilterName();
                                  BDD outAclDenyBDD =
                                      outAcl == null ? _zero : _aclDenyBDDs.get(node).get(outAcl);
                                  BDD edgeBdd = ipSpaceBDD.and(outAclDenyBDD);

                                  return edgeBdd.isZero()
                                      ? Stream.of()
                                      : Stream.of(
                                          new Edge(
                                              new PreOutVrf(node, vrf),
                                              new NodeDropAclOut(node),
                                              edgeBdd::and,
                                              eraseSourceAfter(edgeBdd, node)));
                                });
                      });
            });
  }

  private Stream<Edge> generateRules_PreOutVrf_NodeDropNullRoute() {
    return _forwardingAnalysis
        .getNullRoutedIps()
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD nullRoutedBDD = vrfEntry.getValue().accept(_dstIpSpaceToBDD);
                          return new Edge(
                              new PreOutVrf(node, vrf),
                              new NodeDropNullRoute(node),
                              nullRoutedBDD::and,
                              eraseSourceAfter(nullRoutedBDD, node));
                        }));
  }

  private Stream<Edge> generateRules_PreOutVrf_NodeInterfaceNeighborUnreachable() {
    return _neighborUnreachableBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry -> {
              String node = nodeEntry.getKey();
              return nodeEntry
                  .getValue()
                  .entrySet()
                  .stream()
                  .flatMap(
                      vrfEntry -> {
                        String vrf = vrfEntry.getKey();
                        return vrfEntry
                            .getValue()
                            .entrySet()
                            .stream()
                            .flatMap(
                                ifaceEntry -> {
                                  String iface = ifaceEntry.getKey();
                                  BDD ipSpaceBDD = ifaceEntry.getValue();
                                  String outAcl =
                                      _configs
                                          .get(node)
                                          .getAllInterfaces()
                                          .get(iface)
                                          .getOutgoingFilterName();
                                  BDD outAclPermitBDD =
                                      outAcl == null ? _one : _aclPermitBDDs.get(node).get(outAcl);
                                  BDD edgeBdd = ipSpaceBDD.and(outAclPermitBDD);

                                  return edgeBdd.isZero()
                                      ? Stream.of()
                                      : Stream.of(
                                          new Edge(
                                              new PreOutVrf(node, vrf),
                                              new NodeInterfaceNeighborUnreachable(node, iface),
                                              edgeBdd::and,
                                              eraseSourceAfter(edgeBdd, node)));
                                });
                      });
            });
  }

  private Stream<Edge> generateRules_PreOutVrf_PreOutEdge() {
    return _arpTrueEdgeBDDs
        .entrySet()
        .stream()
        .map(
            entry -> {
              org.batfish.datamodel.Edge edge = entry.getKey();
              BDD arpTrue = entry.getValue();

              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String vrf1 = ifaceVrf(edge.getNode1(), edge.getInt1());
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              return new Edge(
                  new PreOutVrf(node1, vrf1),
                  new PreOutEdge(node1, iface1, node2, iface2),
                  arpTrue);
            });
  }

  @Nonnull
  private LocationVisitor<StateExpr> getLocationToStateExpr() {
    return new LocationVisitor<StateExpr>() {
      @Override
      public StateExpr visitInterfaceLinkLocation(
          @Nonnull InterfaceLinkLocation interfaceLinkLocation) {
        return new OriginateInterfaceLink(
            interfaceLinkLocation.getNodeName(), interfaceLinkLocation.getInterfaceName());
      }

      @Override
      public StateExpr visitInterfaceLocation(@Nonnull InterfaceLocation interfaceLocation) {
        String vrf =
            _configs
                .get(interfaceLocation.getNodeName())
                .getAllInterfaces()
                .get(interfaceLocation.getInterfaceName())
                .getVrf()
                .getName();
        return new OriginateVrf(interfaceLocation.getNodeName(), vrf);
      }
    };
  }

  public BDDReachabilityAnalysis bddReachabilityAnalysis(IpSpaceAssignment srcIpSpaceAssignment) {
    return bddReachabilityAnalysis(
        srcIpSpaceAssignment,
        UniverseIpSpace.INSTANCE,
        _configs.keySet(),
        ImmutableSet.of(FlowDisposition.ACCEPTED));
  }

  public BDDReachabilityAnalysis bddReachabilityAnalysis(
      IpSpaceAssignment srcIpSpaceAssignment,
      IpSpace dstIpSpace,
      Set<String> finalNodes,
      Set<FlowDisposition> actions) {
    return bddReachabilityAnalysis(srcIpSpaceAssignment, matchDst(dstIpSpace), finalNodes, actions);
  }

  public BDDReachabilityAnalysis bddReachabilityAnalysis(
      IpSpaceAssignment srcIpSpaceAssignment,
      AclLineMatchExpr dstHeaderSpace,
      @Nonnull Set<String> finalNodes,
      Set<FlowDisposition> actions) {
    Map<StateExpr, BDD> roots = new HashMap<>();
    BDD dstIpSpaceBDD =
        IpAccessListToBDD.create(_bddPacket, ImmutableMap.of(), ImmutableMap.of())
            .visit(dstHeaderSpace);

    IpSpaceToBDD srcIpSpaceToBDD = new IpSpaceToBDD(_bddPacket.getFactory(), _bddPacket.getSrcIp());
    for (IpSpaceAssignment.Entry entry : srcIpSpaceAssignment.getEntries()) {
      BDD srcIpSpaceBDD = entry.getIpSpace().accept(srcIpSpaceToBDD);
      BDD headerspaceBDD = srcIpSpaceBDD.and(dstIpSpaceBDD);
      for (Location loc : entry.getLocations()) {
        StateExpr root = loc.accept(getLocationToStateExpr());
        roots.put(root, headerspaceBDD);
      }
    }

    Map<StateExpr, Map<StateExpr, Edge>> edges =
        computeEdges(
            Streams.concat(
                generateEdges(finalNodes), generateRootEdges(roots), generateQueryEdges(actions)));

    return new BDDReachabilityAnalysis(_bddPacket, roots.keySet(), edges);
  }

  private String ifaceVrf(String node, String iface) {
    return _configs.get(node).getAllInterfaces().get(iface).getVrfName();
  }

  private static Map<String, Map<String, BDD>> computeVrfAcceptBDDs(
      Map<String, Configuration> configs, IpSpaceToBDD ipSpaceToBDD) {
    Map<String, Map<String, IpSpace>> vrfOwnedIpSpaces =
        CommonUtil.computeVrfOwnedIpSpaces(
            CommonUtil.computeIpVrfOwners(false, CommonUtil.computeNodeInterfaces(configs)));

    return CommonUtil.toImmutableMap(
        vrfOwnedIpSpaces,
        Entry::getKey,
        nodeEntry ->
            CommonUtil.toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry -> vrfEntry.getValue().accept(ipSpaceToBDD)));
  }

  /*
   * Used for backward edges from disposition states into the router.
   */
  private Function<BDD, BDD> validSource(BDD constraint, String node) {
    return _bddSourceManagers.get(node).isValidValue().and(constraint)::and;
  }

  /*
   * Whenever the packet is inside the router, we track its source in the source variable. We erase
   * the variable when the packet is sent to another router (in which case it is immediately reset
   * to a new value for the other router), or when the flow stops (enters some state related to a
   * disposition), in which case the value is left undefined. This method is used in the latter
   * (flow stops) case.
   */
  private Function<BDD, BDD> eraseSourceAfter(BDD constraint, String node) {
    BDDSourceManager mgr = _bddSourceManagers.get(node);
    // check the constraint (which may reference the source), then erase the source by existential
    // quantification
    return orig -> mgr.existsSource(orig.and(constraint));
  }

  private Function<BDD, BDD> preInInterfaceForward(BDD constraint, String node, String iface) {
    BDDSourceManager mgr = _bddSourceManagers.get(node);
    BDD ifaceBdd = mgr.getSourceInterfaceBDD(iface);
    // 1. apply the constraint
    // 2. existentially quantify away the previous node's source,
    // 3. add the next node's source constraint.
    return orig -> mgr.existsSource(orig.and(constraint)).and(ifaceBdd);
  }

  private Function<BDD, BDD> preInInterfaceBackward(
      BDD constraint, String exitNode, String enterNode, String enterIface) {
    BDD exitNodeValidSource = _bddSourceManagers.get(exitNode).isValidValue();
    BDDSourceManager enterSrcMgr = _bddSourceManagers.get(enterNode);
    BDD ifaceBdd = enterSrcMgr.getSourceInterfaceBDD(enterIface);
    BDD exitNodeBdd = constraint.and(exitNodeValidSource);

    // 3. add the next node's source constraint.
    // 2. existentially quantify away the previous node's source,
    // 1. constrain the source variable to be a valid value for the exit node and apply the
    //    constraint
    return exitNodeBdd.isZero()
        ? orig -> _bddPacket.getFactory().zero()
        : orig -> enterSrcMgr.existsSource(orig.and(ifaceBdd)).and(exitNodeBdd);
  }

  private Function<BDD, BDD> sourceNatForwardEdge(List<BDDSourceNat> sourceNats) {
    return orig -> {
      BDD remaining = orig;
      BDD result = orig.getFactory().zero();
      for (BDDSourceNat sourceNat : sourceNats) {
        /*
         * Check the condition, then set source IP (by existentially quantifying away the old value,
         * then ANDing on the new value.
         */
        BDD natted =
            remaining.and(sourceNat._condition).exist(_sourceIpVars).and(sourceNat._updateSrcIp);
        result = result.or(natted);
        remaining = remaining.and(sourceNat._condition.not());
      }
      result = result.or(remaining);
      return result;
    };
  }

  private Function<BDD, BDD> sourceNatBackwardEdge(@Nonnull List<BDDSourceNat> sourceNats) {
    return orig -> {
      BDD origExistSrcIp = orig.exist(_sourceIpVars);
      // non-natted case: srcIp unchanged, none of the lines match
      BDD result =
          sourceNats.stream().map(srcNat -> srcNat._condition.not()).reduce(orig, BDD::and);
      // natted cases
      for (BDDSourceNat sourceNat : sourceNats) {
        if (!orig.and(sourceNat._updateSrcIp).isZero()) {
          // this could be the NAT rule that was applied
          result = result.or(origExistSrcIp.and(sourceNat._condition));
        }
      }
      return result;
    };
  }

  public Map<String, BDDSourceManager> getBDDSourceManagers() {
    return _bddSourceManagers;
  }

  public Map<String, Map<String, Map<String, BDD>>> getNeighborUnreachableBDDs() {
    return _neighborUnreachableBDDs;
  }
}
