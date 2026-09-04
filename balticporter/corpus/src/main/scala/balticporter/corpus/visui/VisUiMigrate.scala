package balticporter.corpus.visui

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **VisUI**'s `ui/` module (162 files — a scene2d widget toolkit) through the TIR.
  * A DEPENDENT port: `gdx/src` is a RESOLUTION root, and the policy is [[LibgdxPolicy.core]]
  * EXTENDED, not restated (CLAUDE.md §1.5). Scope is `ui/` only; `usl/` is a NAMED follow-up
  * (`PROGRESS.md` §10.9). No test source set this wave — behavioural evidence is DIFFERENTIAL
  * against the reference hand port's 72-case suite (`PROGRESS.md` §10.9). */
object VisUiMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val upstream = repoRoot.resolve("../sge/original-src/vis-ui").normalize
    val base     = upstream.resolve("ui/src/main/java").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-visui",
      portRoot  = repoRoot.resolve("ported/sge-visui"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(VisUiPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "VisUI",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "vis-ui/ui/src/main/java",
        sourceRoot       = base.toString,
        // TWO LICENCE REGIMES (CLAUDE.md §4.57): (a) Apache-2.0 per-file header, covering 161/162
        // in-scope sources (`layout/FlowGroup.java` gets a NAMES-not-INCLUDES banner instead);
        // (b) CC BY-ND 3.0 on the shipped ICONS (`ui/NOTICE`/`ui/icons-license`) — a
        // NO-DERIVATIVES licence no harvest or banner can reach, baked into `uiskin.atlas`/`.png`
        // and loaded by classpath string. Apache-2.0 §4(d) makes declaring NOTICE unconditional.
        notices          = List(upstream.resolve("LICENSE"),
                                upstream.resolve("ui/NOTICE"),
                                upstream.resolve("ui/icons-license")),
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just visui-measure",
    ).execute()

/** VisUI's per-library policy -- a DEPENDENT of libGDX core's, deliberately almost empty.
  * `dropTypes`/`dropMethods`/`packageRenames`/every signature-affecting phase are
  * INHERITED, not restated (CLAUDE.md §1.5); `inject` is NOT inherited (exactly one module
  * ships each replacement file). This wave adds a namespace claim, ONE rename and the
  * base-surface residue check, nothing else. */
object VisUiPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-visui",
      // THE CLAIM IS THE MODULE'S SCOPE, not the upstream organisation's namespace:
      // com.kotcrab.vis covers com.kotcrab.vis.usl too, which this port does not emit.
      governs = Set("com.kotcrab.vis.ui"),
      // ONE PAIR, UNIFORM, verified 1:1 against the reference port's tree (22 sub-packages, name
      // for name). WHAT THE RENAME DOES NOT REACH: VisUI loads its own skin/i18n/shader
      // resources through HARDCODED CLASSPATH STRINGS — a rename moves SYMBOLS, never a string
      // literal (§4.56) — so the 22 resources below ship at their UPSTREAM classpath path,
      // verified byte-identical to the reference hand port's own layout.
      packageRenames = Map("com.kotcrab.vis.ui" -> "sge.visui"),
      // THE 22 RESOURCES THE EMITTED CODE ASKS FOR, COPIED VERBATIM (`DESIGN.md` §8.22). The
      // other 2 files under that root (`robovm.xml`, `vis-ui.gwt.xml`) belong to the UPSTREAM
      // BUILD, not the library — confirmed against upstream's own GWT resource enumeration,
      // which lists exactly these 22. Most are reached through indirection (another resource's
      // content, a completed name, a directory+file literal) that no phase can walk.
      resources = List(balticporter.core.ResourceTree(
        repoRoot.resolve("../sge/original-src/vis-ui/ui/src/main/resources").normalize,
        List(
          // the i18n bundles — `Locales` names each of these base names and libGDX's `I18NBundle`
          // appends the `.properties`.
          "com/kotcrab/vis/ui/i18n/ButtonBar.properties",
          "com/kotcrab/vis/ui/i18n/ColorPicker.properties",
          "com/kotcrab/vis/ui/i18n/Common.properties",
          "com/kotcrab/vis/ui/i18n/Dialogs.properties",
          "com/kotcrab/vis/ui/i18n/FileChooser.properties",
          "com/kotcrab/vis/ui/i18n/TabbedPane.properties",
          // the two SKINS. `VisUI.SkinScale` names the `.json` of each; the `.json` names the
          // `.atlas` and the two `.fnt`, and the `.atlas` names the `.png`.
          "com/kotcrab/vis/ui/skin/x1/default.fnt",
          "com/kotcrab/vis/ui/skin/x1/font-small.fnt",
          "com/kotcrab/vis/ui/skin/x1/uiskin.atlas",
          "com/kotcrab/vis/ui/skin/x1/uiskin.json",
          "com/kotcrab/vis/ui/skin/x1/uiskin.png",
          "com/kotcrab/vis/ui/skin/x2/default.fnt",
          "com/kotcrab/vis/ui/skin/x2/font-small.fnt",
          "com/kotcrab/vis/ui/skin/x2/uiskin.atlas",
          "com/kotcrab/vis/ui/skin/x2/uiskin.json",
          "com/kotcrab/vis/ui/skin/x2/uiskin.png",
          // the colour picker's shaders, whose paths the widget builds from a directory literal.
          "com/kotcrab/vis/ui/widget/color/internal/checkerboard.frag",
          "com/kotcrab/vis/ui/widget/color/internal/default.vert",
          "com/kotcrab/vis/ui/widget/color/internal/hsv.frag",
          "com/kotcrab/vis/ui/widget/color/internal/palette.frag",
          "com/kotcrab/vis/ui/widget/color/internal/rgb.frag",
          "com/kotcrab/vis/ui/widget/color/internal/verticalBar.frag",
        ))),
      // NOTHING IS WRITTEN HERE FOR `AsyncTask` (a DECISION, not an omission): upstream's
      // `com.kotcrab.vis.ui.util.async.AsyncTask` and libGDX's own share a simple name and
      // nothing else (verified: neither imports the other). §4.56's trap, not a construct
      // with two answers -- no rename/substitution/redirect may reach across on the name
      // alone.
      surface = List(
        // THE ONE MEMBER THIS LIBRARY HAS TO MOVE (`PROGRESS.md` §10.9.7 family 2, D13):
        // libGDX's Disposable->AutoCloseable redirect renames `dispose -> close`, and two of
        // VisUI's own Disposable implementors already declare `close()` — `OnCollision.Refuse`
        // correctly refused the component. VisUI's OWN member moves to `closeWindow` instead; the
        // reference hand port keeps `close()` by not retargeting `Disposable` at all.
        balticporter.transform.MemberRenameTransform(Map(
          "com.kotcrab.vis.ui.widget.VisWindow#close" -> "closeWindow",
        )),
        // THE CONTEXT SEAM'S EXIT: an EXTENSION of the base's `Gdx` holder, never a second
        // holder (`PROGRESS.md` §10.9.7 family 1, §10.9.10; `ENGINE-LIMITS.md` CT8). `cache` on
        // `VisUI` (all-`static`, clause on its METHODS, matching the hand port's `sgeInstance`
        // name). `selfSupplied` on three enums the threading could not sign: an enum's primary
        // IS its constructor, so these take the context without a parameter, from `sgeInstance`.
        balticporter.transform.GlobalsToImplicitsTransform(Nil, List(
          balticporter.transform.ContextHolderExtension(
            holder = "com.badlogic.gdx.Gdx",
            cache  = Map("com.kotcrab.vis.ui.VisUI" -> "sgeInstance"),
            // NO `sites` ENTRY FOR `Draggable#BLOCKER` — MEASURED, not assumed (CT11): `lazy-init`
            // fires but only MOVES the error to the static block reading the now-context-taking
            // field, which has no clause of its own (8 -> 8 errors, 9 member digests, zero net).
            // Stays a counted `unsuppliable-use`. The other EIGHT sites arrived once the graph
            // learned a `private` member is not inherited (JLS 8.2), unfreezing `Enum#getBundle`.
            selfSupplied = Map(
              "com.kotcrab.vis.ui.VisUI$SkinScale"                          -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.ButtonBar$ButtonType"              -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.building.utilities.layouts.TableLayout"   -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.Locales$CommonText"                       -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.util.dialog.Dialogs$Text"                 -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.color.internal.ColorPickerText"    -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.file.internal.FileChooserText"     -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane$Text"        -> "sge.visui.VisUI.sgeInstance",
            ),
          ),
        // --- 3.1aq: requiredGivens for generic classes constructing retarget targets
        ), requiredGivens = Map(
          "com.kotcrab.vis.ui.util.adapter.AbstractListAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.util.adapter.CachedItemAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.widget.spinner.ArraySpinnerModel" -> "lowlevel.MkArray",
          // 3.1as: subclasses of AbstractListAdapter -- the transitive closure does not
          // reach subclasses through `extends` (only Tree.New), so direct entries are needed.
          "com.kotcrab.vis.ui.util.adapter.ArrayAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.util.adapter.ArrayListAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.util.adapter.SimpleListAdapter" -> "lowlevel.MkArray",
        )),
        // DEPENDENT SEEDS for the base's `Align` opaque family (same `MergeablePolicy`
        // merge VfxPolicy uses): propagation follows pure-move flows and does NOT follow a
        // bitwise test, so these three fields are only reachable by seeding them directly.
        new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
          fqn        = "com.badlogic.gdx.utils.Align",
          target     = balticporter.tir.OpaqueSpec.Target.Existing(
            typeFqn    = "sge.utils.Align",
            wrapName   = "apply",
            unwrapName = "toInt",
          ),
          hints      = Set(
            // `Alignment` enum's sole field — `private final int alignment`, initialised from
            // `Align.center`, `Align.top`, etc. Seeding it causes propagation to reach the
            // constructor parameter, `getAlignment()`, and the enum constant arguments.
            "com.kotcrab.vis.ui.building.utilities.Alignment#alignment",
            // `ToastManager`'s own field — `protected int alignment = Align.topRight`, with
            // `setAlignment(int)` and `getAlignment()` beside it.
            "com.kotcrab.vis.ui.util.ToastManager#alignment",
            // `VisTextField`'s own field — `private int textHAlign = Align.left`, with
            // `setAlignment(int)` writing it and layout code reading it through bit tests.
            "com.kotcrab.vis.ui.widget.VisTextField#textHAlign",
          ),
          underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        )),
        // 3.1ba: body substitutions, THREE FAMILIES — (1) VisTextField keyboard.show(boolean) ->
        // show(TextField)/close(): vendored libGDX 1.14.1 changed the signature after VisUI's
        // 1.14.0 (§3.5); (2) Dialogs.getStackTrace: CharArray.append(Object) has no
        // DynamicArray[Char] equivalent post-retarget, so the hand port's StringBuilder rewrite
        // is carried instead; (3) Draggable#BLOCKER (CT11): counted `unsuppliable-use`.
        new balticporter.transform.MethodBodyTransform(Map(
          // --- (1) keyboard.show(boolean) -> show(TextField) / close() ---
          "com.kotcrab.vis.ui.widget.VisTextField#focusField" ->
            """{
              |  if (this.disabled$field) {
              |    return
              |  } else ()
              |  val stage: sge.scenes.scene2d.Stage = this.stage.orNull
              |  sge.visui.FocusManager.switchFocus(stage, this)
              |  this.cursorPosition = 0
              |  this.selectionStart$field = 0
              |  this.calculateOffsets()
              |  if (stage != null) {
              |    stage.setKeyboardFocus(lowlevel.Nullable(this))
              |  } else ()
              |  this.keyboard.show(this)
              |  this.hasSelection = true
              |}""".stripMargin,
          "com.kotcrab.vis.ui.widget.VisTextField#next(boolean)" ->
            """{
              |  val stage: sge.scenes.scene2d.Stage = this.stage.orNull
              |  if (stage == null) {
              |    return
              |  } else ()
              |  this.parent.get.localToStageCoordinates(sge.visui.widget.VisTextField.tmp1.set(this.x, this.y))
              |  var textField: sge.visui.widget.VisTextField = this.findNextTextField(stage.actors, null, sge.visui.widget.VisTextField.tmp2, sge.visui.widget.VisTextField.tmp1, up)
              |  if (textField == null) {
              |    if (up) {
              |      sge.visui.widget.VisTextField.tmp1.set(java.lang.Float.MIN_VALUE, java.lang.Float.MIN_VALUE)
              |    } else {
              |      sge.visui.widget.VisTextField.tmp1.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
              |    }
              |    textField = this.findNextTextField(this.stage.get.actors, null, sge.visui.widget.VisTextField.tmp2, sge.visui.widget.VisTextField.tmp1, up)
              |  } else ()
              |  if (textField != null) {
              |    textField.focusField()
              |    textField.cursorPosition = textField.text.length()
              |  } else {
              |    this.keyboard.close()
              |  }
              |}""".stripMargin,
          "com.kotcrab.vis.ui.widget.VisTextField$TextFieldClickListener#touchDown(InputEvent,float,float,int,int)" ->
            """{
              |  if (!super.touchDown(event, x, y, pointer, button)) {
              |    return false
              |  } else ()
              |  if ((pointer == 0) && (button != 0)) {
              |    return false
              |  } else ()
              |  if (VisTextField.this.disabled$field) {
              |    return true
              |  } else ()
              |  val stage: sge.scenes.scene2d.Stage = VisTextField.this.stage.orNull
              |  sge.visui.FocusManager.switchFocus(stage, VisTextField.this)
              |  this.setCursorPosition(x, y)
              |  VisTextField.this.selectionStart$field = VisTextField.this.cursor
              |  if (stage != null) {
              |    stage.setKeyboardFocus(lowlevel.Nullable(VisTextField.this))
              |  } else ()
              |  if (VisTextField.this.readOnly$field == false) {
              |    VisTextField.this.keyboard.show(VisTextField.this)
              |  } else ()
              |  VisTextField.this.hasSelection = true
              |  return true
              |}""".stripMargin,
          // --- (2) Dialogs.getStackTrace: StringBuilder instead of DynamicArray[Char] ---
          // Dialogs.getStackTrace(Throwable): DynamicArray[Char].toString would print
          // "[a, b, c]"; reconstruct via new String(builder.toArray) instead.
          "com.kotcrab.vis.ui.util.dialog.Dialogs#getStackTrace(Throwable)" ->
            """{
              |  val builder: lowlevel.util.DynamicArray[scala.Char] = lowlevel.util.DynamicArray.apply[scala.Char]()
              |  sge.visui.util.dialog.Dialogs.getStackTrace(throwable, builder)
              |  return new java.lang.String(builder.toArray)
              |}""".stripMargin,
          // Dialogs.getStackTrace(Throwable,CharArray): CharArray.append(Object) has no
          // DynamicArray[Char] equivalent -- convert each element to a char array and addAll.
          "com.kotcrab.vis.ui.util.dialog.Dialogs#getStackTrace(Throwable,CharArray)" ->
            """{
              |  val msg: java.lang.String = throwable.getMessage()
              |  if (msg != null) {
              |    { val bpCa = msg.toString.toCharArray; builder.addAll(bpCa, 0, bpCa.length) }
              |    { val bpCa = "\n\n".toString.toCharArray; builder.addAll(bpCa, 0, bpCa.length) }
              |  } else ()
              |  for (element <- throwable.getStackTrace()) {
              |    { val bpCa = element.toString.toCharArray; builder.addAll(bpCa, 0, bpCa.length) }
              |    { val bpCa = "\n".toString.toCharArray; builder.addAll(bpCa, 0, bpCa.length) }
              |  }
              |  if (throwable.getCause() != null) {
              |    { val bpCa = "\nCaused by: ".toString.toCharArray; builder.addAll(bpCa, 0, bpCa.length) }
              |    sge.visui.util.dialog.Dialogs.getStackTrace(throwable.getCause(), builder)
              |  } else ()
              |}""".stripMargin,
        )),
        // LAST, deliberately (as AshleyPolicy/GdxAiPolicy/TextraTypistPolicy): reads what
        // the BASE actually emitted; must run after any seam re-pointing such a reference.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-visui. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/visui/src/main/scala").normalize))),
    ))
