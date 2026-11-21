package eu.hxreborn.biometricapplock.ui.component

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.root.RootServiceState
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ModuleStatus
import eu.hxreborn.biometricapplock.ui.viewmodel.ServiceLoadEvent

private data class HeroVisual(
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val content: Color,
    val badgeBg: Color,
    val badgeFg: Color,
)

@Composable
fun StatusCard(
    status: ModuleStatus,
    lockedAppCount: Int,
    serviceLoadEvent: ServiceLoadEvent?,
    rootServiceState: RootServiceState,
    rootGranted: Boolean?,
    rootProtectionEnabled: Boolean,
    onToggleRootProtection: (Boolean) -> Unit,
    onRequestRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = heroVisual(status, rootServiceState)
    val isRootRunning = rootServiceState is RootServiceState.Running
    val title =
        when {
            isRootRunning -> {
                stringResource(R.string.status_root_active)
            }

            status == ModuleStatus.Enabled -> {
                stringResource(R.string.status_active)
            }

            status == ModuleStatus.RebootRequired -> {
                stringResource(R.string.status_reboot_required)
            }

            rootGranted == false || rootServiceState is RootServiceState.NoRoot -> {
                stringResource(R.string.status_root_needed)
            }

            else -> {
                stringResource(R.string.status_paused)
            }
        }

    val summary =
        when {
            isRootRunning || status == ModuleStatus.Enabled -> {
                if (lockedAppCount == 0) {
                    stringResource(R.string.status_locked_empty)
                } else {
                    pluralStringResource(R.plurals.status_locked_count, lockedAppCount, lockedAppCount)
                }
            }

            status == ModuleStatus.RebootRequired -> {
                stringResource(R.string.status_reboot_required_reason)
            }

            rootGranted == false || rootServiceState is RootServiceState.NoRoot -> {
                stringResource(R.string.status_root_needed_action)
            }

            else -> {
                stringResource(R.string.status_paused_action)
            }
        }

    val engineChipText =
        when {
            isRootRunning -> stringResource(R.string.engine_chip_root)
            status == ModuleStatus.Enabled -> stringResource(R.string.engine_chip_xposed)
            rootGranted == false -> stringResource(R.string.engine_chip_no_root)
            else -> stringResource(R.string.engine_chip_paused)
        }

    val loadLine: String? =
        when {
            rootServiceState is RootServiceState.Running -> {
                val relative =
                    DateUtils
                        .getRelativeTimeSpanString(
                            rootServiceState.startedAtMs,
                            System.currentTimeMillis(),
                            DateUtils.SECOND_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                stringResource(R.string.status_root_started, relative)
            }

            status == ModuleStatus.Enabled && serviceLoadEvent != null -> {
                val relative =
                    DateUtils
                        .getRelativeTimeSpanString(
                            serviceLoadEvent.epochMs,
                            System.currentTimeMillis(),
                            DateUtils.SECOND_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                when (serviceLoadEvent) {
                    is ServiceLoadEvent.Boot -> stringResource(R.string.status_loaded_at_boot, relative)
                    is ServiceLoadEvent.HotReload -> stringResource(R.string.status_loaded_via_hot_reload, relative)
                }
            }

            else -> {
                null
            }
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SectionHorizontalMargin, vertical = Tokens.SpacingXs),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border =
            BorderStroke(
                width = 1.dp,
                color = visual.content.copy(alpha = 0.14f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(visual.gradientStart, visual.gradientEnd),
                        ),
                    ).padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(visual.badgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.badgeFg,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = visual.content,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = visual.content.copy(alpha = 0.85f),
                    )
                }

                if (rootGranted != false) {
                    LockSwitch(
                        checked = rootProtectionEnabled && isRootRunning,
                        onCheckedChange = { enabled -> onToggleRootProtection(enabled) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = visual.badgeBg,
                        contentColor = visual.badgeFg,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = engineChipText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                    Surface(
                        color = visual.content.copy(alpha = 0.12f),
                        contentColor = visual.content,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "Android ${Build.VERSION.RELEASE}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                if (rootGranted == false || rootServiceState is RootServiceState.NoRoot) {
                    FilledTonalButton(onClick = onRequestRoot) {
                        Text(stringResource(R.string.action_grant_root))
                    }
                }
            }

            if (loadLine != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = loadLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = visual.content.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun heroVisual(
    status: ModuleStatus,
    rootState: RootServiceState,
): HeroVisual {
    val scheme = MaterialTheme.colorScheme
    return when {
        rootState is RootServiceState.Running || status == ModuleStatus.Enabled -> {
            HeroVisual(
                icon = Icons.Filled.VerifiedUser,
                gradientStart = scheme.primaryContainer,
                gradientEnd = scheme.secondaryContainer,
                content = scheme.onPrimaryContainer,
                badgeBg = scheme.primary,
                badgeFg = scheme.onPrimary,
            )
        }

        status == ModuleStatus.RebootRequired -> {
            HeroVisual(
                icon = Icons.Filled.RestartAlt,
                gradientStart = scheme.tertiaryContainer,
                gradientEnd = scheme.secondaryContainer,
                content = scheme.onTertiaryContainer,
                badgeBg = scheme.tertiary,
                badgeFg = scheme.onTertiary,
            )
        }

        rootState is RootServiceState.NoRoot -> {
            HeroVisual(
                icon = Icons.Filled.GppBad,
                gradientStart = scheme.errorContainer,
                gradientEnd = scheme.surfaceContainerHighest,
                content = scheme.onErrorContainer,
                badgeBg = scheme.error,
                badgeFg = scheme.onError,
            )
        }

        else -> {
            HeroVisual(
                icon = Icons.Filled.AdminPanelSettings,
                gradientStart = scheme.surfaceContainerHigh,
                gradientEnd = scheme.surfaceContainerHighest,
                content = scheme.onSurface,
                badgeBg = scheme.surfaceContainerLowest,
                badgeFg = scheme.primary,
            )
        }
    }
}
