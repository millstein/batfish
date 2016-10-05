package org.batfish.representation.juniper;

import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.main.Warnings;

public final class PsFromPrefixListFilterLonger extends PsFrom {

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   private String _prefixList;

   public PsFromPrefixListFilterLonger(String prefixList) {
      _prefixList = prefixList;
   }

   @Override
   public BooleanExpr toBooleanExpr(JuniperConfiguration jc, Configuration c,
         Warnings warnings) {
      PrefixList pl = jc.getPrefixLists().get(_prefixList);
      if (pl != null) {
         pl.getReferers().put(this, "from prefix-list-filter longer");
         if (pl.getIpv6()) {
            return BooleanExprs.False.toStaticBooleanExpr();
         }
         RouteFilterList rf = c.getRouteFilterLists().get(_prefixList);
         String longerListName = "~" + _prefixList + "~LONGER~";
         RouteFilterList longerList = c.getRouteFilterLists()
               .get(longerListName);
         if (longerList == null) {
            longerList = new RouteFilterList(longerListName);
            for (RouteFilterLine line : rf.getLines()) {
               Prefix prefix = line.getPrefix();
               LineAction action = line.getAction();
               SubRange longerLineRange = new SubRange(
                     line.getLengthRange().getStart() + 1, 32);
               if (longerLineRange.getStart() > 32) {
                  warnings.redFlag("'prefix-list-filter " + _prefixList
                        + " longer' cannot match more specific prefix than "
                        + prefix.toString());
                  continue;
               }
               RouteFilterLine orLongerLine = new RouteFilterLine(action,
                     prefix, longerLineRange);
               longerList.addLine(orLongerLine);
               c.getRouteFilterLists().put(longerListName, longerList);
            }
         }
         return new MatchPrefixSet(new NamedPrefixSet(longerListName));
      }
      else {
         warnings.redFlag(
               "Reference to undefined prefix-list: \"" + _prefixList + "\"");
         return BooleanExprs.False.toStaticBooleanExpr();
      }
   }
}
