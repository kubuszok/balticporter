# Java semantics Scala does not share

Most of Java translates to Scala by changing the spelling. The constructs below do not: the obvious
Scala is *valid* and means something else, so nothing fails to compile — a port simply behaves
differently. Every one of them was found by running ported tests, and each is handled by the engine
for every library. Where a rule has a catalogue id (`JS-…`), that id appears in the
`/* porter: … */` note next to the generated code and in the run's reports.

## Expressions

| Java | Why the obvious Scala is wrong | What is generated |
|---|---|---|
| `a == b` on references | Scala's `==` calls `equals` — inside an `equals` body that is infinite recursion | `a eq b` |
| `x++` used as a value | post-increment yields the value *before* the update; `{ x += 1; x }` is off by one (every circular buffer) | `{ val p = x; x += 1; p }` |
| `arr[f()] += x`, `arr[f()]++` | Java evaluates the array and the index once (JLS 15.26.2); `arr(f()) = arr(f()) + x` runs `f()` twice | each side-effecting part of the target is bound to a temporary once; simple targets keep the direct form (`JS-E17`) |
| an `int` at a `float` slot | Scala's implicit `int2float`, `long2float`, `long2double` are deprecated and fail under `-Werror`; Java widens silently (JLS 5.1.2) | `.toFloat` / `.toDouble` at the slot |
| `int.class` at a `Class<T>` slot | Java types it `Class<Integer>`; `classOf[Int]` is `Class[Int]` and fits no such slot | `classOf[Int].asInstanceOf[Class[Integer]]`, with Java's inferred type argument pinned at the call (`JS-E20`) |
| an array forwarded through `T...` at an external method | scalac reads a class file's `T...` as a repeated parameter, so a bare array becomes ONE element | the spread, `args*` |
| a primitive array at a reference `T...` slot — `Arrays.asList(intArr)` | Java wraps it as a single element (`List<int[]>` of size 1) | packed as one element |
| `list.remove(anInteger)` | Java resolved `remove(Object)` — by value; Scala's only `Buffer.remove` is by index, and unboxing applies silently | a by-value helper, chosen from the overload Java resolved |
| `x instanceof Map`, `(Map<K,V>) x` where the port retyped `Map` | retyping moves static types; these ask about a runtime object, and a ported library holds both representations at every `Object` slot | a test over both representations; refused and counted where the target is a concrete class |

## Statements and control flow

| Java | Why the obvious Scala is wrong | What is generated |
|---|---|---|
| `break` / `continue` | Scala has neither; dropped, the loop runs on | `boundary` around the loop for `break`, around the body for `continue` |
| `break L` / `continue L` | a labelled jump crosses nested loops and switches | a named boundary on the labelled statement. For `break L` the name is a `ControlThrowable` sentinel thrown and caught around the loop: Scala.js lowers a named `boundary.break` across nested `while` loops to a JavaScript `break` of the *innermost* loop |
| a label on a statement that is not a loop | `break L` leaves *that* statement | a named boundary around the statement |
| a jump inside a `try` with a broad `catch` | `boundary.Break` extends `RuntimeException`, so `catch (Exception e)` swallows the jump | a re-throw arm ahead of the Java arms wherever a jump crosses the catch |
| `switch` without `default` | Java falls out when nothing matches; a Scala `match` throws `MatchError` | the fall-out arm `case _ => ()` |
| `switch` on a `null` string, boxed value or enum | Java throws `NullPointerException` at once (JLS 14.11); `null` matches no pattern and reaches the fall-out arm silently | `case null => throw new NullPointerException(…)` ahead of the Java arms |
| fall-through between cases | `match` has none | the next case's tail is duplicated into the arm; a `break` in the middle of a case becomes a boundary around the arm |
| arrow cases `case A -> e;` | no fall-through at all (JLS 14.11.2); deciding by "is there a trailing `break`?" duplicates tails wrongly | the case *kind* is read, not the body |
| a `switch` expression | it must be exhaustive (JLS 15.28.1), so a fall-out arm would answer `()` where Java answers nothing | no fall-out arm; a `yield` that is not the arm's last statement becomes a value-carrying boundary |
| `try (R r = …) { … }` | a bare `try` releases nothing; `Using` breaks `return` and jumps | Java's own lowering (JLS 14.20.3.1) inline: reverse-order close, `addSuppressed`, jumps re-thrown ahead of the recorder |

## Declarations

| Java | Why the obvious Scala is wrong | What is generated |
|---|---|---|
| `static final int X = 0` | Java inlines a constant, so reading it never runs the class initialiser; a typed `val` does — and initialiser cycles (libGDX's `Vector3`/`Matrix4`) deadlock | `inline val X = 0` (`JS-C08`) |
| a `final` field | Java fields are hidden, never overridden (JLS 8.3) | `final val` (`JS-C53`) |
| `static { … }` and non-constant static field initialisers | Java initialises a class on first `new`, static access or subclass initialisation (JLS 12.4.1); a Scala companion initialises only when the *object* is touched, so a registration silently never happens | Java's own triggers reproduced: a reference to the companion at the head of the class body and of subclasses' companions. Refused and counted for reflection and for companions in a mutual cycle (`JS-C07`) |
| `super(args)` in a secondary constructor | Scala's secondary constructors cannot call `super`; every exception lost its message | the widest `super` call is promoted to the primary constructor |
| a `record` | a `case class` differs in six observable ways (explicit accessors clash, `unapply` reads parameters not accessors, `equals` on `double`, added `copy`/`apply`) | a plain `final class` with `equals`/`hashCode`/`toString` over the fields and `unapply` over the accessors (`JS-C43`) |
| overloads that differ only by Java's resolution phase — `f(int)` beside `f(Object)`, fixed arity beside varargs, generic beside non-generic | Java resolves in three phases and an earlier phase wins outright (JLS 15.12.2); Scala resolves in one. Both compilers accept the call and bind different members | no faithful translation short of a resolver: every such call is **counted** and reported (`JS-C22`, `JS-C23`) |
| a rename onto a standard-library member with different mutability — `Bits.xor(bits)` | Java's mutates; `BitSet.xor` returns a new set | the in-place operator |
| an inclusive range bound passed to an exclusive API | a silent off-by-one | the bound translated, Java's own refusals restated |
| reflective instantiation of a private nested class (`Class.newInstance()`, `getConstructor()`, `getDeclaredConstructor().newInstance()`, `MethodHandles.Lookup.findConstructor()`) | javac emits a private constructor (JLS 8.8.9); scalac emits it public — the call succeeds silently where java throws. A JVM-reflection contract java's own tests assert | no faithful translation: every reflective instantiation site is **counted** as `reflection-visibility(private-target\|unknown-target)` (`JS-C54`); a consumer records a test-drop decision for each hit |

## Collections a third party reifies

`TypeReference<Map<K,V>>`, `TypeToken<…>`, `Class<T>`: the type argument survives into the class
file's generic signature and a library such as Jackson reads it back. A port that retypes
`java.util.Map` to a Scala collection must leave such arguments alone and bridge at the use. Which
types are carriers is per-library configuration; `java.lang.Class` always is.

## Tests

| JUnit | Why the obvious MUnit is wrong | What is generated |
|---|---|---|
| per-test instance state | JUnit builds a fresh test object per `@Test`; an MUnit suite is one instance, so every test after the first inherits state. Only running the suite shows it | Java's initialisation sequence is hoisted into a reset method called at the head of every test, ahead of `@Before` |
| `@Before` | MUnit has no equivalent on a shared instance | called at the head of each test body |
| `@Test(expected = E.class)` | run bare, it passes while checking nothing | `intercept[E] { … }` |
| `@Rule ExpectedException` | the expected throw propagates as a failure | the rule is modelled: a list of armed expectations checked around the whole body |
