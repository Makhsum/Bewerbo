package de.bewerbo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.components.FitOneLineText
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.screens.ApplicationScreen
import de.bewerbo.app.ui.screens.LockerScreen
import de.bewerbo.app.ui.screens.MatchScreen
import de.bewerbo.app.ui.screens.OverviewScreen
import de.bewerbo.app.ui.screens.PostingScreen
import de.bewerbo.app.ui.screens.ProfileScreen
import de.bewerbo.app.ui.theme.BewerboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BewerboTheme {
                BewerboApp()
            }
        }
    }
}

enum class Destination(val route: String, val tag: String, val label: Int, val icon: ImageVector) {
    Overview("uebersicht", "nav_uebersicht", R.string.nav_overview, BewerboIcons.Overview),
    Profile("profil", "nav_profil", R.string.nav_profile, BewerboIcons.Person),
    Posting("stellenanzeige", "nav_stellenanzeige", R.string.nav_posting, BewerboIcons.Posting),
    Application("bewerbung", "nav_bewerbung", R.string.nav_application, BewerboIcons.Document),
    Locker("mappe", "nav_mappe", R.string.nav_locker, BewerboIcons.Anlagen),
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BewerboApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(state.lastSavedPdf) {
        state.lastSavedPdf?.let { snackbar.showSnackbar(it) }
    }

    Box(
        Modifier
            // testTagsAsResourceId is set ONCE, here, above every branch of the tree. Compose test
            // tags are invisible to Appium without it, and every screen below relies on that —
            // which is why it is at the root rather than sprinkled per screen.
            .semantics { testTagsAsResourceId = true }
            .fillMaxSize(),
    ) {
        Scaffold(
            bottomBar = { BottomBar(navController) },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(snackbarData = data, modifier = Modifier.testTag("app_message"))
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Overview.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Overview.route) {
                    OverviewScreen(state, viewModel) { route -> navController.navigate(route) }
                }
                composable(Destination.Profile.route) { ProfileScreen(state, viewModel) }
                composable(Destination.Posting.route) {
                    PostingScreen(state, viewModel) { navController.navigate("abgleich") }
                }
                composable("abgleich") {
                    MatchScreen(
                        state,
                        viewModel,
                        navigate = { route -> navController.navigate(route) },
                    ) { navController.navigate(Destination.Application.route) }
                }
                composable(Destination.Application.route) { ApplicationScreen(state, viewModel) }
                composable(Destination.Locker.route) { LockerScreen(state, viewModel) }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                // The tag goes on the item itself — the clickable wrapper — not on the label
                // inside it, so a driver taps a node that is actually clickable.
                modifier = Modifier.testTag(destination.tag),
                selected = current == destination.route ||
                    (destination == Destination.Posting && current == "abgleich"),
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(destination.icon, contentDescription = stringResource(destination.label))
                },
                label = {
                    // Five destinations share the screen width, so a long word — "Application",
                    // "Bewerbung", "Вакансия" — met the edge of its item and wrapped onto a
                    // second line. The label shrinks to fit instead; its own tag lets a driver
                    // read the line back and see that it is still one line.
                    FitOneLineText(
                        text = stringResource(destination.label),
                        modifier = Modifier.fillMaxWidth().testTag("${destination.tag}_label"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
