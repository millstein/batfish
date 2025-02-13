package org.batfish.representation.juniper;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.batfish.datamodel.routing_policy.as_path.AsPathMatchExpr;
import org.batfish.datamodel.routing_policy.as_path.AsPathMatchRegex;
import org.batfish.datamodel.routing_policy.as_path.AsSetsMatchingRanges;
import org.batfish.representation.juniper.parboiled.AsPathRegex;

/**
 * A class that converts a Juniper AS Path regex to an instance of {@link AsPathMatchExpr}.
 *
 * @see <a
 *     href="https://www.juniper.net/documentation/en_US/junos/topics/usage-guidelines/policy-configuring-as-path-regular-expressions-to-use-as-routing-policy-match-conditions.html">Juniper
 *     docs</a>
 */
public final class AsPathMatchExprParser {

  // "N" : "AS Path matches single ASN number"
  private static final Pattern AS_PATH_EXACT_MATCH_ASN = Pattern.compile("(\\d+)");

  // ".* N .*" : "AS Path contains N"
  private static final Pattern AS_PATH_CONTAINS_ASN = Pattern.compile("\\.\\*\\s?(\\d+)\\s?\\.\\*");

  // ".* N" or ".* N$" : "AS Path ends with N"
  private static final Pattern AS_PATH_ENDS_WITH_ASN = Pattern.compile("\\.\\* (\\d+)\\$?");

  // "N .*" or "^N .*" : "AS Path starts with N".
  private static final Pattern AS_PATH_STARTS_WITH_ASN = Pattern.compile("\\^?(\\d+) \\.\\*");

  // ".* [start-end] .*" : "AS Path contains an ASN in range between start and end included
  private static final Pattern AS_PATH_CONTAINS_ASN_RANGE_PATTERN_1 =
      Pattern.compile("\\.\\* \\[(\\d+)-(\\d+)\\] \\.\\*");

  // ".* start-end .*" : "AS Path contains an ASN in range between start and end included
  private static final Pattern AS_PATH_CONTAINS_ASN_RANGE_PATTERN_2 =
      Pattern.compile("\\.\\* (\\d+)-(\\d+) \\.\\*");

  // "[start-end]" : "AS Path matches single ASN number in range between start and end included
  private static final Pattern AS_PATH_EXACT_MATCH_ASN_RANGE_PATTERN_1 =
      Pattern.compile("\\[(\\d+)-(\\d+)\\]");

  // "start-end" : "AS Path matches single ASN number in range between start and end included
  private static final Pattern AS_PATH_EXACT_MATCH_ASN_RANGE_PATTERN_2 =
      Pattern.compile("(\\d+)-(\\d+)");

  /**
   * Converts the given Juniper AS Path regular expression to an instance of {@link
   * AsPathMatchExpr}. First match the AS path input regex against predefined AS path regexes and
   * return VI construct {@link AsSetsMatchingRanges} Else convert to java regex and return {@link
   * AsPathMatchRegex}
   */
  public static AsPathMatchExpr convertToAsPathMatchExpr(String asPathRegex) {

    Matcher asPathExactMatchAsn = AS_PATH_EXACT_MATCH_ASN.matcher(asPathRegex);
    if (asPathExactMatchAsn.matches()) {
      return getAsSetsMatchingRanges(asPathExactMatchAsn.group(1), true, true);
    }

    Matcher asPathContainsAsn = AS_PATH_CONTAINS_ASN.matcher(asPathRegex);
    if (asPathContainsAsn.matches()) {
      return getAsSetsMatchingRanges(asPathContainsAsn.group(1), false, false);
    }

    Matcher asPathStartsWithAsn = AS_PATH_STARTS_WITH_ASN.matcher(asPathRegex);
    if (asPathStartsWithAsn.matches()) {
      return getAsSetsMatchingRanges(asPathStartsWithAsn.group(1), false, true);
    }

    Matcher asPathEndsWithAsn = AS_PATH_ENDS_WITH_ASN.matcher(asPathRegex);
    if (asPathEndsWithAsn.matches()) {
      return getAsSetsMatchingRanges(asPathEndsWithAsn.group(1), true, false);
    }

    Matcher asPathContainsAsnRangeBrackets =
        AS_PATH_CONTAINS_ASN_RANGE_PATTERN_1.matcher(asPathRegex);
    if (asPathContainsAsnRangeBrackets.matches()) {
      return getAsSetsMatchingRanges(
          asPathContainsAsnRangeBrackets.group(1),
          asPathContainsAsnRangeBrackets.group(2),
          false,
          false);
    }

    Matcher asPathContainsAsnRangeNoBrackets =
        AS_PATH_CONTAINS_ASN_RANGE_PATTERN_2.matcher(asPathRegex);
    if (asPathContainsAsnRangeNoBrackets.matches()) {
      return getAsSetsMatchingRanges(
          asPathContainsAsnRangeNoBrackets.group(1),
          asPathContainsAsnRangeNoBrackets.group(2),
          false,
          false);
    }

    Matcher asPathExactMatchAsnRangeBrackets =
        AS_PATH_EXACT_MATCH_ASN_RANGE_PATTERN_1.matcher(asPathRegex);
    if (asPathExactMatchAsnRangeBrackets.matches()) {
      return getAsSetsMatchingRanges(
          asPathExactMatchAsnRangeBrackets.group(1),
          asPathExactMatchAsnRangeBrackets.group(2),
          true,
          true);
    }

    Matcher asPathExactMatchAsnRangeNoBrackets =
        AS_PATH_EXACT_MATCH_ASN_RANGE_PATTERN_2.matcher(asPathRegex);
    if (asPathExactMatchAsnRangeNoBrackets.matches()) {
      return getAsSetsMatchingRanges(
          asPathExactMatchAsnRangeNoBrackets.group(1),
          asPathExactMatchAsnRangeNoBrackets.group(2),
          true,
          true);
    }

    String javaRegex = AsPathRegex.convertToJavaRegex(asPathRegex);
    return AsPathMatchRegex.of(javaRegex);
  }

  private static AsSetsMatchingRanges getAsSetsMatchingRanges(
      String asn, boolean anchorEnd, boolean anchorStart) {
    long asnLong = Long.parseLong(asn);
    return AsSetsMatchingRanges.of(
        anchorEnd, anchorStart, ImmutableList.of(Range.singleton(asnLong)));
  }

  private static AsSetsMatchingRanges getAsSetsMatchingRanges(
      String asnLowerRange, String asnUpperRange, boolean anchorEnd, boolean anchorStart) {
    long start = Long.parseLong(asnLowerRange);
    long end = Long.parseLong(asnUpperRange);
    checkArgument(start <= end, "Invalid range %s-%s", start, end);
    return AsSetsMatchingRanges.of(
        anchorEnd, anchorStart, ImmutableList.of(Range.closed(start, end)));
  }

  private AsPathMatchExprParser() {} // prevent instantiation of utility class.
}
