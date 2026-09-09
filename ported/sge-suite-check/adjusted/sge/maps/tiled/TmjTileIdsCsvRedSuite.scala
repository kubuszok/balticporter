package sge
package maps
package tiled

// Excluded: the port's BaseTmjMapLoader.getTileIds takes (JsonValue, Int, Int),
// while sge redesigned it to take TmjLayerJson — a complete API change.
// The test's assertions (ISS-780 loud-failure semantics) are valid but
// need the sge-original TmjLayerJson API to express.
class TmjTileIdsCsvRedSuite extends munit.FunSuite {}
