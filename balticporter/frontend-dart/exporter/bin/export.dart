/// Baltic Porter Dart Exporter — Phase 3
///
/// Reads a Dart project via `package:analyzer`, extracts a Resolved AST (RAST)
/// with symbols, types, and resolved references, and writes deterministic JSON
/// to an output directory.
///
/// The exporter is deliberately dumb: it serializes what the Dart analyzer knows.
/// All lowering decisions live in the Scala frontend-dart module.
///
/// Usage:
///   dart run export.dart --project <path/to/pubspec.yaml> --out <dir> [--include <glob>]
///
/// Requires: Dart SDK (dart on PATH), package:analyzer dependency.

import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart' show sha256;

import 'package:analyzer/dart/analysis/analysis_context_collection.dart';
import 'package:analyzer/dart/analysis/results.dart';
import 'package:analyzer/dart/ast/ast.dart';
import 'package:analyzer/dart/ast/visitor.dart';
import 'package:analyzer/dart/element/element.dart';
import 'package:analyzer/dart/element/type.dart';
import 'package:analyzer/file_system/physical_file_system.dart';
import 'package:path/path.dart' as path;

/// RAST file for one Dart source file.
class RastFile {
  final int version = 1;
  final String filePath;
  final String sha256Hash;
  final List<Map<String, dynamic>> nodes;
  final Map<String, Map<String, dynamic>> symbols;
  final Map<String, Map<String, dynamic>> types;

  RastFile({
    required this.filePath,
    required this.sha256Hash,
    required this.nodes,
    required this.symbols,
    required this.types,
  });

  Map<String, dynamic> toJson() => {
    'version': version,
    'path': filePath,
    'sha256': sha256Hash,
    'nodes': nodes,
    'symbols': symbols,
    'types': types,
  };
}

/// Exports a resolved Dart compilation unit to RAST JSON.
class DartExporter {
  int _symbolCounter = 0;
  int _typeCounter = 0;
  final Map<Element, String> _symbolMap = {};
  final Map<String, Map<String, dynamic>> _symbols = {};
  final Map<DartType, String> _typeCache = {};
  final Map<String, Map<String, dynamic>> _types = {};

  RastFile exportUnit(ResolvedUnitResult unit) {
    _symbolMap.clear();
    _symbolCounter = 0;
    _typeCache.clear();
    _typeCounter = 0;
    _symbols.clear();
    _types.clear();

    final content = unit.content;
    final hash = sha256.convert(utf8.encode(content)).toString();

    final nodes = unit.unit.declarations.map(_visitNode).toList();

    return RastFile(
      filePath: unit.path,
      sha256Hash: hash,
      nodes: nodes,
      symbols: Map.from(_symbols),
      types: Map.from(_types),
    );
  }

  Map<String, dynamic> _visitNode(AstNode node) {
    final result = <String, dynamic>{
      'kind': node.runtimeType.toString(),
      'pos': [
        node.offset,
        node.end - node.offset,
      ],
    };

    // Symbol for declarations
    if (node is Declaration) {
      final element = node.declaredElement;
      if (element != null) {
        result['symbol'] = _internSymbol(element);
      }
    }

    // Type for expressions
    if (node is Expression) {
      final type = node.staticType;
      if (type != null) {
        result['type'] = _internType(type);
      }
    }

    // Identifier text
    if (node is SimpleIdentifier) {
      result['text'] = node.name;
      final element = node.staticElement;
      if (element != null) {
        result['resolvedSymbol'] = _internSymbol(element);
      }
    }

    // Literal values
    if (node is IntegerLiteral) result['value'] = node.value;
    if (node is DoubleLiteral) result['value'] = node.value;
    if (node is SimpleStringLiteral) result['value'] = node.value;
    if (node is BooleanLiteral) result['value'] = node.value;

    // Flags
    final flags = <String>[];
    if (node is FieldDeclaration) {
      if (node.isStatic) flags.add('static');
      if (node.fields.isFinal) flags.add('final');
      if (node.fields.isConst) flags.add('const');
      if (node.fields.isLate) flags.add('late');
    }
    if (node is MethodDeclaration) {
      if (node.isStatic) flags.add('static');
      if (node.isAbstract) flags.add('abstract');
      if (node.isGetter) flags.add('getter');
      if (node.isSetter) flags.add('setter');
      if (node.isOperator) flags.add('operator');
    }
    if (node is ClassDeclaration) {
      if (node.abstractKeyword != null) flags.add('abstract');
    }
    if (node is ConstructorDeclaration) {
      if (node.factoryKeyword != null) flags.add('factory');
      if (node.constKeyword != null) flags.add('const');
    }
    if (flags.isNotEmpty) result['flags'] = flags;

    // Dart-specific flags
    if (node is ConstructorDeclaration && node.factoryKeyword != null) {
      result['isFactory'] = true;
    }
    if (node is FieldDeclaration && node.fields.isLate) {
      result['isLate'] = true;
    }
    if (node is ExtensionDeclaration) {
      result['isExtension'] = true;
    }
    if (node is MixinDeclaration) {
      result['kind'] = 'MixinDeclaration';
    }
    if (node is ClassDeclaration && node.withClause != null) {
      result['mixins'] = node.withClause!.mixinTypes
          .map((t) => t.toSource())
          .toList();
    }

    // Children
    final children = <Map<String, dynamic>>[];
    node.visitChildren(_ChildVisitor(this, children));
    if (children.isNotEmpty) result['children'] = children;

    return result;
  }

  String _internSymbol(Element element) {
    return _symbolMap.putIfAbsent(element, () {
      final id = 's${_symbolCounter++}';
      _symbols[id] = _exportSymbol(element);
      return id;
    });
  }

  Map<String, dynamic> _exportSymbol(Element element) {
    final flags = <String>[];
    if (element is ClassElement && element.isAbstract) flags.add('abstract');
    if (element is ExecutableElement && element.isStatic) flags.add('static');
    if (element is PropertyAccessorElement && element.isGetter) flags.add('getter');
    if (element is PropertyAccessorElement && element.isSetter) flags.add('setter');
    if (element is ConstructorElement && element.isFactory) flags.add('factory');

    final result = <String, dynamic>{
      'name': element.name ?? '',
      'flags': flags,
    };

    final type = element is ExecutableElement ? element.returnType : null;
    if (type != null) {
      result['declarationType'] = _internType(type);
    }

    final enclosing = element.enclosingElement;
    if (enclosing != null && enclosing is! CompilationUnitElement) {
      result['parent'] = _internSymbol(enclosing);
    }

    if (element is ParameterElement) {
      result['isNullable'] = element.type.nullabilitySuffix.toString().contains('question');
    }

    return result;
  }

  String _internType(DartType type) {
    return _typeCache.putIfAbsent(type, () {
      final id = 't${_typeCounter++}';
      _types[id] = _exportType(type);
      return id;
    });
  }

  Map<String, dynamic> _exportType(DartType type) {
    final text = type.getDisplayString(withNullability: true);
    final isNullable = type.nullabilitySuffix.toString().contains('question');

    if (type is InterfaceType) {
      final result = <String, dynamic>{
        'kind': type.element.name,
        'text': text,
        'isNullable': isNullable,
      };
      if (type.typeArguments.isNotEmpty) {
        result['typeArguments'] = type.typeArguments.map(_internType).toList();
      }
      return result;
    }

    if (type is FunctionType) {
      return {
        'kind': 'function',
        'text': text,
        'isNullable': isNullable,
        'parameters': type.parameters.map((p) => {
          'name': p.name,
          'type': _internType(p.type),
          'isNamed': p.isNamed,
          'isOptional': p.isOptional,
          'hasDefault': p.hasDefaultValue,
        }).toList(),
        'returnType': _internType(type.returnType),
      };
    }

    if (type is TypeParameterType) {
      return {
        'kind': 'typeParameter',
        'text': text,
        'isNullable': isNullable,
        if (type.bound != null) 'bound': _internType(type.bound),
      };
    }

    if (type.isDartCoreInt) return {'kind': 'int', 'text': text, 'isNullable': isNullable};
    if (type.isDartCoreDouble) return {'kind': 'double', 'text': text, 'isNullable': isNullable};
    if (type.isDartCoreNum) return {'kind': 'num', 'text': text, 'isNullable': isNullable};
    if (type.isDartCoreBool) return {'kind': 'bool', 'text': text, 'isNullable': isNullable};
    if (type.isDartCoreString) return {'kind': 'String', 'text': text, 'isNullable': isNullable};
    if (type.isVoid) return {'kind': 'void', 'text': text};
    if (type is NeverType) return {'kind': 'Never', 'text': text};
    if (type.isDynamic) return {'kind': 'dynamic', 'text': text};

    return {'kind': 'other', 'text': text, 'isNullable': isNullable};
  }
}

class _ChildVisitor extends GeneralizingAstVisitor<void> {
  final DartExporter exporter;
  final List<Map<String, dynamic>> children;

  _ChildVisitor(this.exporter, this.children);

  @override
  void visitNode(AstNode node) {
    children.add(exporter._visitNode(node));
  }
}

void main(List<String> args) async {
  String? project;
  String? outDir;
  final includes = <String>[];

  for (var i = 0; i < args.length; i++) {
    if (args[i] == '--project' && i + 1 < args.length) {
      project = args[++i];
    } else if (args[i] == '--out' && i + 1 < args.length) {
      outDir = args[++i];
    } else if (args[i] == '--include' && i + 1 < args.length) {
      includes.add(args[++i]);
    }
  }

  if (project == null || outDir == null) {
    stderr.writeln('Usage: dart run export.dart --project <pubspec.yaml> --out <dir> [--include <glob>]');
    exit(1);
  }

  final projectDir = path.dirname(path.absolute(project!));
  final collection = AnalysisContextCollection(
    includedPaths: [projectDir],
    resourceProvider: PhysicalResourceProvider.INSTANCE,
  );

  final outPath = path.absolute(outDir!);
  Directory(outPath).createSync(recursive: true);

  var count = 0;
  for (final context in collection.contexts) {
    for (final filePath in context.contextRoot.analyzedFiles()) {
      if (!filePath.endsWith('.dart')) continue;
      if (filePath.contains('/test/') || filePath.contains('_test.dart')) continue;

      final result = await context.currentSession.getResolvedUnit(filePath);
      if (result is! ResolvedUnitResult) continue;

      final exporter = DartExporter();
      final rast = exporter.exportUnit(result);

      final relPath = path.relative(filePath, from: projectDir)
          .replaceAll('.dart', '.dart.rast.json');
      final outputFile = File(path.join(outPath, relPath));
      outputFile.parent.createSync(recursive: true);
      outputFile.writeAsStringSync(
        const JsonEncoder.withIndent('  ').convert(rast.toJson()) + '\n',
      );
      count++;
    }
  }

  print('[dart-exporter] Exported $count file(s) to $outPath');
}
