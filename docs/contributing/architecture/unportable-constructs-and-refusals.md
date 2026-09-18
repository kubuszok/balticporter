# Unportable constructs and refusals

Not every Java construct has a faithful Scala translation, and not every third-party API is available
on every platform a port targets. This page describes how the engine handles both situations: never
by silently approximating or silently dropping something, but by refusing loudly, in a form later
phases and the target compiler can both still see, and by counting the refusal so it shows up in the
run's reports.

## The anti-omission stance

A construct the engine cannot translate faithfully is refused and counted, never silently
approximated and never quietly dropped. Diagnostics are values with stable identifiers, aggregated
per compilation unit, and a unit carrying an error fails the run. This is not the same as saying every
translation attempt must produce a working program on the first pass — it says that when a
translation is not faithful, that fact is a first-class, reportable outcome rather than a hidden
defect a test suite might or might not happen to catch. Every translation path is required to get a
check at the same time it gets a translation, walked with the engine's shared tree traversal rather
than a private recursion that could quietly skip a node kind nobody thought to add a case for.

## The marker: an unfaithful translation lives in the tree

Where a phase can only translate a term approximately, the tree carries an explicit wrapper node
around it rather than losing the distinction in a side table (positions in a tree do not survive a
rewrite, so a side table keyed on position would go stale) or inside every node as an
almost-always-empty field. An open marker means the wrapped term must not ship as-is. The wrapper
insists on pointing at real Java — it refuses to wrap a node with no genuine source origin — because
without that guarantee every marker with no real origin would collapse onto one indistinguishable
identity, defeating the whole point of being able to find and count them individually.

A marker may optionally carry a catalogue identifier (see below); carrying none is treated as the
honest answer when nothing in the catalogue actually describes the situation, and is preferred over
inventing a catalogue entry just to have something to point at. A parallel, declaration-level tag
exists for findings whose subject is a whole declaration's *shape* rather than one term inside it —
for example, a constructor topology with no expressible single-primary encoding.

The kinds of trouble a marker can represent form a closed, engine-level list: unchecked raw-type
conversion, a context-dependent choice of what to fill a raw type with, a value crossing a JDK
boundary in a way nothing can coerce, a constructor topology with no single expressible primary, an
API that is hostile to at least one target platform, a reflective lookup with no static answer, calls
whose overload resolution genuinely disagrees between the two languages, a gap in what the frontend
itself can see, an unmodelled tree node kind, and residue left behind by an annotation. Each kind
carries ranked, generic remediation text — a hand-porter's likely next step — built from nothing but
engine mechanisms, never from a library's name.

The tree traversal has exactly one extra case to let every phase's hooks reach *inside* an
approximation; a phase that does not specifically match the wrapper leaves it untouched, which is the
safe default — a marker survives being ignored by phases that have nothing to say about it. Discharge
is explicit: a marker moves from open to resolved only when a specific phase says how and why it
resolved it, never by falling silent.

## Reports: what a run publishes about what it could not do

A run produces one human-readable report describing every marker's location, the original Java, why
it could not be translated faithfully, and ranked remediation options. Alongside it, a machine-
readable table gives each finding a stable identifier built from its kind, its Java path, its owning
declaration's full name and a digest of its detail — deliberately *not* including the line number, so
a whitespace-only change upstream does not orphan an accepted baseline row. A digest per emitted
member gives a stronger revert-detection signal than any count, because a member's digest is
unchanged if and only if its emitted bytes are unchanged. A source map ties every emitted member back
to the line range it produced and the Java origin that produced it.

## Correlating diagnostics with the source map

Compiler and test-runner output are joined back to the source map in three categories. A diagnostic
that lands on a marked location is expected and already carries its own remediation. A diagnostic
that lands somewhere the engine did not mark is exactly the useful signal — a genuine engine gap,
worth investigating, because nothing already explains it. A diagnostic that cannot be placed in the
source map at all — for instance, one raised inside a hand-written, injected file — is reported
separately rather than forced into either of the other two categories. A marked location that
produces *no* diagnostic at all is itself worth a second look, since it may mean the marker no longer
describes anything real.

## Best-effort emission is a diagnostic mode, not a shipping mode

A single flag controls whether a run tolerates open markers at all. With it off — the default, and
the only shape a real deliverable run takes — the emission gate runs *before* anything is written: any
remaining open marker means the tree is not written at all, and the run exits with a nonzero status
only at the very end, after every other diagnostic has still been produced. The default rendering of
an open marker, in the code path that does write it out, is a Scala compile-time error: the port
simply refuses to compile at that spot, naming the construct, why it has no faithful translation, and
what an operator needs to do about it. A real deliverable run should never reach that rendering path
at all — reaching it is itself a sign that something upstream skipped the gate.

With the flag on, useful only while a new library is first being explored, an open marker instead
renders as the best guess it produced, fenced inside a deterministic comment, with a banner marking
the file and the whole output written to a clearly separate directory carrying its own sentinel — so
a partially-working exploratory build can never be mistaken for, or accidentally shipped as, a real
one.

At zero open markers, the two modes produce byte-identical output by construction — the flag changes
nothing about what a fully-handled run emits.

### Preview mode

A related, separate flag exists for the earliest stage of bringing a new library into the corpus,
where an operator's job is to *find* every construct the engine has no faithful translation for before
deciding what, if anything, to do about each one. With preview on, every such refusal becomes a
compile-time error at the refusal site itself, in order, naming the construct, why no faithful
translation exists, what an operator should do, and where in the original Java it came from — and
these errors are classified separately from any real compiler error, so the two are never confused
while triaging. Preview is off by default, and turning it off restores exactly the same emitted bytes
a run always produced — it changes nothing about what ships, only how loudly a first exploration
surfaces what still needs a decision.

## The difference catalogue

Every known semantic difference between Java and Scala that the engine has an opinion about is a
value in `balticporter.catalog`, not prose in a document — a document cannot be cited from code, kept
in sync with coverage measurement, or shipped inside the engine's own published artifact for a
downstream agent to consult. Catalogue identifiers are short, stable, area-coded slugs (an expression
difference, a statement difference, a class-shape difference, a generics difference, a library-surface
difference, a platform-capability difference) and are never reused or renumbered once retired, so a
citation from months ago still means what it always meant.

A catalogue row deliberately takes no per-library parameter: it is not a `RuleScope`, not a set of
names, not a predicate. A row about library surface or platform capability may carry a fact about
*which platform* it applies to — Scala.js and Scala Native genuinely do disagree about some JDK
surface — but never a *target set*, because which platforms a given port cares about is that port's
own declared configuration, not a fact about the difference itself.

### Portability per platform

The portability check reads a port's own declared target platforms and evaluates every catalogue row
that carries a platform-specific verdict against them, citing the row it consulted. A row cannot claim
a platform is available while a citation elsewhere calls it kept as-is, and a row's stated status must
agree with whether it has actually been discharged in code — a row cannot say "handled" while nothing
handles it, and cannot say "still open" once something does.

A related but separate question is whether a target platform's *build* actually declares the
dependency coordinate a mapped API needs — an API can be genuinely available on a platform while the
port simply has not added the artifact that supplies it there. That is a build-graph fact, checked
independently of the platform-availability question itself.

### Coverage as an obligation, not a courtesy

A run tracks, for the whole catalogue, which rows were actually consulted, which were never reached
by anything the run touched, which have no code path capable of deciding them at all, and which were
consulted but never actually discharged. All four are required together, on the same principle that
governs comment preservation elsewhere: a coverage number that can be driven to a clean value just by
declaring the awkward rows out of scope is not a coverage number worth having.
