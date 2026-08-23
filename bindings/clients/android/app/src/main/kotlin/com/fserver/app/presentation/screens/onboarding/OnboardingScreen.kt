package com.fserver.app.presentation.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPageIndicator
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.discovery.shared.ConnectRouteCard
import com.fserver.app.presentation.screens.onboarding.model.OnboardingIntent
import com.fserver.app.presentation.screens.onboarding.model.OnboardingState
import com.fserver.app.presentation.theme.FServerTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private data class OnboardingPage(
    @param:StringRes val illustration: Int,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

/**
 * Why the app exists, where the bytes actually go, and the fork. No permission is mentioned:
 * each path explains and requests its own, so asking here would be asking for something the
 * user has not chosen to do yet.
 */
private val onboardingPages = listOf(
    OnboardingPage(
        illustration = R.string.onboarding_illustration_devices,
        title = R.string.onboarding_title_purpose,
        body = R.string.onboarding_body_purpose,
    ),
    OnboardingPage(
        illustration = R.string.onboarding_illustration_privacy,
        title = R.string.onboarding_title_privacy,
        body = R.string.onboarding_body_privacy,
    ),
    OnboardingPage(
        illustration = R.string.onboarding_illustration_fork,
        title = R.string.onboarding_title_fork,
        body = R.string.onboarding_body_fork,
    ),
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToFiles: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { onboardingPages.size }

    // The pager is the source of truth for what is on screen (it also moves on swipe), so
    // the state follows it rather than the other way round.
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onIntent(OnboardingIntent.PageSettled(pagerState.currentPage))
    }

    OnboardingScreen(
        state = state,
        pagerState = pagerState,
        navigateToConnect = navigateToConnect,
        navigateToSourcePick = navigateToSourcePick,
        navigateToFiles = navigateToFiles,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingState,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToFiles: () -> Unit,
    pagerState: PagerState = rememberPagerState { onboardingPages.size },
) {
    val scope = rememberCoroutineScope()

    DkScaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = DkSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
        ) {
            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState,
                pageSpacing = DkSpacing.lg,
                contentPadding = PaddingValues(
                    horizontal = DkSpacing.screenPadding,
                )
            ) { pageIndex ->
                OnboardingPageContent(
                    page = onboardingPages[pageIndex],
                    // The fork's two ways out live inside the page: swiping back to step 2 must
                    // take them with it, not leave them under a different explanation.
                    fork = if (pageIndex == onboardingPages.lastIndex) {
                        {
                            OnboardingFork(
                                navigateToConnect = navigateToConnect,
                                navigateToSourcePick = navigateToSourcePick,
                            )
                        }
                    } else {
                        null
                    },
                )
            }

            DkPageIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = DkSpacing.screenPadding),
                pageCount = state.pageCount,
                currentPage = state.pageIndex,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)
            ) {
                if (!state.isFork) {
                    DkPrimaryButton(
                        text = stringResource(R.string.action_next),
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(state.pageIndex + 1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Skipping is not a dead end: it lands on the empty file list, which offers the
                // same two exits the fork does.
                DkGhostButton(
                    text = stringResource(R.string.action_skip),
                    onClick = navigateToFiles,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
    fork: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        DkPlaceholderBox(
            label = stringResource(page.illustration),
            modifier = Modifier.height(if (fork == null) 200.dp else 132.dp),
        )
        Spacer(modifier = Modifier.height(DkSpacing.xs))
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(page.body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fork != null) {
            fork()
        }
    }
}

/** The two things the app can do, as peers — neither is the lesser path. */
@Composable
private fun OnboardingFork(
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        ConnectRouteCard(
            title = stringResource(R.string.fork_connect_title),
            description = stringResource(R.string.fork_connect_subtitle),
            onClick = navigateToConnect,
        )
        ConnectRouteCard(
            title = stringResource(R.string.fork_send_title),
            description = stringResource(R.string.fork_send_subtitle),
            onClick = navigateToSourcePick,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingScreenPreview() {
    FServerTheme {
        OnboardingScreen(
            state = OnboardingState(),
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToFiles = {},
        )
    }
}

@Preview(name = "Fork", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingForkPreview() {
    FServerTheme {
        OnboardingScreen(
            state = OnboardingState(pageIndex = 2),
            pagerState = rememberPagerState(initialPage = 2) { onboardingPages.size },
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToFiles = {},
        )
    }
}
