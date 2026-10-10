package com.vemestael.archeryshotcounter.companion.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

/*
 * The phone's component library — a mirror of the watch app's `presentation/theme/Components.kt`.
 * Same names, same parameters and the same color contract (bright titles; accent/destructive
 * buttons with dark text; neutral buttons with dim text), built on phone Material3 instead of Wear
 * Material3, so the two apps stay visually unified and every size lives in one place. Wear-only
 * concerns (TransformingLazyColumn scope/transformation params, round-bezel width caps, TimeText)
 * are dropped; phone-only pieces (top bar, card, list with pull-to-refresh, dialog action row,
 * wheel picker) are added at the bottom.
 */

/** Minimum height of every [AppButton] — Material's 48dp touch-target minimum, same as the watch. */
val ButtonMinHeight = 48.dp

/**
 * A section/dialog title: the bright half of the app's confirmed color hierarchy — section and
 * dialog titles stay bright ([LocalAppPalette.text]) while every button caption below is dim
 * (see [AppButton]). Default modifier is full-width with top 16dp / bottom 4dp spacing; pass a
 * different one for dialogs (already spaced by their own Column) or where spacing differs.
 *
 * Pass [onInfoClick] to add a small ⓘ next to the title that opens an explanatory dialog — for
 * settings whose effect isn't obvious from their name alone (see [InfoButton]).
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp, bottom = 4.dp),
    textAlign: TextAlign = TextAlign.Start,
    onInfoClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = LocalAppPalette.current.text,
            textAlign = textAlign,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onInfoClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            InfoButton(onClick = onInfoClick, color = LocalAppPalette.current.textDim)
        }
    }
}

/**
 * A hand-drawn "ⓘ" glyph — a ring, a dot and a stem — matching [PlayPauseIcon]/[TimerIcon]'s
 * approach of drawing small glyphs instead of pulling in an icon library.
 */
@Composable
fun InfoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val radius = size.minDimension * 0.42f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth))
        drawCircle(color = color, radius = size.minDimension * 0.07f, center = Offset(center.x, center.y - radius * 0.42f))
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 0.02f),
            end = Offset(center.x, center.y + radius * 0.55f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * A tappable [InfoIcon] with a touch target comfortably larger than the glyph itself, for use
 * next to a setting's title/label. Opens the setting's explanatory dialog when tapped.
 */
@Composable
fun InfoButton(onClick: () -> Unit, color: Color, modifier: Modifier = Modifier) {
    IconTapTarget(onClick = onClick, modifier = modifier) {
        InfoIcon(color = color, modifier = Modifier.size(18.dp))
    }
}

/** A hand-drawn pencil glyph — a rotated rounded body with a triangular tip — for an edit affordance. */
@Composable
fun EditIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        rotate(degrees = 45f) {
            val bodyWidth = size.minDimension * 0.22f
            val bodyHeight = size.minDimension * 0.8f
            val tipHeight = bodyHeight * 0.28f
            val left = (size.width - bodyWidth) / 2f
            val top = (size.height - bodyHeight) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(bodyWidth, bodyHeight - tipHeight),
                cornerRadius = CornerRadius(bodyWidth * 0.3f)
            )
            val tipPath = Path().apply {
                moveTo(left, top + bodyHeight - tipHeight)
                lineTo(left + bodyWidth, top + bodyHeight - tipHeight)
                lineTo(left + bodyWidth / 2f, top + bodyHeight)
                close()
            }
            drawPath(tipPath, color = color)
        }
    }
}

/**
 * A tappable [EditIcon] with a touch target comfortably larger than the glyph itself, for use
 * next to a session's date in the history list. Opens that session's editor when tapped.
 */
@Composable
fun EditButton(onClick: () -> Unit, color: Color, modifier: Modifier = Modifier) {
    IconTapTarget(onClick = onClick, modifier = modifier) {
        EditIcon(color = color, modifier = Modifier.size(18.dp))
    }
}

/**
 * The app's one button style. Unselected (the default) is an elevated pill with a dim caption;
 * [selected] swaps it to the accent pill with dark-on-accent text, and [destructive] swaps it to
 * the pause/error-colored pill with that same dark text (Pause/Stop, Clear data, Delete) — both
 * are bright-background cases where dark text reads better than the usual dim caption. One-off
 * buttons needing a background neither of these covers pass [containerColor] directly, keeping
 * whichever text color (dim by default, or [contentColor] itself) fits that background.
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    containerColor: Color = when {
        destructive -> LocalAppPalette.current.pause
        selected -> LocalAppPalette.current.accent
        else -> LocalAppPalette.current.bgElev2
    },
    contentColor: Color = if (selected || destructive) LocalAppPalette.current.onAccent else LocalAppPalette.current.buttonTextDim,
    disabledContainerColor: Color = LocalAppPalette.current.bgElev2,
    disabledContentColor: Color = LocalAppPalette.current.textMuted,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        ),
        content = content
    )
}

/** [AppButton] convenience overload for the common single-line-caption case. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    containerColor: Color = when {
        destructive -> LocalAppPalette.current.pause
        selected -> LocalAppPalette.current.accent
        else -> LocalAppPalette.current.bgElev2
    },
    contentColor: Color = if (selected || destructive) LocalAppPalette.current.onAccent else LocalAppPalette.current.buttonTextDim,
    disabledContainerColor: Color = LocalAppPalette.current.bgElev2,
    disabledContentColor: Color = LocalAppPalette.current.textMuted,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        destructive = destructive,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
        shape = shape,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A labeled on/off row with a trailing switch, for boolean settings — the phone counterpart of the
 * watch's Wear `SwitchButton`: the same elevated pill, tappable as a whole. The checked thumb gets
 * [AppPalette.onAccent] on an accent track, the same fix the watch needed so the thumb doesn't
 * disappear into a track of the same color.
 */
@Composable
fun AppSwitchButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onInfoClick: (() -> Unit)? = null
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .clip(CircleShape)
            .background(palette.bgElev2)
            .clickable { onCheckedChange(!checked) }
            .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = palette.buttonTextDim,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onInfoClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                InfoButton(onClick = onInfoClick, color = palette.buttonTextDim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.accent,
                checkedBorderColor = palette.accent,
                uncheckedThumbColor = palette.textMuted,
                uncheckedTrackColor = palette.bgElev,
                uncheckedBorderColor = palette.line
            )
        )
    }
}

/**
 * A "−value+" row: a dim [AppButton] to decrement, the current value in the mono numeric style,
 * and an accent [AppButton] to increment. Same layout as the watch's numeric settings and session
 * editor fields, so their shared sizing (button weight, value font size, row spacing) lives in one
 * place.
 */
@Composable
fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    valueFontSize: TextUnit = 22.sp,
    decrementLabel: String = "−",
    incrementLabel: String = "+",
    spacing: Dp = 12.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppButton(
            text = decrementLabel,
            onClick = onDecrement,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontFamily = IbmPlexMono,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = LocalAppPalette.current.accent
        )
        AppButton(
            text = incrementLabel,
            onClick = onIncrement,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            selected = true
        )
    }
}

/**
 * The shot counter itself: a big mono digit, with the series delta ("−N"/"+N") flanking it on
 * either side when both deltas are given — the same display as the watch's main screen.
 */
@Composable
fun ShotCounterDisplay(
    count: Int,
    modifier: Modifier = Modifier,
    leftDelta: Int? = null,
    rightDelta: Int? = null,
    fontSize: TextUnit = 72.sp,
    color: Color = LocalAppPalette.current.accent
) {
    if (leftDelta != null && rightDelta != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (leftDelta > 0) "−$leftDelta" else "",
                fontFamily = IbmPlexMono,
                fontSize = 16.sp,
                color = LocalAppPalette.current.textMuted,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Text(
                text = "$count",
                fontFamily = IbmPlexMono,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = color
            )
            Text(
                text = "+$rightDelta",
                fontFamily = IbmPlexMono,
                fontSize = 16.sp,
                color = LocalAppPalette.current.textMuted,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    } else {
        Text(
            text = "$count",
            fontFamily = IbmPlexMono,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = modifier,
            color = color
        )
    }
}

/**
 * A colored dot followed by a label — a session's active/inactive marker. [textColor] defaults to
 * matching the dot; pass a fixed color when the label shouldn't track the dot (a history card's
 * date, which stays [AppPalette.textDim] regardless of dot color).
 */
@Composable
fun StatusIndicator(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = dotColor,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
        Text(
            text = text,
            style = textStyle,
            color = textColor
        )
    }
}

/** The blank space every scrollable list ends on, so content doesn't sit flush against the edge. */
@Composable
fun ListBottomSpacer(height: Dp = 16.dp) {
    Spacer(modifier = Modifier.fillMaxWidth().height(height))
}

/**
 * The rounded, scrollable card every dialog opens: clipped corners, elevated background,
 * centered content with consistent vertical spacing. End it with [DialogActions] for the usual
 * cancel/confirm pair.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    cornerRadius: Dp = 28.dp,
    contentPadding: Dp = 24.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(LocalAppPalette.current.bgElev)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

/** A dialog's body text: dim, centered, under its [SectionTitle]. */
@Composable
fun DialogText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalAppPalette.current.textDim,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * The cancel/confirm pair every dialog ends on: a neutral [AppButton] and an accent one side by side
 * (or a destructive one when [destructive], e.g. Clear data / Delete). Omit [confirmText] for a
 * dialog whose only action is dismissing it.
 */
@Composable
fun DialogActions(
    dismissText: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    destructive: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
        if (confirmText != null) {
            AppButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                selected = !destructive,
                destructive = destructive
            )
        }
    }
}

/**
 * A hand-drawn play/pause glyph — two bars when [playing] (tapping pauses), a right-pointing
 * triangle otherwise (tapping starts/resumes). Avoids pulling in an icon library for two shapes.
 */
@Composable
fun PlayPauseIcon(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (playing) {
            val barWidth = size.minDimension * 0.18f
            val barHeight = size.minDimension * 0.6f
            val gap = size.minDimension * 0.16f
            val top = (size.height - barHeight) / 2f
            val cornerRadius = CornerRadius(barWidth * 0.3f)
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width / 2f - gap / 2f - barWidth, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width / 2f + gap / 2f, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        } else {
            val w = size.minDimension * 0.55f
            val h = size.minDimension * 0.62f
            val left = (size.width - w) / 2f + w * 0.12f
            val top = (size.height - h) / 2f
            val path = Path().apply {
                moveTo(left, top)
                lineTo(left, top + h)
                lineTo(left + w, top + h / 2f)
                close()
            }
            drawPath(path, color = color)
        }
    }
}

/** A hand-drawn clock/timer glyph — stem, ring and two hands — for the auto-pause button. */
@Composable
fun TimerIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width / 2f, size.height / 2f + size.minDimension * 0.06f)
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius - size.minDimension * 0.16f),
            end = Offset(center.x, center.y - radius - size.minDimension * 0.02f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * 0.55f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * 0.45f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// ---- Phone-only components below: no watch counterpart, but built from the same palette/fonts. ----

/** A hand-drawn "←" glyph for the top bar's back button. */
@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        val y = size.height / 2f
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        val head = size.minDimension * 0.28f
        drawLine(color, Offset(left, y), Offset(right, y), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(left, y), Offset(left + head, y - head), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(left, y), Offset(left + head, y + head), strokeWidth, StrokeCap.Round)
    }
}

/** A hand-drawn "⋮" glyph for the top bar's overflow menu. */
@Composable
fun MoreIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.09f
        val x = size.width / 2f
        listOf(0.22f, 0.5f, 0.78f).forEach { fraction ->
            drawCircle(color = color, radius = radius, center = Offset(x, size.height * fraction))
        }
    }
}

/**
 * Makes an [IconTapTarget] take only [layoutSize] in its parent's layout while keeping its full
 * 48dp touch area, which overflows equally on every side. For glyphs inline with text (the
 * history card's pencil next to the date), where the full 48dp box would push the surrounding
 * rows apart. Touches still reach the overflow since Compose hit-tests unclipped children outside
 * their parent's bounds.
 */
fun Modifier.inlineTapTarget(layoutSize: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val size = layoutSize.roundToPx()
    layout(size, size) {
        placeable.place((size - placeable.width) / 2, (size - placeable.height) / 2)
    }
}

/** A round 48dp tap target around a small hand-drawn glyph (back, overflow, ⓘ, edit). */
@Composable
fun IconTapTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(ButtonMinHeight)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Every phone screen's frame: a [Scaffold] with a flat top bar (title in the display font, an
 * optional ← back button, trailing [actions]) on the palette background. [content] receives the
 * scaffold's inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val palette = LocalAppPalette.current
    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    if (onBack != null) {
                        IconTapTarget(onClick = onBack) {
                            BackIcon(color = palette.text, modifier = Modifier.size(24.dp))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bg,
                    scrolledContainerColor = palette.bg,
                    titleContentColor = palette.text,
                    navigationIconContentColor = palette.text,
                    actionIconContentColor = palette.textDim
                )
            )
        },
        content = content
    )
}

/**
 * The scrolling list every phone screen shows its items in — the phone counterpart of the watch's
 * `AppListScreen` list: consistent side padding and item spacing, ending on a [ListBottomSpacer].
 * Pass [onRefresh] to add pull-to-refresh (history's sync-from-watch gesture).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppList(
    modifier: Modifier = Modifier.fillMaxSize(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalSpacing: Dp = 12.dp,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val list = @Composable {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            content()
            item { ListBottomSpacer() }
        }
    }
    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) { list() }
    } else {
        Box(modifier = modifier) { list() }
    }
}

/**
 * A list card: the elevated rounded surface history entries and shot rows sit on. Tappable when
 * [onClick] is given.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(LocalAppPalette.current.bgElev)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

private val WheelItemHeight = 40.dp
private const val WHEEL_VISIBLE_COUNT = 5

/**
 * A scrolling drum/wheel picker: snaps to whichever item sits nearest the centered highlight
 * band. Built by hand rather than pulling in a picker library or Material3's clock-dial
 * TimePicker, which is awkward to operate precisely — the same drum the watch's session editor
 * uses, styled the same way (mono digits, accent for the selected value).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val visibleHeight = WheelItemHeight * WHEEL_VISIBLE_COUNT
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf selectedIndex
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index ?: selectedIndex
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) onSelectedIndexChange(centerIndex)
    }

    Box(modifier = modifier.height(visibleHeight), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(palette.bgElev2, RoundedCornerShape(8.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = (visibleHeight - WheelItemHeight) / 2),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(values) { index, label ->
                val selected = index == centerIndex
                Box(
                    modifier = Modifier.fillMaxWidth().height(WheelItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = IbmPlexMono,
                        fontSize = if (selected) 22.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) palette.accent else palette.textMuted
                    )
                }
            }
        }
    }
}
