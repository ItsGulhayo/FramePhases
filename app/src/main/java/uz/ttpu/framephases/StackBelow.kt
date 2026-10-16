package uz.ttpu.framephases

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StackBelow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->

        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable ->
            Log.d("StackBelow", "measure child")
            measurable.measure(loose)
        }

        val spacingPx = spacing.roundToPx()
        val width = placeables.maxOf { it.width }
        val height = placeables.sumOf { it.height } + spacingPx * (placeables.size - 1).coerceAtLeast(0)

        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            Log.d("StackBelow", "place children")
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height + spacingPx
            }
        }
    }
}
