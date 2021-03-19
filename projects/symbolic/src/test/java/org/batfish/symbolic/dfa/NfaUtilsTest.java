package org.batfish.symbolic.dfa;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class NfaUtilsTest{

  @Test
  public void testInsertExplicitConcatOperator() {
    String pathSpec = "^<a><b>*(<c><d>)*|<e><f>$";
    String inserted = NfaUtils.insertExplicitConcatOperator(pathSpec);
    assertEquals("^<a>~<b>*~(<c>~<d>)*|<e>~<f>$", inserted);
  }

  @Test
  public void testToPostfix() {
    String inserted = "^<a>~(<b>|<c>)*$";
    List<Object> postfix = NfaUtils.toPostfix(inserted);
    List<Object> expected = new ArrayList<>();
    expected.add(new SingleNodeTerm("a"));
    expected.add(new SingleNodeTerm("b"));
    expected.add(new SingleNodeTerm("c"));
    expected.add('|');
    expected.add('*');
    expected.add('~');
    expected.add('^');
    expected.add('$');

    assertEquals(expected, postfix);
  }

  @Test
  public void testToEpsilonNFA() {
    List<Object> postfix = new ArrayList<>();
    postfix.add(new SingleNodeTerm("a"));
    postfix.add(new SingleNodeTerm("b"));
    //postfix.add(new SingleNodeTerm("c"));
    postfix.add('|');
    //postfix.add('*');
    //postfix.add('~');
    // postfix.add('^');
    // postfix.add('$');

    MetaNFA eNFA = NfaUtils.toEpsilonNFA(postfix);
    MetaNFA expected = new MetaNFA(new MetaNFAState(false), new MetaNFAState(true));

    assertEquals(expected, eNFA);
  }

  @Test
  public void testToNFA() {
    List<Object> postfix = new ArrayList<>();
    postfix.add(new SingleNodeTerm("a"));
    postfix.add(new SingleNodeTerm("b"));
    postfix.add(new SingleNodeTerm("c"));
    postfix.add('|');
    postfix.add('*');
    postfix.add('~');
    // postfix.add('^');
    // postfix.add('$');

    MetaNFA eNFA = NfaUtils.toEpsilonNFA(postfix);
    Nfa nfa = NfaUtils.toNFA(eNFA);
    Nfa expected = new Nfa();

    assertEquals(expected, nfa);
  }

  @Test
  public void testCompile() {
    String spec = "<a><b><c>";
    Nfa nfa = NfaUtils.compile(spec);
    Nfa expected = new Nfa();

    assertEquals(expected, nfa);
  }
}