// Links every class of one jar against a classpath the way the JVM would on first use, and prints
// each reference that would throw: a missing class, method or field, a static/instance or
// class/interface mismatch, or an abstract member a concrete class no longer implements.
//
//   java scripts/LinkCheck.java <subject.jar> <classpath>
//
// Needs JDK 24+ (java.lang.classfile). Exit 0 when everything links, 1 when something does not.
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.zip.ZipFile;

public class LinkCheck {
  static final Map<String, ZipFile> index = new HashMap<>();
  static final Map<String, Optional<ClassModel>> models = new HashMap<>();
  static final Map<String, String> problems = new TreeMap<>();

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("usage: java LinkCheck.java <subject.jar> <classpath>");
      System.exit(64);
    }
    for (String entry : args[1].split(java.io.File.pathSeparator)) {
      if (entry.endsWith(".jar")) indexJar(entry, false);
    }
    List<String> subject = indexJar(args[0], true);
    Set<String> own = new HashSet<>(subject);
    for (String name : subject) {
      ClassModel cm = model(name).orElseThrow();
      checkPool(cm, own);
      checkInstructions(cm, own);
      checkAbstracts(cm, own);
    }
    problems.forEach((problem, from) -> System.out.println(problem + "   (referenced from " + dotted(from) + ")"));
    System.out.println(subject.size() + " classes linked, " + problems.size() + " broken references");
    System.exit(problems.isEmpty() ? 0 : 1);
  }

  /** Indexes a jar's classes; the subject jar wins over the classpath. */
  static List<String> indexJar(String path, boolean subject) throws IOException {
    ZipFile zip = new ZipFile(path);
    List<String> names = new ArrayList<>();
    zip.stream().filter(e -> e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/"))
        .forEach(e -> {
          String name = e.getName().substring(0, e.getName().length() - ".class".length());
          if (subject || !index.containsKey(name)) index.put(name, zip);
          if (!name.endsWith("module-info")) names.add(name);
        });
    return names;
  }

  /** The class, from the indexed jars or else the running JDK; empty when it exists nowhere. */
  static Optional<ClassModel> model(String name) {
    return models.computeIfAbsent(name, n -> {
      try {
        ZipFile zip = index.get(n);
        InputStream in = zip != null ? zip.getInputStream(zip.getEntry(n + ".class"))
            : ClassLoader.getSystemResourceAsStream(n + ".class");
        if (in == null) return Optional.empty();
        try (in) { return Optional.of(ClassFile.of().parse(in.readAllBytes())); }
      } catch (IOException e) { throw new UncheckedIOException(e); }
    });
  }

  static String dotted(String internal) { return internal.replace('/', '.'); }

  static String elementClass(String name) {
    if (!name.startsWith("[")) return name;
    String el = name.replaceFirst("^\\[+", "");
    return el.startsWith("L") ? el.substring(1, el.length() - 1) : null;
  }

  static boolean isInterface(ClassModel cm) { return cm.flags().has(AccessFlag.INTERFACE); }

  /** Every class and interface above {@code name}, itself first; a missing one is recorded and skipped. */
  static List<ClassModel> ancestry(String name, String from) {
    List<ClassModel> out = new ArrayList<>();
    Deque<String> todo = new ArrayDeque<>(List.of(name));
    Set<String> seen = new HashSet<>();
    while (!todo.isEmpty()) {
      String n = todo.poll();
      if (!seen.add(n)) continue;
      Optional<ClassModel> m = model(n);
      if (m.isEmpty()) {
        problems.putIfAbsent("NoClassDefFoundError: " + dotted(n), from);
        continue;
      }
      out.add(m.get());
      m.get().superclass().ifPresent(s -> todo.add(s.asInternalName()));
      m.get().interfaces().forEach(i -> todo.add(i.asInternalName()));
    }
    return out;
  }

  static Optional<MethodModel> findMethod(String owner, String name, String type, String from) {
    for (ClassModel cm : ancestry(owner, from)) {
      String cls = cm.thisClass().asInternalName();
      boolean polymorphic = cls.equals("java/lang/invoke/MethodHandle") || cls.equals("java/lang/invoke/VarHandle");
      for (MethodModel m : cm.methods()) {
        if (m.methodName().equalsString(name) && (polymorphic || m.methodType().equalsString(type))) return Optional.of(m);
      }
      if (name.equals("<init>") || name.equals("<clinit>")) break;
    }
    return Optional.empty();
  }

  static Optional<FieldModel> findField(String owner, String name, String type, String from) {
    for (ClassModel cm : ancestry(owner, from)) {
      for (FieldModel f : cm.fields()) {
        if (f.fieldName().equalsString(name) && f.fieldType().equalsString(type)) return Optional.of(f);
      }
    }
    return Optional.empty();
  }

  static String member(MemberRefEntry ref) {
    return dotted(ref.owner().asInternalName()) + "." + ref.name().stringValue() + ref.type().stringValue();
  }

  /** Every class and member the constant pool names outside the subject jar, lambda targets included. */
  static void checkPool(ClassModel cm, Set<String> own) {
    String from = cm.thisClass().asInternalName();
    for (PoolEntry e : cm.constantPool()) {
      if (e instanceof ClassEntry c) {
        String n = elementClass(c.asInternalName());
        if (n != null && !own.contains(n) && model(n).isEmpty())
          problems.putIfAbsent("NoClassDefFoundError: " + dotted(n), from);
      } else if (e instanceof MemberRefEntry ref) {
        String owner = ref.owner().asInternalName();
        if (owner.startsWith("[") || own.contains(owner)) continue;
        Optional<ClassModel> om = model(owner);
        if (om.isEmpty()) {
          problems.putIfAbsent("NoClassDefFoundError: " + dotted(owner), from);
        } else if (ref instanceof FieldRefEntry) {
          if (findField(owner, ref.name().stringValue(), ref.type().stringValue(), from).isEmpty())
            problems.putIfAbsent("NoSuchFieldError: " + member(ref), from);
        } else {
          if ((ref instanceof InterfaceMethodRefEntry) != isInterface(om.get()))
            problems.putIfAbsent("IncompatibleClassChangeError: " + member(ref) + " (class/interface changed)", from);
          else if (findMethod(owner, ref.name().stringValue(), ref.type().stringValue(), from).isEmpty())
            problems.putIfAbsent("NoSuchMethodError: " + member(ref), from);
        }
      }
    }
  }

  /** A resolved member whose static-ness no longer matches the instruction that uses it. */
  static void checkInstructions(ClassModel cm, Set<String> own) {
    String from = cm.thisClass().asInternalName();
    for (MethodModel mm : cm.methods()) {
      mm.code().ifPresent(code -> {
        for (CodeElement el : code) {
          if (el instanceof InvokeInstruction inv && !own.contains(inv.owner().asInternalName())
              && !inv.owner().asInternalName().startsWith("[")) {
            findMethod(inv.owner().asInternalName(), inv.name().stringValue(), inv.type().stringValue(), from)
                .ifPresent(m -> {
                  boolean wantsStatic = inv.opcode() == Opcode.INVOKESTATIC;
                  if (wantsStatic != m.flags().has(AccessFlag.STATIC))
                    problems.putIfAbsent("IncompatibleClassChangeError: " + member(inv.method())
                        + (wantsStatic ? " is no longer static" : " became static"), from);
                });
          } else if (el instanceof FieldInstruction fi && !own.contains(fi.owner().asInternalName())) {
            findField(fi.owner().asInternalName(), fi.name().stringValue(), fi.type().stringValue(), from)
                .ifPresent(f -> {
                  boolean wantsStatic = fi.opcode() == Opcode.GETSTATIC || fi.opcode() == Opcode.PUTSTATIC;
                  if (wantsStatic != f.flags().has(AccessFlag.STATIC))
                    problems.putIfAbsent("IncompatibleClassChangeError: " + member(fi.field())
                        + (wantsStatic ? " is no longer static" : " became static"), from);
                });
          }
        }
      });
    }
  }

  /** A concrete subject class must implement every abstract member its external supertypes declare. */
  static void checkAbstracts(ClassModel cm, Set<String> own) {
    if (isInterface(cm) || cm.flags().has(AccessFlag.ABSTRACT)) return;
    String from = cm.thisClass().asInternalName();
    Map<String, String> abstracts = new LinkedHashMap<>();
    Set<String> concrete = new HashSet<>();
    for (ClassModel a : ancestry(from, from)) {
      String cls = a.thisClass().asInternalName();
      for (MethodModel m : a.methods()) {
        if (m.flags().has(AccessFlag.STATIC) || m.methodName().stringValue().startsWith("<")) continue;
        String key = m.methodName().stringValue() + m.methodType().stringValue();
        if (m.flags().has(AccessFlag.ABSTRACT)) {
          if (!own.contains(cls)) abstracts.putIfAbsent(key, dotted(cls) + "." + key);
        } else concrete.add(key);
      }
    }
    abstracts.forEach((key, where) -> {
      if (!concrete.contains(key))
        problems.putIfAbsent("AbstractMethodError: " + dotted(from) + " does not implement " + where, from);
    });
  }
}
