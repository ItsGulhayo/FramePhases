package uz.ttpu.framephases

/*
 ═══════════════════════════════════════════════════════════════
  QUESTION 1 — The three phases of a frame
 ═══════════════════════════════════════════════════════════════

  (a) Order and outputs:
      1. Composition  → produces a UI tree (list of layout nodes describing what to show)
      2. Layout       → assigns sizes and positions (coordinates) to every node
      3. Drawing      → renders pixels to the canvas (the actual visual output)

  (b) The Layout phase has two steps:
      1. Measurement — each node measures its children and decides its own size
      2. Placement   — each node places its children at specific (x, y) positions

  (c) LazyColumn exception:
      LazyColumn only composes the children that are currently visible. To know
      which items are visible, it needs to know its own size first — which comes
      from the parent's layout phase. So the composition of LazyColumn's children
      depends on its parent's layout result.
      Example: a LazyColumn showing a contacts list — if the screen is 1920px tall
      and each item is 64px, only ~30 items are composed, not all 500.

  (d) Compose is fast because of intelligent skipping:
      The mechanism is called "recomposition scope invalidation" (smart
      recomposition). Compose tracks which state each phase reads; when a state
      changes, only the scopes that actually read that state are re-executed.
      Phases and composables that did not read the changed state are skipped.

 ═══════════════════════════════════════════════════════════════
  QUESTION 2 — Walk the layout algorithm
 ═══════════════════════════════════════════════════════════════

  (a) Measure and place events (depth-first):
      1. measure Row
      2.   measure Image         → 48×48
      3.   measure Column
      4.     measure Text A      → 100×20
      5.     measure Text B      → 80×20
      6.   Column decides size   → 100×40
      7. Row decides size        → 148×48
      8. place Row
      9.   place Image at (0, 0)
      10.  place Column at (48, 0)
      11.    place Text A at (0, 0) relative to Column = (48, 0) relative to Row
      12.    place Text B at (0, 20) relative to Column = (48, 20) relative to Row

  (b) Final sizes and positions (relative to Row's top-left):
      Column: width = max(100, 80) = 100,  height = 20 + 20 = 40
      Row:    width = 48 + 100 = 148,      height = max(48, 40) = 48

      Node       Size      Position
      Row        148×48    (0, 0)
      Image      48×48     (0, 0)
      Column     100×40    (48, 0)
      Text A     100×20    (48, 0)
      Text B     80×20     (48, 20)

  (c) Each node is visited exactly once for measurement and once for placement.
      The lecture stresses this because Compose enforces single-pass measurement.
      If every node were measured twice at each level, the total traversal time
      would grow exponentially: O(2^depth) instead of O(N). For a deep tree
      this would be extremely slow.

  (d) The tree was produced by the Composition phase. Each node in it contains
      the composable's layout instructions (modifiers, content lambda) — in
      other words, a LayoutNode that knows how to measure and place itself.

 ═══════════════════════════════════════════════════════════════
  QUESTION 3 — State reads and restart scopes
 ═══════════════════════════════════════════════════════════════

  (a) Snippets 1 and 2 are equivalent. In both, the state is read at the moment
      the modifier chain is built (during composition), not when the state is
      created. Snippet 1 reads via .value, snippet 2 via the `by` property
      delegate, but both trigger the read at the same point: when
      Modifier.padding(paddingState.value) / Modifier.padding(padding) executes.

  (b) Snippet 3:
      Text("A"): offsetX is read inside the lambda `{ IntOffset(offsetX.roundToPx(), 0) }`.
      This lambda runs during the layout phase (placement step), so the read
      happens in the Layout phase. When offsetX changes, only layout and draw
      re-run; composition is skipped.

      Text("B"): offsetPx is computed during composition (the `val offsetPx = …`
      line runs in the body of the composable). So the read happens in the
      Composition phase. When offsetX changes, the entire composable recomposes,
      then layout and draw run — all three phases execute.

  (c) A restart scope is a block of code that Compose can re-execute independently
      when a state it reads changes. For a read made directly in the body of a
      composable function, the restart scope is the nearest enclosing composable
      function itself — Compose will recompose that entire function.

 ═══════════════════════════════════════════════════════════════
  QUESTION 8 — Which phase re-runs?
 ═══════════════════════════════════════════════════════════════

  (a) Modifier.padding(p) — p read as modifier is built
      Read in: Composition
      Runs: Composition → Layout → Drawing (all three)

  (b) Modifier.offset { IntOffset(p, 0) } — p read inside lambda
      Read in: Layout (placement step)
      Runs: Layout (place only) → Drawing.  Composition: skipped.

  (c) Modifier.offset(p) — the Dp overload, p is read when modifier is built
      Read in: Composition
      Runs: Composition → Layout → Drawing (all three)

  (d) Canvas(…) { drawRect(c) } — c read inside onDraw
      Read in: Drawing
      Runs: Drawing only. Composition: skipped. Layout: skipped.

  (e) Layout(content) { m, cs -> /* uses s */ } — s read in measure lambda
      Read in: Layout (measurement step)
      Runs: Layout (measure + place) → Drawing. Composition: skipped.

  (f) Modifier.drawWithContent { /* uses c */ } — c read inside draw lambda
      Read in: Drawing
      Runs: Drawing only. Composition: skipped. Layout: skipped.

  Best for per-frame animation: (d) or (f) — reading in the drawing phase.
  Only the draw phase re-runs, which is the cheapest single phase. Composition
  and layout are skipped entirely, giving the best performance for values that
  change on every frame.

 ═══════════════════════════════════════════════════════════════
  QUESTION 12 — Two frames for one layout
 ═══════════════════════════════════════════════════════════════

  Frame 1:
    - Composition reads imageHeightPx = 0 (initial value)
    - Text gets top padding = 0.dp → text overlaps the image
    - Layout measures the image → 360 px tall
    - onSizeChanged fires: imageHeightPx = 360 (state write)
    - Drawing renders: text is at the top, overlapping the image (wrong)
    - A recomposition is scheduled because imageHeightPx changed

  Frame 2:
    - Composition reads imageHeightPx = 360
    - Text gets top padding = 360px converted to dp
    - Layout runs (sizes may change), onSizeChanged fires again with
      the same value (360), so no new recomposition is scheduled
    - Drawing renders: text is now below the image (correct)

  (a) The toDp() conversion is a warning sign because it converts layout-phase
      output (pixels) back to a composition-phase input (Dp). This crosses
      the phase boundary backwards, forcing a second frame.

  (b) The three-step pattern: read layout result → write state → recompose.
      This is a "write-in-layout, read-in-composition" cycle that always
      takes two frames to stabilize.

  (c) This code breaks the principle of unidirectional data flow: data should
      flow from Composition → Layout → Drawing, never backwards. Here, layout
      output feeds back into composition input, creating a cycle across frames.
*/
