package com.fserver.app.presentation.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

private val onboardingPages = listOf(
    OnboardingPage(
        illustration = R.string.onboarding_illustration_devices,
        title = R.string.onboarding_title_purpose,
        body = R.string.onboarding_body_purpose,
    ),
    OnboardingPage(
        illustration = R.string.onboarding_illustration_permissions,
        title = R.string.onboarding_title_permissions,
        body = R.string.onboarding_body_permissions,
    ),
    OnboardingPage(
        illustration = R.string.onboarding_illustration_connect,
        title = R.string.onboarding_title_connect,
        body = R.string.onboarding_body_connect,
    ),
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    navigateToDiscovery: () -> Unit,
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
        navigateToDiscovery = navigateToDiscovery,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingState,
    navigateToDiscovery: () -> Unit,
    pagerState: PagerState = rememberPagerState { onboardingPages.size },
) {
    val scope = rememberCoroutineScope()

    DkScaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = DkSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                OnboardingPageContent(page = onboardingPages[pageIndex])
            }

            DkPageIndicator(
                pageCount = state.pageCount,
                currentPage = state.pageIndex,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                DkPrimaryButton(
                    text = stringResource(
                        if (state.isLastPage) R.string.action_start else R.string.action_next
                    ),
                    onClick = {
                        if (state.isLastPage) {
                            navigateToDiscovery()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(state.pageIndex + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DkGhostButton(
                    text = stringResource(R.string.action_skip),
                    onClick = navigateToDiscovery,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        DkPlaceholderBox(
            label = stringResource(page.illustration),
            modifier = Modifier.height(200.dp),
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
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingScreenPreview() {
    FServerTheme {
        OnboardingScreen(state = OnboardingState(), navigateToDiscovery = {})
    }
}
