package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.ui.theme.Tokens

enum class SectionPosition { Single, Top, Middle, Bottom }

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    position: SectionPosition = SectionPosition.Single,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier =
        modifier.fillMaxWidth().padding(
            horizontal = Tokens.SectionHorizontalMargin,
            vertical = Tokens.SectionItemSpacing,
        )
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    val border =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    val shape = sectionShape(position)
    when {
        onLongClick != null -> {
            Card(
                modifier =
                    cardModifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    ),
                shape = shape,
                colors = colors,
                border = border,
            ) { content() }
        }

        onClick != null -> {
            Card(
                onClick = onClick,
                modifier = cardModifier,
                shape = shape,
                colors = colors,
                border = border,
            ) { content() }
        }

        else -> {
            Card(
                modifier = cardModifier,
                shape = shape,
                colors = colors,
                border = border,
            ) { content() }
        }
    }
}

@Composable
private fun sectionShape(position: SectionPosition): Shape {
    val large = CornerSize(Tokens.OneUiCardRadius)
    val small = CornerSize(6.dp)
    return when (position) {
        SectionPosition.Single -> RoundedCornerShape(large, large, large, large)
        SectionPosition.Top -> RoundedCornerShape(large, large, small, small)
        SectionPosition.Middle -> RoundedCornerShape(small, small, small, small)
        SectionPosition.Bottom -> RoundedCornerShape(small, small, large, large)
    }
}
