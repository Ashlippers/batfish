package org.batfish.symbolic.ainterpreter;

import java.util.Map.Entry;
import java.util.Set;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDPairing;
import org.batfish.datamodel.Prefix;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.Protocol;
import org.batfish.symbolic.bdd.BDDFiniteDomain;
import org.batfish.symbolic.bdd.BDDNetFactory;
import org.batfish.symbolic.bdd.BDDNetFactory.BDDRoute;
import org.batfish.symbolic.bdd.BDDTransferFunction;
import org.batfish.symbolic.bdd.BDDUtils;

public class ReachabilityDomain implements IAbstractDomain<BDD> {

  private BDDNetFactory _netFactory;

  private BDDPairing _pairing;

  private BDDRoute _variables;

  private BDD _projectVariables;

  ReachabilityDomain(BDDNetFactory netFactory, BDD projectVariables) {
    _netFactory = netFactory;
    _variables = _netFactory.variables();
    _projectVariables = projectVariables;
    _pairing = _netFactory.makePair();
  }

  @Override
  public BDD bot() {
    return _netFactory.zero();
  }

  @Override
  public BDD value(String router, Protocol proto, Set<Prefix> prefixes) {
    BDD acc = _netFactory.zero();
    if (prefixes != null) {
      for (Prefix prefix : prefixes) {
        BDD pfx = BDDUtils.prefixToBdd(_netFactory.getFactory(), _variables, prefix);
        acc.orWith(pfx);
      }
    }
    BDD prot = _variables.getProtocolHistory().value(proto);
    BDD dst = _variables.getDstRouter().value(router);
    acc = acc.andWith(dst);
    acc = acc.andWith(prot);
    return acc;
  }

  @Override
  public BDD transform(BDD input, EdgeTransformer t) {
    BDDTransferFunction f = t.getBgpTransfer();
    // Filter routes that can not pass through the transformer
    BDD allow = f.getFilter();
    BDD block = allow.not();

    // System.out.println(_variables.dot(input));
    // System.out.println(_variables.dot(block));

    BDD blockedInputs = input.and(block);
    BDD blockedPrefixes = blockedInputs.exist(_projectVariables);
    BDD notBlockedPrefixes = blockedPrefixes.not();
    // Not sure why, but andWith does not work here (JavaBDD bug?)
    input = input.and(notBlockedPrefixes);

    // Modify the result
    BDDRoute mods = f.getRoute();
    _pairing.reset();
    if (mods.getConfig().getKeepCommunities()) {
      for (Entry<CommunityVar, BDD> e : _variables.getCommunities().entrySet()) {
        CommunityVar cvar = e.getKey();
        BDD x = e.getValue();
        BDD temp = _variables.getCommunitiesTemp().get(cvar);
        BDD expr = mods.getCommunities().get(cvar);
        BDD equal = temp.biimp(expr);
        input = input.andWith(equal);
        _pairing.set(temp.var(), x.var());
      }
    }

    if (mods.getConfig().getKeepProtocol()) {
      BDDFiniteDomain<Protocol> var = _variables.getProtocolHistory();
      BDDFiniteDomain<Protocol> prot = new BDDFiniteDomain<>(var);
      prot.setValue(Protocol.BGP);
      BDD[] vec = _variables.getProtocolHistory().getInteger().getBitvec();
      for (int i = 0; i < vec.length; i++) {
        BDD x = vec[i];
        BDD temp = _variables.getProtocolHistoryTemp().getInteger().getBitvec()[i];
        BDD expr = prot.getInteger().getBitvec()[i];
        BDD equal = temp.biimp(expr);
        input.andWith(equal);
        _pairing.set(temp.var(), x.var());
      }
    }

    input = input.exist(_projectVariables);
    input = input.replaceWith(_pairing);
    return input;
  }

  @Override
  public BDD merge(BDD x, BDD y) {
    return x.or(y);
  }

  @Override
  public BDD toBdd(BDD value) {
    return value;
  }

  public BDDRoute getVariables() {
    return _variables;
  }
}
