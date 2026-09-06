package balticporter.transform

import balticporter.tir.*

/** The TYPE-CLASS ARRAY boundary, counted — every place [[ElementWitnessTransform]] declined to
  * move an element-typed array onto the witness, and why. Parameterised by the phase's own
  * `subjects`; an empty subject map is a no-op and this lane records nothing (CLAUDE.md §1(b)). */
object ElementWitnessCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "witness"

  /** what kind of refusal this is, which decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** a `null` read or write standing for TABLE OCCUPANCY, not for an absent value — the element
      * type cannot lose its `<: java.lang.Object` bound without a representation change. */
    case OccupancySentinel
    /** an element-typed array created inside a declaration the policy does not name. */
    case NonSubject
    /** a creation at an element-typed slot the phase recognises as one and cannot express through
      * the witness — no witness is in scope at that declaration, or the shape is not one of the
      * four the mechanism translates. */
    case UnhandledCreation
    /** an element-typed array presented as `Array[java.lang.Object]` — java's own RAW view of a
      * receiver, which stops being true the moment the element type may be a primitive. */
    case ErasedArrayCast
    /** java's UNCHECKED CONVERSION (JLS 5.1.9) at a RAW formal this phase filled with `Object`:
      * the argument is cast to the filled type — erasure-sound, and invisible in the java. */
    case RawConversion

  object Issue:
    /** which of §1's three kinds the fix is (CLAUDE.md §4.45). */
    def classification(i: Issue): String = i match
      case OccupancySentinel =>
        "§1(b) PER-LIBRARY, and it is a REPRESENTATION question the engine may not answer: this " +
          "site reads or writes `null` at an element slot to mean THE SLOT IS EMPTY, not to mean " +
          "the value is absent. An open-addressed table does this at every probe. Drop the " +
          "element type's `<: java.lang.Object` bound and the emitted code still compiles — `x == " +
          "null` is universal equality in Scala and `null.asInstanceOf[T]` is legal — while at a " +
          "primitive element type every empty slot reads as `0`/`false` and the probe loop finds a " +
          "key that was never inserted. No coercion closes that: the fix is a parallel occupancy " +
          "array, which is a different data structure and a hand-written one. Until the library " +
          "ships it, KEEP the class out of `dropBound` — which is what this row records — and the " +
          "boxed element type keeps java's own null semantics."
      case NonSubject =>
        "§1(b) PER-LIBRARY: an array whose element type is one of THIS declaration's own type " +
          "parameters is created here, and the policy's `subjects` map does not name the " +
          "declaration. Either add it (with the type-parameter indexes the arrays are keyed on) " +
          "so its creations move onto the witness and its constructors take the clause, or leave " +
          "it — a class whose arrays are only ever `Object[]` at run time is correct as java " +
          "wrote it, and this row is the statement that the omission was noticed."
      case UnhandledCreation =>
        "§1(a)/§1(b): the phase RECOGNISED an array creation at an ELEMENT-typed slot and could " +
          "not route it through the witness. Three shapes reach here: the array type REFLECTED " +
          "out of a `Class` argument java's own signature carries (a deprecated `T[]`-by-`Class` " +
          "constructor), a shape the mechanism does not translate (a multi-dimensional array, one " +
          "with an initialiser, or a whole-array `Arrays.fill` over a receiver that is not a " +
          "stable path), and a default array factory passed at a slot whose element type the " +
          "enclosing declaration does not hold. All keep the emitted text they had before the " +
          "phase ran, which compiles; what they do not get is the witness's element " +
          "representation. NOT here, and deliberately: a creation at a METHOD's OWN type " +
          "parameter (`<V> V[] toArray(Class<V>)`) — `V` is nobody's element type, so no policy " +
          "key reaches it and a row naming it would be unactionable."
      case RawConversion =>
        "§1(a) ENGINE, and IT COMPILES: java wrote a RAW type at this formal (or assignment target), " +
          "which this phase filled with `java.lang.Object` once the element parameter lost its bound " +
          "(a raw wildcard no longer conforms to `Object` unbounded). The filled type is INVARIANT " +
          "where java's raw type accepted any instantiation, so the phase emits java's own unchecked " +
          "conversion at the site: `arg.asInstanceOf[C[java.lang.Object]]`. Sound under erasure " +
          "(the JVM sees one class); a row is a place to give the declaration its type argument."
      case ErasedArrayCast =>
        "§1(b) PER-LIBRARY, and IT COMPILES: java wrote a RAW receiver here, so javac's own erased " +
          "view of the call presents this element-typed array as `Object[]`, and the port emits " +
          "that view as a cast. While the element type was bounded by `java.lang.Object` the cast " +
          "was a no-op; with the bound dropped a primitive element array (`int[]`) reaches it and " +
          "the JVM throws `ClassCastException` at a line that type-checks. No coercion closes it: " +
          "the fix is the RECEIVER's raw type — give the field or local its type argument in the " +
          "library, or keep the declaring class out of `dropBound` so the element stays boxed. " +
          "Until then this call is correct for a reference element type and throws for a primitive one."

  /** one refusal. `unit` is the top-level symbol for D2 ownership filtering. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin,
                           unit: SymId = SymId.None):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
