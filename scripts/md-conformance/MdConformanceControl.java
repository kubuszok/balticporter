// The CONTROL half of `just md-conformance` — the CommonMark conformance census, over the UPSTREAM
// JAVA, as a number a lane reproduces rather than a number somebody counted by hand.
//
// WHY THIS FILE EXISTS AT ALL. `FullSpecTestCase.testSpecExample` is ONE `assertEquals` over the
// WHOLE rendered spec, so the suite answers pass/fail per spec FILE — 4 tests, and `OK (4 tests)`
// says nothing about how much of CommonMark either side implements. The per-EXAMPLE reading
// `PROGRESS.md` §10.6.7 quotes is that same comparison split at the spec format's own delimiter, and
// it was produced by hand. `CLAUDE.md` §5's rule is that a number is reproduced by a lane or it is
// not quoted.
//
// IT REPRODUCES THE SUITE'S OWN CONSTRUCTION, it does not approximate it. `create(location)` is
// `FullSpecTestCase`'s own factory, `readExamples()` is the reader's own drive, and
// `getFullSpec()`/`getExpectedFullSpec()` are the two strings the suite's own `assertEquals` takes.
// Everything this file adds is the SPLIT. A driver that built its own parser and renderer would be
// measuring something else and would agree with the suite only by luck.
//
// THE LOCATION COMES FROM THE `RESOURCE_LOCATION` FIELD AND NOT FROM `getSpecResourceLocation()`,
// which is the one deliberate difference and is the whole of `spec.0.29.txt`'s presence here.
// `FullOrigSpec029CoreTest.getSpecResourceLocation` returns `ResourceLocation.NULL` under
// `// FIX: implement 0.29 spec and enable test`, so java runs ZERO of its 649 examples; the public
// `RESOURCE_LOCATION` field is the location that method would return if the class were enabled. For
// the three LIVE suites the field IS what the method returns, so those three are the suite exactly.
// 0.29's numbers are reported apart and are never added into the 1,870 — see the `--- 0.29` block in
// `scripts/md-conformance.sh`.
//
// WHAT AN EXAMPLE BLOCK IS. `DumpSpecReader` appends every non-example line to BOTH builders and
// each example through `TestUtils.addSpecExample` — the RENDERED html into `sb`, the spec's own into
// `sbExp`. So the two strings differ only inside example blocks, and splitting both at
// `SpecReader.EXAMPLE_BREAK` (32 backticks; `EXAMPLE_START` is that string plus " example", so the
// split cuts at the open and at the close) yields chunk lists of equal length whose ODD indices are
// the example bodies. The even chunks are prose and MUST be equal — this file asserts that rather
// than assuming it, because a difference there would mean the two sides were not the same document
// and every per-example verdict under it would be an artefact of the alignment.

import com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec027CoreTest;
import com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec028CoreTest;
import com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec029CoreTest;
import com.vladsch.flexmark.core.test.util.renderer.FullOrigSpecCoreTest;
import com.vladsch.flexmark.test.util.DumpSpecReader;
import com.vladsch.flexmark.test.util.FullSpecTestCase;
import com.vladsch.flexmark.test.util.spec.ResourceLocation;
import com.vladsch.flexmark.test.util.spec.SpecExample;
import com.vladsch.flexmark.test.util.spec.SpecReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MdConformanceControl {
    // The four suites, in the order `PROGRESS.md` §10.6.7's table has them. The `live` flag is
    // UPSTREAM's own answer — whether `getSpecResourceLocation()` returns the location or NULL —
    // and it is what keeps 0.29 out of the conformance total.
    private static final String[] KEYS = { "spec.txt", "spec.0.27.txt", "spec.0.28.txt", "spec.0.29.txt" };

    private static FullSpecTestCase testCase(String key) {
        switch (key) {
            case "spec.txt":      return new FullOrigSpecCoreTest();
            case "spec.0.27.txt": return new FullOrigSpec027CoreTest();
            case "spec.0.28.txt": return new FullOrigSpec028CoreTest();
            case "spec.0.29.txt": return new FullOrigSpec029CoreTest();
            default: throw new IllegalArgumentException("no suite for " + key);
        }
    }

    private static ResourceLocation location(String key) {
        switch (key) {
            case "spec.txt":      return FullOrigSpecCoreTest.RESOURCE_LOCATION;
            case "spec.0.27.txt": return FullOrigSpec027CoreTest.RESOURCE_LOCATION;
            case "spec.0.28.txt": return FullOrigSpec028CoreTest.RESOURCE_LOCATION;
            case "spec.0.29.txt": return FullOrigSpec029CoreTest.RESOURCE_LOCATION;
            default: throw new IllegalArgumentException("no location for " + key);
        }
    }

    private static boolean live(String key) {
        return !"spec.0.29.txt".equals(key);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) { usage(); System.exit(2); }
        if ("dump".equals(args[0])) {
            if (args.length != 2) { usage(); System.exit(2); }
            dump(Paths.get(args[1]));
        } else if ("classify".equals(args[0])) {
            if (args.length != 4) { usage(); System.exit(2); }
            classify(Paths.get(args[1]), Paths.get(args[2]), args[3]);
        } else {
            usage();
            System.exit(2);
        }
    }

    private static void usage() {
        System.err.println("usage: MdConformanceControl dump <outDir>");
        System.err.println("       MdConformanceControl classify <controlDir> <portDir> <specKey>");
    }

    // ------------------------------------------------------------------------------------------
    // dump — drive every spec and write the three artifacts a later comparison needs.

    private static void dump(Path outDir) throws IOException {
        Files.createDirectories(outDir);

        System.out.println("spec resource     examples  passing  failing");
        int liveExamples = 0, livePassing = 0, liveFailing = 0;

        for (String key : KEYS) {
            FullSpecTestCase tc = testCase(key);
            DumpSpecReader reader = tc.create(location(key));
            reader.readExamples();

            String actual = reader.getFullSpec();
            String expected = reader.getExpectedFullSpec();
            List<SpecExample> examples = reader.getExamples();

            List<String> a = split(actual);
            List<String> e = split(expected);
            if (a.size() != e.size()) {
                throw new IllegalStateException(key + ": the two dumps split into " + a.size()
                        + " and " + e.size() + " chunks — they are not the same document");
            }
            if ((a.size() % 2) != 1) {
                throw new IllegalStateException(key + ": " + a.size()
                        + " chunks — an example is delimited by a PAIR of breaks, so the count is odd");
            }
            int count = (a.size() - 1) / 2;
            if (count != examples.size()) {
                throw new IllegalStateException(key + ": split found " + count
                        + " example blocks and the reader read " + examples.size());
            }
            for (int i = 0; i < a.size(); i += 2) {
                if (!a.get(i).equals(e.get(i))) {
                    throw new IllegalStateException(key + ": prose chunk " + i
                            + " differs between the rendered dump and the spec — the two are misaligned");
                }
            }

            StringBuilder status = new StringBuilder("# idx\tsection\texample\tverdict\n");
            int passing = 0;
            for (int i = 0; i < count; i++) {
                boolean ok = a.get(2 * i + 1).equals(e.get(2 * i + 1));
                if (ok) passing++;
                SpecExample ex = examples.get(i);
                status.append(i + 1).append('\t')
                        .append(ex.getSection() == null ? "" : ex.getSection()).append('\t')
                        .append(ex.getExampleNumber()).append('\t')
                        .append(ok ? "PASS" : "FAIL").append('\n');
            }

            write(outDir.resolve(key + ".actual"), actual);
            write(outDir.resolve(key + ".expected"), expected);
            write(outDir.resolve(key + ".status.tsv"), status.toString());

            System.out.printf("%-16s  %8d  %7d  %7d%s%n",
                    key, count, passing, count - passing, live(key) ? "" : "   (NOT RUN by the java suite)");

            if (live(key)) { liveExamples += count; livePassing += passing; liveFailing += count - passing; }
        }

        System.out.printf("%-16s  %8d  %7d  %7d%n", "THE THREE LIVE", liveExamples, livePassing, liveFailing);
        write(outDir.resolve("live-totals.tsv"),
                "examples\tpassing\tfailing\n" + liveExamples + "\t" + livePassing + "\t" + liveFailing + "\n");
    }

    // ------------------------------------------------------------------------------------------
    // classify — the java control's rendering against the PORT's, example by example, over ONE spec.
    //
    // Four verdicts, and the two that matter are the two the census cannot conflate: an example the
    // control renders right and the port does not is a PORT DEFECT; one they both get wrong is a
    // SPEC GAP — the rule flexmark itself never implemented — and the port is faithfully reproducing
    // its upstream. The other two are recorded because their absence is a claim too.

    private static void classify(Path controlDir, Path portDir, String key) throws IOException {
        List<String> expected = split(read(controlDir.resolve(key + ".expected")));
        List<String> control = split(read(controlDir.resolve(key + ".actual")));
        List<String> port = split(read(portDir.resolve(key + ".actual")));
        List<String[]> labels = labels(controlDir.resolve(key + ".status.tsv"));

        if (expected.size() != control.size() || expected.size() != port.size()) {
            throw new IllegalStateException(key + ": chunk counts differ — expected " + expected.size()
                    + ", control " + control.size() + ", port " + port.size());
        }
        int count = (expected.size() - 1) / 2;

        int agreeOk = 0, specGap = 0, portDefect = 0, portOnlyOk = 0;
        StringBuilder rows = new StringBuilder("# idx\tsection\texample\tclass\n");
        for (int i = 0; i < count; i++) {
            String exp = expected.get(2 * i + 1);
            boolean c = control.get(2 * i + 1).equals(exp);
            boolean p = port.get(2 * i + 1).equals(exp);
            String cls;
            if (c && p)        { cls = "BOTH-PASS";   agreeOk++; }
            else if (!c && !p) { cls = "SPEC-GAP";    specGap++; }
            else if (c)        { cls = "PORT-DEFECT"; portDefect++; }
            else               { cls = "PORT-ONLY-PASS"; portOnlyOk++; }

            String[] label = labels.get(i);
            rows.append(i + 1).append('\t').append(label[0]).append('\t').append(label[1])
                    .append('\t').append(cls).append('\n');

            if (!"BOTH-PASS".equals(cls)) {
                System.out.println("---- " + key + " example #" + (i + 1)
                        + "  (section '" + label[0] + "', number " + label[1] + ")  " + cls);
                System.out.println("  SPEC EXPECTS:"); System.out.println(indent(exp));
                System.out.println("  JAVA CONTROL:"); System.out.println(indent(control.get(2 * i + 1)));
                System.out.println("  PORT:");         System.out.println(indent(port.get(2 * i + 1)));
            }
        }
        write(controlDir.resolve(key + ".classification.tsv"), rows.toString());

        System.out.println();
        System.out.printf("%s: %d examples — BOTH-PASS %d, SPEC-GAP %d, PORT-DEFECT %d, PORT-ONLY-PASS %d%n",
                key, count, agreeOk, specGap, portDefect, portOnlyOk);
        if (portDefect > 0) {
            System.out.println("!! " + portDefect + " PORT DEFECT(S) — java renders these correctly and the port does not");
        }
    }

    private static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n", -1)) sb.append("    |").append(line).append('\n');
        return sb.toString();
    }

    private static List<String[]> labels(Path statusTsv) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String line : read(statusTsv).split("\n", -1)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            out.add(new String[] { f[1], f[2] });
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------

    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split(Pattern.quote(SpecReader.EXAMPLE_BREAK), -1)) out.add(s);
        return out;
    }

    private static void write(Path p, String s) throws IOException {
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
