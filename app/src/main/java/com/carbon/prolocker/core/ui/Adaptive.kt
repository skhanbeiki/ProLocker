package com.carbon.prolocker.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet support. The app was designed for phones; on tablets (600dp wide and up) pages use the full
 * width: lists of cards become multi-column grids of phone-sized cards, tile grids gain columns instead of
 * growing their tiles, and real components (lock pad, buttons, ads) keep a natural size.
 * Every helper here is a no-op below [Adaptive.TabletMinWidth], so the phone UI is unchanged.
 */
object Adaptive {
    /** Width from which a window counts as a tablet (Material "medium" width class). */
    val TabletMinWidth = 600.dp

    /** Material "expanded" width class: landscape tablets. */
    val ExpandedMinWidth = 840.dp

    /** Minimum width of one card/row column when a list becomes a grid (2 columns portrait, 3 landscape). */
    val ListCellMinWidth = 340.dp

    /** Minimum tile size for image/thumbnail grids. */
    val TileMinWidth = 140.dp

    /** Space between grid columns on tablets. */
    val Gutter = 16.dp

    /** Pattern grid and PIN pad on the lock screens. */
    val LockInputMaxWidth = 380.dp

    /** Primary buttons: natural width instead of spanning a whole tablet. */
    val ButtonMaxWidth = 420.dp

    /** Native ad views (XML layouts that stretch to their container). */
    val NativeAdMaxWidth = 560.dp

    /** A single standalone card on an otherwise empty page (permission prompts, empty states). */
    val StandaloneCardMaxWidth = 600.dp

    /** A bar of two side-by-side action buttons. */
    val ButtonBarMaxWidth = 640.dp

    /** Segmented tab rows and similar small controls. */
    val ControlMaxWidth = 520.dp

    /** One tile of the horizontal tools row on Home. */
    val ToolTileWidth = 96.dp
}

/**
 * True on a tablet: the window is at least [Adaptive.TabletMinWidth] wide in both orientations
 * (smallest width), so a phone turned to landscape never gets tablet layouts.
 */
@Composable
@ReadOnlyComposable
fun isTablet(): Boolean {
    val c = LocalConfiguration.current
    return c.smallestScreenWidthDp >= Adaptive.TabletMinWidth.value && c.screenWidthDp >= Adaptive.TabletMinWidth.value
}

/** True on a tablet whose window is at least [Adaptive.ExpandedMinWidth] wide (landscape tablet). */
@Composable
@ReadOnlyComposable
fun isExpandedWidth(): Boolean =
    isTablet() && LocalConfiguration.current.screenWidthDp >= Adaptive.ExpandedMinWidth.value

/** True on a tablet held in landscape. */
@Composable
@ReadOnlyComposable
fun isTabletLandscape(): Boolean {
    val c = LocalConfiguration.current
    return isTablet() && c.screenWidthDp > c.screenHeightDp
}

/**
 * For components (buttons, ads, small controls), not pages: on tablets the component takes the width
 * offered, centers itself and fills at most [maxWidth]. On phones it does nothing.
 */
fun Modifier.centeredMaxWidth(maxWidth: Dp): Modifier = composed {
    if (isTablet()) {
        Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = maxWidth)
            .fillMaxWidth()
    } else {
        Modifier
    }
}

/** [phoneColumns] fixed columns on phones; on tablets as many columns of at least [tabletMinCellSize] as fit. */
@Composable
@ReadOnlyComposable
fun adaptiveGridCells(phoneColumns: Int, tabletMinCellSize: Dp): GridCells =
    if (isTablet()) GridCells.Adaptive(tabletMinCellSize) else GridCells.Fixed(phoneColumns)

/** A list of cards/rows: one column on phones, phone-width columns filling the width on tablets. */
@Composable
@ReadOnlyComposable
fun adaptiveListCells(): GridCells = adaptiveGridCells(1, Adaptive.ListCellMinWidth)

/**
 * A page of sections/cards with uneven heights: one column on phones; on tablets phone-width columns
 * filling the width, each card dropping into the shortest column.
 */
@Composable
@ReadOnlyComposable
fun adaptiveStaggeredCells(): StaggeredGridCells =
    if (isTablet()) StaggeredGridCells.Adaptive(Adaptive.ListCellMinWidth) else StaggeredGridCells.Fixed(1)

/** Horizontal arrangement for a list grid: column gutter on tablets, unchanged on phones. */
@Composable
@ReadOnlyComposable
fun adaptiveGridHorizontalArrangement(): Arrangement.Horizontal =
    if (isTablet()) Arrangement.spacedBy(Adaptive.Gutter) else Arrangement.Start

/**
 * Non-lazy card grid for scrolling pages made of a few sections (settings groups, info cards).
 * On phones it is a plain [Column] with [phoneArrangement], so phone layouts are untouched.
 * On tablets the children are laid out masonry-style in as many columns of at least [minCellWidth]
 * as fit (each child goes to the shortest column), filling the full width.
 */
@Composable
fun AdaptiveCardGrid(
    modifier: Modifier = Modifier,
    phoneArrangement: Arrangement.Vertical = Arrangement.Top,
    minCellWidth: Dp = Adaptive.ListCellMinWidth,
    spacing: Dp = Adaptive.Gutter,
    content: @Composable () -> Unit
) {
    if (!isTablet()) {
        Column(modifier = modifier, verticalArrangement = phoneArrangement) { content() }
        return
    }
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val columns = ((width + gap) / (minCellWidth.roundToPx() + gap)).coerceAtLeast(1)
        val cellWidth = (width - gap * (columns - 1)) / columns
        val heights = IntArray(columns)
        val placements = measurables.map { m ->
            val p = m.measure(Constraints.fixedWidth(cellWidth))
            val col = heights.indices.minBy { heights[it] }
            val y = heights[col]
            heights[col] += p.height + gap
            Triple(p, col, y)
        }
        val totalHeight = ((heights.maxOrNull() ?: 0) - gap).coerceAtLeast(0)
        layout(width, totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placements.forEach { (p, col, y) -> p.placeRelative(col * (cellWidth + gap), y) }
        }
    }
}
