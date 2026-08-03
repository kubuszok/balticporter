/*
 * The hand-written half of the libgdx-screenmanager port (CLAUDE.md §5.5).
 *
 * Derived from guacamole — https://github.com/crykn/guacamole, v0.3.6
 * Original author: damios
 * Licensed under the Apache License, Version 2.0
 *
 * ==Why these files exist, and what would delete them==
 * libgdx-screenmanager's `build.gradle` declares
 * `api "com.github.crykn.guacamole:gdx:v0.3.6"  // is exposed because of NestableFrameBuffer`.
 * guacamole is a SEPARATE upstream library: this corpus resolves against its jar so the frontend
 * understands screenmanager's sources, but converts none of its compilation units, so the ten
 * types screenmanager reaches would emit as `de.damios.guacamole.…` — Scala names nothing in
 * reach declares.
 *
 * `TypeRedirectTransform` (CLAUDE.md §1(b)) re-points every one of them at the FQNs below; see
 * `balticporter.corpus.screens.ScreensPolicy.guacamole` for the table. These are ordinary Scala
 * the port ships, and they are HAND-WRITTEN — a statement about scope, not about quality. The day
 * guacamole becomes a corpus port of its own, both the table and this directory are deleted and
 * the emitted references follow that port's own package rename instead.
 *
 * Each file below states what it reproduces and where it deliberately does not.
 */
package sge.screen.guacamole
