package org.batfish.representation.cisco;

import org.batfish.vendor.StructureUsage;

public enum CiscoStructureUsage implements StructureUsage {
  BGP_AGGREGATE_ATTRIBUTE_MAP("bgp vrf aggregate-address attribute-map"),
  BGP_DEFAULT_ORIGINATE_ROUTE_MAP("bgp default-originate route-map"),
  BGP_INBOUND_FILTER_LIST("bgp inbound filter-list"),
  BGP_INBOUND_FILTER6_LIST("bgp inbound ipv6 filter-list"),
  BGP_INBOUND_PREFIX_LIST("bgp inbound prefix-list"),
  BGP_INBOUND_PREFIX6_LIST("bgp inbound ipv6 prefix-list"),
  BGP_INBOUND_ROUTE_MAP("bgp inbound route-map"),
  BGP_INBOUND_ROUTE6_MAP("bgp inbound ipv6 route-map"),
  BGP_INHERITED_GROUP("inherited BGP group"),
  BGP_INHERITED_PEER("inherited BGP peer"),
  BGP_INHERITED_PEER_POLICY("inherited BGP peer-policy"),
  BGP_INHERITED_SESSION("inherited BGP peer-session"),
  BGP_NEIGHBOR_REMOTE_AS_ROUTE_MAP("bgp neighbor remote-as route-map"),
  BGP_NEIGHBOR_STATEMENT("bgp neighbor statement"),
  BGP_NETWORK_ORIGINATION_ROUTE_MAP("bgp ipv4 network statement route-map"),
  BGP_NETWORK6_ORIGINATION_ROUTE_MAP("bgp ipv6 network statement route-map"),
  BGP_OUTBOUND_FILTER_LIST("bgp outbound filter-list"),
  BGP_OUTBOUND_FILTER6_LIST("bgp outbound ipv6 filter-list"),
  BGP_OUTBOUND_PREFIX_LIST("bgp outbound prefix-list"),
  BGP_OUTBOUND_PREFIX6_LIST("bgp outbound ipv6 prefix-list"),
  BGP_OUTBOUND_ROUTE_MAP("bgp outbound route-map"),
  BGP_OUTBOUND_ROUTE6_MAP("bgp outbound ipv6 route-map"),
  BGP_REDISTRIBUTE_CONNECTED_MAP("bgp redistribute connected route-map"),
  BGP_REDISTRIBUTE_EIGRP_MAP("bgp redistribute eigrp route-map"),
  BGP_REDISTRIBUTE_ISIS_MAP("bgp redistribute isis route-map"),
  BGP_REDISTRIBUTE_LISP_MAP("bgp redistribute lisp route-map"),
  BGP_REDISTRIBUTE_OSPF_MAP("bgp redistribute ospf route-map"),
  BGP_REDISTRIBUTE_OSPFV3_MAP("bgp redistribute ospfv3 route-map"),
  BGP_REDISTRIBUTE_RIP_MAP("bgp redistribute rip route-map"),
  BGP_REDISTRIBUTE_STATIC_MAP("bgp redistribute static route-map"),
  BGP_ROUTE_MAP_ADVERTISE("bgp address-family aggregate-address advertise-map"),
  BGP_ROUTE_MAP_ATTRIBUTE("bgp address-family aggregate-address attribute-map"),
  BGP_ROUTE_MAP_OTHER("bgp otherwise in/outbound route-map"),
  BGP_ROUTE_MAP_SUPPRESS("bgp address-family aggregate-address suppress-map"),
  BGP_ROUTE_MAP_UNSUPPRESS("bgp unsuppress-map"),
  BGP_TABLE_MAP("bgp table-map"),
  BGP_UPDATE_SOURCE_INTERFACE("update-source interface"),
  BGP_VRF_AGGREGATE_ROUTE_MAP("bgp vrf aggregate-address route-map"),
  CLASS_MAP_ACCESS_GROUP("class-map access-group"),
  CONTROL_PLANE_ACCESS_GROUP("control-plane ip access-group"),
  CONTROLLER_DEPI_TUNNEL("controller rf-channel depi-tunnel"),
  COPS_LISTENER_ACCESS_LIST("cops listener access-list"),
  CRYPTO_MAP_IPSEC_ISAKMP_ACL("crypto map ipsec-isakmp acl"),
  CRYPTO_MAP_IPSEC_ISAKMP_ISAKMP_PROFILE("crypto map ipsec-isakmp isakmp-profile"),
  CRYPTO_MAP_IPSEC_ISAKMP_TRANSFORM_SET("crypto map ipsec-isakmp transform-set"),
  DEPI_TUNNEL_DEPI_CLASS("depi-tunnel depi-class"),
  DEPI_TUNNEL_L2TP_CLASS("depi-tunnel l2tp-class"),
  DEPI_TUNNEL_PROTECT_TUNNEL("depi-tunnel protect-tunnel"),
  DOCSIS_GROUP_DOCSIS_POLICY("cable load-balance docsis-group docsis-policy"),
  DOCSIS_POLICY_DOCSIS_POLICY_RULE("cable load-balance docsis-policy rule"),
  EXTENDED_ACCESS_LIST_NETWORK_OBJECT_GROUP("extended access-list network object-group"),
  EXTENDED_ACCESS_LIST_PROTOCOL_OBJECT_GROUP("extended access-list protocol object-group"),
  EXTENDED_ACCESS_LIST_PROTOCOL_OR_SERVICE_OBJECT_GROUP(
      "extended access-list protocol or service object-group"),
  EXTENDED_ACCESS_LIST_SERVICE_OBJECT_GROUP("extended access-list service object-group"),
  INSPECT_CLASS_MAP_MATCH_ACCESS_GROUP("class-map type inspect match access-group"),
  INSPECT_POLICY_MAP_INSPECT_CLASS("policy-map type inspect class type inspect"),
  INTERFACE_IGMP_ACCESS_GROUP_ACL("interface igmp access-group acl"),
  INTERFACE_IGMP_STATIC_GROUP_ACL("interface igmp static-group acl"),
  INTERFACE_INCOMING_FILTER("interface incoming ip access-list"),
  INTERFACE_IP_INBAND_ACCESS_GROUP("interface ip inband access-group"),
  INTERFACE_IP_VERIFY_ACCESS_LIST("interface ip verify access-list"),
  INTERFACE_OUTGOING_FILTER("interface outgoing ip access-list"),
  INTERFACE_PIM_NEIGHBOR_FILTER("interface ip pim neighbor-filter"),
  INTERFACE_POLICY_ROUTING_MAP("interface policy-routing route-map"),
  INTERFACE_SERVICE_POLICY("interface service-policy"),
  INTERFACE_ZONE_MEMBER("interface zone-member"),
  IP_NAT_DESTINATION_ACCESS_LIST("ip nat destination acl"),
  IP_NAT_SOURCE_ACCESS_LIST("ip nat source dynamic access-list"),
  IP_NAT_SOURCE_POOL("ip nat source pool"),
  IPSEC_PROFILE_ISAKMP_PROFILE("ipsec profile set isakmp-profile"),
  IPSEC_PROFILE_TRANSFORM_SET("ipsec profile set transform-set"),
  ISAKMP_POLICY_SELF_REF("isakmp policy"),
  ISAKMP_PROFILE_SELF_REF("isakmp profile"),
  ISAKMP_PROFILE_KEYRING("isakmp profile keyring"),
  LINE_ACCESS_CLASS_LIST("line access-class list"),
  LINE_ACCESS_CLASS_LIST6("line access-class ipv6 list"),
  MANAGEMENT_SSH_ACCESS_GROUP("management ssh ip access-group"),
  MANAGEMENT_TELNET_ACCESS_GROUP("management telnet ip access-group"),
  MSDP_PEER_SA_LIST("msdp peer sa-list"),
  NETWORK_OBJECT_GROUP_GROUP_OBJECT("object-group network group-object"),
  NETWORK_OBJECT_GROUP_NETWORK_OBJECT("object-group network network-object"),
  NTP_ACCESS_GROUP("ntp access-group"),
  OSPF_AREA_FILTER_LIST("ospf area filter-list"),
  OSPF_DEFAULT_ORIGINATE_ROUTE_MAP("ospf default-originate route-map"),
  OSPF_REDISTRIBUTE_BGP_MAP("ospf redistribute bgp route-map"),
  OSPF_REDISTRIBUTE_CONNECTED_MAP("ospf redistribute connected route-map"),
  OSPF_REDISTRIBUTE_STATIC_MAP("ospf redistribute static route-map"),
  PIM_ACCEPT_REGISTER_ACL("pim accept-register acl"),
  PIM_ACCEPT_REGISTER_ROUTE_MAP("pim accept-register route-map"),
  PIM_ACCEPT_RP_ACL("pim accept-rp acl"),
  PIM_RP_ADDRESS_ACL("pim rp-address"),
  PIM_RP_ANNOUNCE_FILTER("pim rp announce filter"),
  PIM_RP_CANDIDATE_ACL("pim rp candidate acl"),
  PIM_SEND_RP_ANNOUNCE_ACL("pim send rp announce acl"),
  PIM_SPT_THRESHOLD_ACL("pim spt threshold acl"),
  POLICY_MAP_CLASS("policy-map class"),
  QOS_ENFORCE_RULE_SERVICE_CLASS("cable qos enforce-rule service-class"),
  RIP_DEFAULT_ORIGINATE_ROUTE_MAP("rip default-information originate route-map"),
  RIP_DISTRIBUTE_LIST("router rip distribute-list"),
  RIP_REDISTRIBUTE_BGP_MAP("router rip redistribute bgp route-map"),
  RIP_REDISTRIBUTE_CONNECTED_MAP("router rip redistribute connected route-map"),
  RIP_REDISTRIBUTE_STATIC_MAP("router rip redistribute static route-map"),
  ROUTE_MAP_ADD_COMMUNITY("route-map set community additive"),
  ROUTE_MAP_CONTINUE("route-map continue target clause"),
  ROUTE_MAP_DELETE_COMMUNITY("route-map delete community"),
  ROUTE_MAP_MATCH_AS_PATH_ACCESS_LIST("route-map match as-path access-list"),
  ROUTE_MAP_MATCH_COMMUNITY_LIST("route-map match community-list"),
  ROUTE_MAP_MATCH_IP_ACCESS_LIST("route-map match ip access-list"),
  ROUTE_MAP_MATCH_IP_PREFIX_LIST("route-map match ip prefix-list"),
  ROUTE_MAP_MATCH_IPV6_ACCESS_LIST("route-map match ipv6 access-list"),
  ROUTE_MAP_MATCH_IPV6_PREFIX_LIST("route-map match ipv6 prefix-list"),
  ROUTE_MAP_SET_COMMUNITY("route-map set community list"),
  ROUTE_POLICY_AS_PATH_IN("route-policy as-path in"),
  ROUTE_POLICY_PREFIX_SET("route-policy prefix-set"),
  ROUTER_ISIS_DISTRIBUTE_LIST_ACL("router isis distribute-list acl"),
  ROUTER_VRRP_INTERFACE("router vrrp interface"),
  SNMP_SERVER_COMMUNITY_ACL("snmp server community acl"),
  SNMP_SERVER_COMMUNITY_ACL4("snmp server community ipv4 acl"),
  SNMP_SERVER_COMMUNITY_ACL6("snmp server community ipv6 acl"),
  SNMP_SERVER_FILE_TRANSFER_ACL("snmp server file transfer acl"),
  SNMP_SERVER_TFTP_SERVER_LIST("snmp server tftp-server list"),
  SSH_ACL("ssh acl"),
  SSH_IPV4_ACL("ssh ipv4 access-list"),
  SSH_IPV6_ACL("ssh ipv6 access-list"),
  TUNNEL_PROTECTION_IPSEC_PROFILE("interface TunnelX tunnel protection ipsec profile"),
  TUNNEL_SOURCE("tunnel source"),
  WCCP_GROUP_LIST("ip wccp group-list"),
  WCCP_REDIRECT_LIST("ip wccp redirect-list"),
  WCCP_SERVICE_LIST("ip wccp service-list"),
  ZONE_PAIR_DESTINATION_ZONE("zone-pair security destination"),
  ZONE_PAIR_INSPECT_SERVICE_POLICY("zone-pair service-policy type inspect"),
  ZONE_PAIR_SOURCE_ZONE("zone-pair security source");

  private final String _description;

  CiscoStructureUsage(String description) {
    _description = description;
  }

  @Override
  public String getDescription() {
    return _description;
  }
}
