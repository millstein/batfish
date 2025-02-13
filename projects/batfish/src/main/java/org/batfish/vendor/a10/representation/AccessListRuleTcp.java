package org.batfish.vendor.a10.representation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.SubRange;

/** An access-list rule matching tcp traffic. */
public class AccessListRuleTcp implements AccessListRule {
  @Nonnull
  @Override
  public Action getAction() {
    return _action;
  }

  @Nonnull
  @Override
  public AccessListAddress getSource() {
    return _source;
  }

  @Nonnull
  @Override
  public AccessListAddress getDestination() {
    return _destination;
  }

  @Override
  public <T> T accept(AccessListRuleVisitor<T> visitor) {
    return visitor.visitTcp(this);
  }

  /** Destination port range this rule matches on. */
  public @Nullable SubRange getDestinationRange() {
    return _destinationRange;
  }

  @Nonnull
  @Override
  public String getLineText() {
    return _lineText;
  }

  public void setDestinationRange(@Nullable SubRange destinationRange) {
    _destinationRange = destinationRange;
  }

  public AccessListRuleTcp(
      Action action, AccessListAddress source, AccessListAddress destination, String lineText) {
    _action = action;
    _source = source;
    _destination = destination;
    _lineText = lineText;
  }

  private final @Nonnull Action _action;
  private final @Nonnull AccessListAddress _source;
  private final @Nonnull AccessListAddress _destination;
  private @Nullable SubRange _destinationRange;
  private final @Nonnull String _lineText;
}
