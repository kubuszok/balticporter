# Provenance and licensing

The source map (see [intermediate-representation.md](intermediate-representation.md)) answers "which
Java line produced this Scala line". It does not answer "why is this type missing", "why was this
package renamed", or "why does this member have a hand-written body". Those are decisions, and every
one a port makes is recorded twice: once in a machine-readable log for tooling, and once directly
beside the code, for whoever is reading the generated file itself.

## The decision log

Every non-mechanical thing a port does is recorded as a `Decision`. Its reason is one of exactly three
constructor shapes — never free text — because the very first question anyone reading a decision needs
answered is which of the three rule kinds justified it (see
[phases-and-policy.md](phases-and-policy.md) for what each kind means): a universal engine fact
naming the rule; a configured, per-library policy naming both the phase and the manifest entry
verbatim; or a genuinely library-specific rule naming itself. Forcing the reason into one of these
three shapes, rather than a sentence a phase author is free to phrase however they like, is what keeps
"which kind of rule is this" answerable by a machine rather than by re-reading prose.

`Decision.Kind` is a closed, fixed set of outcomes — a renamed type, a renamed package, a renamed
member, a dropped type, a dropped member, a substituted body, an injected member, a redirected call, a
retyped signature, a funnelled constructor, a dropped super call, a widened visibility, or an
unrenderable construct. Two of these stay deliberately distinct even though they can look similar: a
class can successfully synthesise a shared constructor and still drop one root's own superclass
arguments in the process, and merging the two outcomes into one would make that combination
unanswerable.

A phase records a decision itself, through its own `record` call, drained once per run; a phase that a
debug flag skipped entirely records nothing, so a skipped phase is indistinguishable from one that
genuinely found nothing to do only in the sense that both leave zero rows — never in the sense that a
skip is silently treated as a positive "ran and did nothing" result. A declaration's *identity* in
the log is fixed at the moment the decision is recorded, not re-derived when the log is written out,
because a phase that runs later (a package rename, which always runs last) would otherwise relabel
every earlier decision into a name space it did not exist in yet when the decision was made.

Every decider records at the *declaration* level — one row per declaration whose emitted form
actually changed, reached through the same cross-reference index every other whole-program query
uses, never by a hand-rolled walk that tries to find "the current definition" some other way.
Parameters and local variables are filtered out structurally, not by a boolean flag someone remembered
to set. The log is scoped to a module's *own* declarations: a dependent module's phases making
decisions about a base module's units are withheld from the dependent's own log, though the count of
what was withheld is still printed, so "nothing recorded" and "some records withheld" are never
confused with each other. The log is sorted on every column, for diffability, and is written even
when it is empty — a header with no rows is a different, and equally meaningful, statement than a run
that never got far enough to write anything at all.

## The porter note: the same fact, written beside the code

The same decision is also rendered directly in the generated file, as a short comment immediately
beside the declaration it concerns:

```
/* porter: <kind> k=v … — <why> */
```

Porter notes are **derived, never authored** — the emitter renders a note only for a decision whose
subject it is actually about to emit, so there is no path by which a note and a decision can drift
apart from two independent authors. A dedicated check fails a run in both directions, per compilation
unit: a decision with no corresponding note in the output, or a note in the output with no decision
behind it (which would mean policy reached the emitted file by some path the decision log never saw).
The two are joined by the declaration's stable identity, never by name, because several passes may
still rename the very symbol a note is being written for.

Not every decision kind earns a note — a retyped signature, for instance, already states the new type
directly in the declaration, so a note beside it would only restate what the signature already says. A
kind is excluded from notes only when a reader could already explain the line from the line itself;
the moment a kind's *shape* changes in a way that stops being true, the exclusion has to be revisited,
because nothing in the pipeline notices an exclusion going stale on its own.

Placement follows a simple order: a comment already present in the source comes first, the porter note
comes last, and the member declaration itself comes right after. A note never opens or closes a
comment on its own — a value that would contain a comment delimiter is neutralised rather than
rejected outright, and a value that is only whitespace is quoted so it remains visible.

## Comments are part of the port

The original Java source's own comments are treated as first-class content to carry across, not as
disposable formatting. They are sliced verbatim out of the original source buffer rather than
re-printed through a parser's own rendering, because a parser's textual representation of a comment
routinely loses exact formatting a hand-authored comment relied on (aligned code samples inside a
comment, for instance). Every comment gets exactly one home in the output — a comment a more specific
part of the tree has already claimed is not also claimed by a coarser, surrounding harvest.

The check that verifies this compares actual source text against actual emitted text directly, never
the tree the engine built along the way: an independent pass re-reads the original Java, and searches
for each comment's normalised body inside what the run genuinely wrote to disk. Counting how many
comment nodes were harvested during translation proves nothing at all about what ended up in the
emitted file, which is the entire reason the check works this way instead of the cheaper alternative.
A comment failing to appear in the output is treated as a genuine engine gap, chargeable against the
engine, with exactly one exception: a member the port dropped on purpose, whose accompanying comment
was always meant to be dropped along with it.

## Provenance: where a line came from

Every tree node's `Origin` records the Java file and position that produced it; after the emitter
runs, the same node also carries the Scala position it was rendered at. This is what lets every
report described on this page and in
[unportable-constructs-and-refusals.md](unportable-constructs-and-refusals.md) answer "where did this
come from" and "where did this end up" without ever falling back to opening a generated file and
reading it by hand to find the answer.

## Licensing and where generated code lives

A port's output follows one fixed layout: build files and licence material (a `NOTICE` file and a
third-party licences file) sit at the top, hand-written shims and overrides for a library the engine
cannot fully derive sit under each module's ordinary `src/`, and everything the engine actually
emitted sits under that module's `src_managed/`, which is gitignored and removed whenever the build is
cleaned. This split is not a tidiness preference: emitted code is fully reproducible from the upstream
Java plus the port's own configuration, and is invalidated by any engine change, so keeping it out of
version control alongside hand-written code is what keeps a plain `git status` able to tell a genuine
decision apart from a regenerable artifact — which is the one thing every check and report described
on this page ultimately depends on being able to see clearly.
