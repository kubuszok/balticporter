package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** The four `java.util` rows whose absence off the JVM is a LINK error and whose answer is not a
  * stdlib type: `EnumMap`, `EnumSet` and the three primitive `Optional`s.
  *
  * `EnumMap`/`EnumSet` are SHIMS because both GUARANTEE iteration in the enum's declaration order,
  * which a `HashMap` does not have and a `LinkedHashMap` answers with INSERTION order instead —
  * reproducing the availability and dropping the guarantee is catalog row `JS-C42`. The optionals
  * are ALIASES because their retype is arity-changing: `OptionalInt` takes no type argument and
  * `Option` takes one, so the head swap alone would emit `scala.Option` un-applied.
  *
  * `JavaEnumCollectionsSpec` is the behavioural half; this is the emission half.
  */
class CollectionsEnumOptionalSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.EnumMap;
      |import java.util.EnumSet;
      |import java.util.Map;
      |import java.util.OptionalInt;
      |import java.util.Set;
      |class Cfg {
      |  enum Level { LOW, MID, HIGH }
      |  Map<Level, String> names = new EnumMap<Level, String>(Level.class);
      |  EnumMap<Level, String> direct = new EnumMap<Level, String>(Level.class);
      |  EnumSet<Level> raw = EnumSet.noneOf(Level.class);
      |  Map<Level, String> copied = new EnumMap<Level, String>(names);
      |  Set<Level> on = EnumSet.noneOf(Level.class);
      |  Set<Level> all = EnumSet.allOf(Level.class);
      |  Set<Level> two = EnumSet.of(Level.LOW, Level.HIGH);
      |  OptionalInt limit = OptionalInt.empty();
      |  void set(Level l, String n) { names.put(l, n); on.add(l); }
      |  String get(Level l) { return names.get(l); }
      |  int limitOr(int d) { return limit.orElse(d); }
      |  boolean hasLimit() { return limit.isPresent(); }
      |  int theLimit() { return limit.getAsInt(); }
      |  OptionalInt some() { return OptionalInt.of(3); }
      |}
      |""".stripMargin

  private val out =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))).emit

  test("EnumMap and EnumSet retype to the ORDER-KEEPING shims, never to a stdlib map") {
    // At a DECLARED `EnumMap`/`EnumSet` slot the shim is the type; at a slot java declared `Map` or
    // `Set` the port keeps `mutable.Map`/`Set`, which is right and is what makes the shim a
    // sub-type answer rather than a parallel one.
    assert(clue(out).contains("balticporter.runtime.JavaEnumMap[demo.Cfg.Level, java.lang.String]"))
    assert(clue(out).contains("balticporter.runtime.JavaEnumSet[demo.Cfg.Level]"))
    assert(!out.contains("java.util.EnumMap") && !out.contains("java.util.EnumSet"))
  }

  test("the COPY constructor reaches the shim's own `from`, not a capacity hint") {
    assert(clue(out).contains("balticporter.runtime.JavaEnumMap.from(this.names)"))
  }

  test("the CLASS TOKEN constructor becomes a factory — never an argument silently deleted") {
    // Java needs `Level.class` to size its ordinal ARRAY; the shim orders by `ordinal` and has
    // nothing to size. Routed to a named factory, the emitted code still reads as the java it came
    // from; with the argument dropped it would read as a different call.
    assert(clue(out).contains("balticporter.runtime.JavaEnumMap.ofType(classOf["))
  }

  test("EnumSet's statics are the whole way IN — the java type has no public constructor") {
    assert(clue(out).contains("balticporter.runtime.JavaEnumSet.noneOf(classOf["))
    assert(clue(out).contains("balticporter.runtime.JavaEnumSet.allOf(classOf["))
    assert(clue(out).contains("balticporter.runtime.JavaEnumSet.of("))
  }

  test("the kind-driven arms still fire on the shims — they ARE a Map and a Set") {
    // The shims are `Kind.Map`/`Kind.Set`, so every rewrite those kinds already have applies: this
    // is what makes them a mapping with a stronger target rather than a second mechanism.
    assert(clue(out).contains("this.on += l"))
    assert(clue(out).contains("this.names.put(l, n)"))
    assert(clue(out).contains("this.names.getOrElse(l"))
  }

  test("a primitive Optional becomes the Option ALIAS, and its members are renamed") {
    assert(clue(out).contains("balticporter.runtime.JavaOptionalInt"))
    assert(!out.contains("java.util.OptionalInt"))
    assert(clue(out).contains("this.limit.isDefined"))     // isPresent, PARAMETERLESS
    assert(clue(out).contains("this.limit.get"))           // getAsInt, likewise
  }

  test("`orElse` is the ONE member of that family that is not a rename — java evaluates its default") {
    // `Optional.orElse(v)` takes a VALUE: java computes it before the call, whatever the optional
    // holds. `Option.getOrElse` takes it BY NAME and computes it only when empty. Same name, same
    // answer, and a side effect that runs in java and does not run in the port — `CLAUDE.md` §4.4's
    // defect class, with a green compile and no moved count. The strict helper restores java's
    // evaluation order at the call.
    assert(clue(out).contains("balticporter.runtime.JavaCollections.optionalOrElse(this.limit, d)"))
    assert(!out.contains("this.limit.getOrElse(d)"))
  }

  test("…and its two factories are Some and None, which need no runtime member at all") {
    assert(clue(out).contains("scala.Some(3)"))
    assert(clue(out).contains("scala.None"))
  }
