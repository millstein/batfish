package batfish.logicblox;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class Facts {

   private  static final Map<String, String> _CONTROL_PLANE_FACT_COLUMN_HEADERS = new TreeMap<String, String> ();
   private static final Map<String, String> _TRAFFIC_FACT_COLUMN_HEADERS = new TreeMap<String, String>();
   public static final Map<String, String> CONTROL_PLANE_FACT_COLUMN_HEADERS = Collections.unmodifiableMap(_CONTROL_PLANE_FACT_COLUMN_HEADERS);
   public static final Map<String, String> TRAFFIC_FACT_COLUMN_HEADERS = Collections.unmodifiableMap(_TRAFFIC_FACT_COLUMN_HEADERS);

   static {
      _TRAFFIC_FACT_COLUMN_HEADERS.put("DuplicateRoleFlows", "DUMMY");
      _TRAFFIC_FACT_COLUMN_HEADERS.put("SetFlowOriginate", "NODE|SRCIP|DSTIP|SRCPORT|DSTPORT|IPPROTOCOL");

      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetFakeInterface", "NODE|INTERFACE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetFlowSinkInterface", "NODE|INTERFACE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("GuessTopology", "DUMMY");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SamePhysicalSegment", "NODE1|INTERFACE1|NODE2|INTERFACE2");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetSwitchportAccess", "SWITCH|INTERFACE|VLAN");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetSwitchportTrunkAllows", "SWITCH|INTERFACE|VLANSTART|VLANEND");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetSwitchportTrunkEncapsulation", "SWITCH|INTERFACE|ENCAPSULATION");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetSwitchportTrunkNative", "SWITCH|INTERFACE|VLAN");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetVlanInterface", "NODE|INTERFACE|VLAN");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetInterfaceFilterIn", "NODE|INTERFACE|FILTER");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetInterfaceFilterOut", "NODE|INTERFACE|FILTER");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetInterfaceRoutingPolicy", "NODE|INTERFACE|POLICY");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetNetwork", "STARTIP|START|END|PREFIXLENGTH");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetIpAccessListDenyLine", "LIST|LINE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetIpAccessListLine", "LIST|LINE|PROTOCOL|SRCIPSTART|SRCIPEND|DSTIPSTART|DSTIPEND");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetIpAccessListLine_dstPortRange", "LIST|LINE|DSTPORTSTART|DSTPORTEND");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetIpAccessListLine_srcPortRange", "LIST|LINE|SRCPORTSTART|SRCPORTEND");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetActiveInt", "NODE|INTERFACE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetIpInt", "NODE|INTERFACE|IP|PREFIXLENGTH");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetLinkLoadLimitIn", "NODE|INTERFACE|LIMIT");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetLinkLoadLimitOut", "NODE|INTERFACE|LIMIT");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetGeneratedRoute_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|ADMIN");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetGeneratedRoutePolicy_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetStaticRoute_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|NEXTHOPIP|ADMIN|TAG");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetStaticIntRoute_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|NEXTHOPIP|NEXTHOPINT|ADMIN|TAG");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfGeneratedRoute_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfGeneratedRoutePolicy_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfInterface", "NODE|INTERFACE|AREA");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfInterfaceCost", "NODE|INTERFACE|COST");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfOutboundPolicyMap", "NODE|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetOspfRouterId", "NODE|IP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetCommunityListLine", "LIST|LINE|COMMUNITY");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetCommunityListLinePermit", "LIST|LINE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetRouteFilterLine", "LIST|LINE|NETWORKSTART|NETWORKEND|MINPREFIX|MAXPREFIX");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetRouteFilterPermitLine", "LIST|LINE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseAddCommunity", "MAP|CLAUSE|COMMUNITY");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseDeleteCommunity", "MAP|CLAUSE|LIST");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseDeny", "MAP|CLAUSE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchAcl", "MAP|CLAUSE|ACL");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchCommunityList", "MAP|CLAUSE|LIST");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchNeighbor", "MAP|CLAUSE|NEIGHBORIP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchProtocol", "MAP|CLAUSE|PROTOCOL");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchRouteFilter", "MAP|CLAUSE|LIST");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseMatchTag", "MAP|CLAUSE|TAG");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClausePermit", "MAP|CLAUSE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseSetCommunity", "MAP|CLAUSE|COMMUNITY");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseSetLocalPreference", "MAP|CLAUSE|LOCALPREF");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseSetMetric", "MAP|CLAUSE|METRIC");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseSetNextHopIp", "MAP|CLAUSE|NEXTHOPIP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapClauseSetOriginType", "MAP|CLAUSE|ORIGINTYPE");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetPolicyMapOspfExternalRouteType", "MAP|PROTOCOL");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpDefaultLocalPref", "NODE|NEIGHBORIP|LOCALPREF");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpExportPolicy", "NODE|NEIGHBORIP|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpGeneratedRoute_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpGeneratedRoutePolicy_flat", "NODE|NETWORKSTART|NETWORKEND|PREFIXLENGTH|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpImportPolicy", "NODE|NEIGHBORIP|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpNeighborDefaultMetric", "NODE|NEIGHBORIP|METRIC");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpNeighborGeneratedRoute_flat", "NODE|NEIGHBORIP|NETWORKSTART|NETWORKEND|PREFIXLENGTH");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpNeighborGeneratedRoutePolicy_flat", "NODE|NEIGHBORIP|NETWORKSTART|NETWORKEND|PREFIXLENGTH|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpNeighborIp", "NODE|NEIGHBORIP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpNeighborSendCommunity", "NODE|NEIGHBORIP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetBgpOriginationPolicy", "NODE|NEIGHBORIP|MAP");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetLocalAs", "NODE|NEIGHBORIP|LOCALAS");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetRemoteAs", "NODE|NEIGHBORIP|REMOTEAS");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetRouteReflectorClient", "NODE|NEIGHBORIP|CLUSTERID");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetNodeVendor", "NODE|VENDOR");
      _CONTROL_PLANE_FACT_COLUMN_HEADERS.put("SetNodeRole", "NODE|ROLE");
   }

   private Facts() {
   }

}
