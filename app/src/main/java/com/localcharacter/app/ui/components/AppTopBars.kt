package com.localcharacter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppDimensions {
    val MainTopBarMinHeight: Dp = 56.dp
    val HomeTopBarMinHeight: Dp = 64.dp
    val DetailTopBarMinHeight: Dp = 56.dp
    val TopBarHorizontalPadding: Dp = 16.dp
    val TopBarVerticalPadding: Dp = 4.dp
    val TopBarTouchTarget: Dp = 48.dp
    val TopBarTitleSideGutter: Dp = 56.dp
}

@Composable
fun MainScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.MainTopBarMinHeight)
                .padding(
                    horizontal = AppDimensions.TopBarHorizontalPadding,
                    vertical = AppDimensions.TopBarVerticalPadding,
                ),
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = AppDimensions.TopBarTitleSideGutter),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun HomeTopBar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.HomeTopBarMinHeight)
                .padding(
                    horizontal = AppDimensions.TopBarHorizontalPadding,
                    vertical = AppDimensions.TopBarVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.DetailTopBarMinHeight)
                .padding(
                    horizontal = AppDimensions.TopBarHorizontalPadding,
                    vertical = AppDimensions.TopBarVerticalPadding,
                ),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(AppDimensions.TopBarTouchTarget),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
            }
            leading?.let { content ->
                Box(Modifier.align(Alignment.CenterStart).padding(start = 54.dp).size(36.dp)) { content() }
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = AppDimensions.TopBarTitleSideGutter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Preview(name = "Main top bar small", widthDp = 320, heightDp = 64)
@Composable
private fun MainScreenTopBarSmallPreview() {
    MainScreenTopBar("Explorar")
}

@Preview(name = "Main top bar normal", widthDp = 393, heightDp = 64)
@Composable
private fun MainScreenTopBarNormalPreview() {
    MainScreenTopBar("Modelos")
}

@Preview(name = "Main top bar large font", widthDp = 430, heightDp = 76, fontScale = 1.3f)
@Composable
private fun MainScreenTopBarLargeFontPreview() {
    MainScreenTopBar("Ajustes")
}

@Preview(name = "Home top bar", widthDp = 393, heightDp = 76)
@Composable
private fun HomeTopBarPreview() {
    HomeTopBar("Local Character", "Procesamiento local") {
        IconButton(onClick = {}) { Icon(Icons.Default.Search, "Buscar") }
        IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Ajustes") }
    }
}
