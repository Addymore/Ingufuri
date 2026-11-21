package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.ui.component.BiometricHardwareSection
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.LockedAppsSection
import eu.hxreborn.biometricapplock.ui.component.StatusCard
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScopeViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val app = App.from(context)
    val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val serviceLoadEvent by viewModel.serviceLoadEvent.collectAsStateWithLifecycle()
    val rootServiceState by viewModel.rootServiceState.collectAsStateWithLifecycle()
    val rootGranted by viewModel.rootGranted.collectAsStateWithLifecycle()
    val installedPackageKeys = rememberInstalledPackageKeys()
    val lockedApps =
        remember(scope, installedPackageKeys) {
            scope.filterTo(linkedSetOf()) { it in installedPackageKeys }
        }
    var showRelockDialog by remember { mutableStateOf(false) }

    if (showRelockDialog) {
        RelockDelayDialog(
            currentSeconds = prefs.relockDelaySeconds,
            onSelect = { seconds ->
                app.prefsRepository.save(Prefs.RELOCK_DELAY_SECONDS, seconds)
                showRelockDialog = false
            },
            onDismiss = { showRelockDialog = false },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { ExpandedTitle(stringResource(R.string.app_bar_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + Tokens.SpacingSm,
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
        ) {
            item {
                StatusCard(
                    status = moduleStatus,
                    lockedAppCount = lockedApps.size,
                    serviceLoadEvent = serviceLoadEvent,
                    rootServiceState = rootServiceState,
                    rootGranted = rootGranted,
                    rootProtectionEnabled = prefs.rootProtectionEnabled,
                    onToggleRootProtection = viewModel::toggleRootProtection,
                    onRequestRoot = viewModel::requestRootAndStart,
                )
            }

            item {
                Spacer(modifier = Modifier.height(Tokens.BentoGridSpacing))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.SectionHorizontalMargin),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.BentoGridSpacing),
                ) {
                    BentoStatCard(
                        icon = Icons.Outlined.Lock,
                        value = lockedApps.size.toString(),
                        label = stringResource(R.string.bento_locked_apps),
                        onClick = onNavigateToApps,
                        modifier = Modifier.weight(1f),
                    )
                    BentoStatCard(
                        icon = Icons.Outlined.Timer,
                        value = relockDelaySummary(prefs.relockDelaySeconds),
                        label = stringResource(R.string.settings_relock_delay_title),
                        onClick = { showRelockDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(Tokens.BentoGridSpacing))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.SectionHorizontalMargin),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.BentoGridSpacing),
                ) {
                    BentoToggleCard(
                        icon = Icons.Outlined.ScreenLockPortrait,
                        title = stringResource(R.string.bento_self_lock),
                        active = prefs.selfLock,
                        onClick = { app.prefsRepository.save(Prefs.SELF_LOCK, !prefs.selfLock) },
                        modifier = Modifier.weight(1f),
                    )
                    BentoToggleCard(
                        icon = Icons.Outlined.Screenshot,
                        title = stringResource(R.string.settings_block_screenshots_title),
                        active = prefs.blockScreenshots,
                        onClick = {
                            app.prefsRepository.save(Prefs.BLOCK_SCREENSHOTS, !prefs.blockScreenshots)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                LockedAppsSection(
                    scope = lockedApps,
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToAppDetail = onNavigateToAppDetail,
                )
            }

            item { BiometricHardwareSection() }
        }
    }
}

@Composable
private fun BentoStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.BentoCardRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BentoToggleCard(
    icon: ImageVector,
    title: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val pillBg =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val pillFg =
        if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.BentoCardRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Surface(
                    color = pillBg,
                    contentColor = pillFg,
                    shape = CircleShape,
                ) {
                    Text(
                        text = stringResource(if (active) R.string.bento_state_on else R.string.bento_state_off),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
