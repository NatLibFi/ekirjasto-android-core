package org.nypl.simplified.tests.books.audio

/*
 * TODO (audiobook 24.x migration): The original unit tests for the audiobook manifest
 * fulfillment / parse / license-check strategy were removed because audiobook 24.0.0 reworked the
 * underlying library internals (PlayerManifest / ManifestFulfilled / ManifestUnparsed, the
 * RxJava 1 -> 2 event change, and the engine-provider API). The test fixtures need rebuilding
 * against the new APIs. The real behaviour is exercised by end-to-end device QA; the audiobook-24
 * migration notes are in memory/project_readium_3x_migration.md.
 */

class AudioBookManifestStrategyTest
