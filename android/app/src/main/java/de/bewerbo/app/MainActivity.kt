package de.bewerbo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.hasAssistant
import de.bewerbo.app.data.isOpeningApplication
import de.bewerbo.app.ui.UiLanguageProvider
import de.bewerbo.app.ui.components.FitOneLineText
import de.bewerbo.app.ui.components.FitOneLineTextGroup
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.errorMessage
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.screens.ApplicationScreen
import de.bewerbo.app.ui.screens.AssistantScreen
import de.bewerbo.app.ui.screens.LegalPage
import de.bewerbo.app.ui.screens.LegalScreen
import de.bewerbo.app.ui.screens.LockerScreen
import de.bewerbo.app.ui.screens.MatchScreen
import de.bewerbo.app.ui.screens.OverviewScreen
import de.bewerbo.app.ui.screens.PostingScreen
import de.bewerbo.app.ui.screens.ProfileScreen
import de.bewerbo.app.ui.screens.SettingsScreen
import de.bewerbo.app.ui.screens.SignInScreen
import de.bewerbo.app.ui.theme.BewerboTheme
import de.bewerbo.app.ui.theme.CardElevation
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

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

/**
 * The PLACES — and the bottom bar is nothing else.
 *
 * A place is somewhere the user goes back to whenever they like, in whatever order: how ready the
 * Mappe is, the conversation with the assistant, what the profile says, what is filed in the Mappe.
 * That is exactly the freedom a tab promises, so these four are the only things allowed on the bar.
 *
 * The assistant is a place by that same definition and not an exception to it: a user comes back to
 * it to add a station, to ask what is still missing, to read an answer again. What was wrong before
 * was never the NUMBER of items — it was that three of five were flow STEPS, which promised an order
 * the product does not have.
 *
 * Producing an application is not a place and never was — see [FlowStep].
 */
enum class Destination(val route: String, val tag: String, val label: Int, val icon: ImageVector) {
    Overview("uebersicht", "nav_uebersicht", R.string.nav_overview, BewerboIcons.Overview),
    Assistant("assistent", "nav_assistent", R.string.nav_assistant, BewerboIcons.Assistant),
    Profile("profil", "nav_profil", R.string.nav_profile, BewerboIcons.Person),
    Locker("mappe", "nav_mappe", R.string.nav_locker, BewerboIcons.Anlagen),
}

/**
 * The STEPS of the one path an application is produced along, in the order they happen.
 *
 * Three of these used to sit on the bottom bar as two tabs with the Abgleich hidden inside the
 * Stellenanzeige one, and that promised an order the product does not have: no Abgleich without a
 * posting, no Bewerbung without a letter. A user who did not already know the sequence tapped
 * "Bewerbung", read "Noch kein Anschreiben" and had nothing telling them where to start.
 *
 * They keep their routes — "stellenanzeige", "abgleich", "bewerbung" — because the Übersicht's
 * deep links carry them as the backend writes them, and a step is still a destination of its own.
 * What changed is that they are drawn with the rail of [ApplicationFlow] above them instead of
 * posing as places, and the bar keeps the three places reachable from inside every one of them.
 */
enum class FlowStep(val route: String, val tag: String, val label: Int) {
    Posting("stellenanzeige", "flow_stellenanzeige", R.string.nav_posting),
    Match("abgleich", "flow_abgleich", R.string.match_title),
    Application("bewerbung", "flow_bewerbung", R.string.nav_application),
}

/**
 * The settings, which are neither a place nor a step.
 *
 * A place is somewhere the user works, and the bar carries the three of those; a step is part of
 * producing an application. The settings are where the app is set up and read about — opened from
 * the Übersicht, left by the arrow they carry. That is why this is a route on its own rather than a
 * fourth entry in [Destination], and NavigationShapeTest is what keeps it from becoming one.
 */
const val SettingsRoute = "einstellungen"

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun BewerboApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val keyboardOpen = WindowInsets.isImeVisible

    // The door: everything below is reached through it, and a device that is not signed in reaches
    // none of it. Not a destination of the NavHost on purpose — drawn ABOVE the Scaffold, so the
    // bottom bar never stands behind it and the three places stay the three places.
    val door = !state.loading && state.accountEmail == null

    // The back stack belongs to the account that built it, and the door does not clear it: the
    // NavHost only leaves the composition while the door is up, so rememberNavController hands the
    // next session the stack the last one left. That stack always ends on the settings, because
    // that is where the sign-out button is — so the first thing a new account saw of Bewerbo was
    // the previous user's last screen instead of the Übersicht. Popped while the door is up rather
    // than on the way in, so the app is already at its start destination when it draws.
    //
    // currentBackStackEntry is null before the NavHost has ever composed — the first launch, where
    // there is no graph to pop and reading graph.startDestinationId would throw.
    LaunchedEffect(door) {
        if (door && navController.currentBackStackEntry != null) {
            navController.popBackStack(navController.graph.startDestinationId, inclusive = false)
        }
    }

    // The interface language is the app's own, not the phone's, and it wraps the whole tree for
    // the same reason testTagsAsResourceId does: every screen below reads strings through it.
    UiLanguageProvider(state.uiLanguage) {
        // INSIDE the provider, and that is the whole point: the error text is read out of the
        // resources, so resolving it above this line would draw it in the PHONE's language over an
        // app the user had set to something else. Same boundary BewerboDialog exists for.
        //
        // The door writes its own failures under its own form, where the field that caused them is,
        // and there is no Scaffold to host a snackbar while it is up anyway.
        val errorText = state.error?.takeUnless { door }?.let { errorMessage(it) }
        LaunchedEffect(errorText) {
            errorText?.let {
                snackbar.showSnackbar(it)
                viewModel.dismissError()
            }
        }
        LaunchedEffect(state.lastSavedFile) {
            state.lastSavedFile?.let { snackbar.showSnackbar(it) }
        }

        Box(
            Modifier
                // testTagsAsResourceId is set ONCE, here, above every branch of the tree. Compose test
                // tags are invisible to Appium without it, and every screen below relies on that —
                // which is why it is at the root rather than sprinkled per screen.
                .semantics { testTagsAsResourceId = true }
                .fillMaxSize(),
        ) {
            if (door) {
                SignInScreen(state, viewModel)
                return@Box
            }

            Scaffold(
                // The bar has nowhere to sit while the keyboard is up: the IME inset lifted it onto
                // the keyboard, where it ate a row of the little viewport that was left. Nobody
                // changes tab mid-word, so it stands down until the keyboard is gone.
                bottomBar = { if (!keyboardOpen) BottomBar(navController) },
                snackbarHost = {
                    // The Scaffold places the host over the bottom of the window, which the keyboard
                    // covers. It keeps rising above the keyboard as it did before.
                    SnackbarHost(snackbar, modifier = Modifier.imePadding()) { data ->
                        Snackbar(snackbarData = data, modifier = Modifier.testTag("app_message"))
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Destination.Overview.route,
                    // The IME inset belongs to the content, not to the whole window: consumed above
                    // the Scaffold it lifted the bar along with everything else. The Scaffold's own
                    // padding goes on first and is then declared consumed, so imePadding() adds only
                    // what the keyboard needs beyond it instead of counting the system bar twice.
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding(),
                ) {
                    composable(Destination.Overview.route) {
                        // The Übersicht is the ONE way into the flow, so it says where the path is
                        // picked up rather than leaving the user to find the first step.
                        val step = state.resumeStep()
                        val beginning = step == FlowStep.Posting && state.posting == null
                        OverviewScreen(
                            state,
                            viewModel,
                            flowLabel = if (beginning) {
                                R.string.overview_flow_start
                            } else {
                                R.string.overview_flow_continue
                            },
                            onOpenFlow = { navController.openFromOverview(step.route) },
                            // The first minutes are a conversation where there is one to have. Null
                            // means this installation has no model behind it, and the Übersicht then
                            // offers the form as the first step exactly as it did before — the same
                            // "null is not applicable" idiom onBeginAnother uses below.
                            onOpenAssistant = if (state.hasAssistant) {
                                { navController.openFromOverview(Destination.Assistant.route) }
                            } else {
                                null
                            },
                            // A path that can be walked once is not a path the user moves along: with
                            // one application under way the action above leads back INTO it, and the
                            // next employer had nowhere to start from at all once the Stellenanzeige
                            // stopped being a tab. Beginning another drops the current posting the way
                            // the first step's own "Andere Anzeige einfügen" does; the application it
                            // produced stays, and is reached from the list below.
                            onOpenSettings = { navController.openSettings() },
                            onBeginAnother = if (beginning) {
                                null
                            } else {
                                {
                                    viewModel.clearPosting()
                                    navController.openFromOverview(FlowStep.Posting.route)
                                }
                            },
                        ) { route -> navController.openFromOverview(route) }
                    }
                    composable(Destination.Assistant.route) {
                        AssistantScreen(state, viewModel) {
                            navController.openFromOverview(Destination.Profile.route)
                        }
                    }
                    composable(Destination.Profile.route) { ProfileScreen(state, viewModel) }
                    composable(Destination.Locker.route) { LockerScreen(state, viewModel) }

                    // The settings and the legal pages. The pages are pushed ON TOP of the
                    // settings and leave by popping, which is what makes the arrow in their header
                    // lead back to the list they were opened from.
                    composable(SettingsRoute) {
                        SettingsScreen(
                            state,
                            viewModel,
                            onBack = { navController.openFromOverview(Destination.Overview.route) },
                        ) { page -> navController.navigate(page.route) }
                    }
                    LegalPage.entries.forEach { page ->
                        composable(page.route) {
                            LegalScreen(page, state, viewModel) { navController.popBackStack() }
                        }
                    }

                    // The flow. Each step is drawn inside the same rail, which is what says where
                    // along the path the user is — and the screen's own primary button is the move
                    // to the next step, as it already was.
                    composable(FlowStep.Posting.route) {
                        ApplicationFlow(FlowStep.Posting, state, navController) {
                            PostingScreen(state, viewModel) {
                                navController.openStep(FlowStep.Match)
                            }
                        }
                    }
                    composable(FlowStep.Match.route) {
                        ApplicationFlow(FlowStep.Match, state, navController) {
                            MatchScreen(state, viewModel) {
                                navController.openStep(FlowStep.Application)
                            }
                        }
                    }
                    composable(FlowStep.Application.route) {
                        ApplicationFlow(FlowStep.Application, state, navController) {
                            // A Maschinenlesbarkeit check with nothing to read leads to the place
                            // the field is filled in on, and it has to get there the way the bar
                            // does — a plain navigate() would stack the place on top of the step.
                            ApplicationScreen(state, viewModel) { route ->
                                navController.openFromOverview(route)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Opens the settings from the Übersicht.
 *
 * The Übersicht is popped and its state saved, the way the bar and every other link out of it do
 * it — a screen pushed on top of the Übersicht is what made the bar bring the user back to the
 * pushed screen instead of the list. Nothing is RESTORED here, and that is the difference to
 * [openFromOverview]: the settings open on the settings, not on whichever legal page was read last.
 */
private fun NavHostController.openSettings() {
    navigate(SettingsRoute) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
    }
}

/**
 * Follows a link out of the Übersicht — a next step, an application, the Mappe card, the flow.
 *
 * Every one of them leaves the Übersicht behind, so it has to leave it the way the bar does. A plain
 * navigate() pushed the destination on top of the Übersicht's own entry instead, and the bar then
 * had nothing to go back to: tapping "Übersicht" returned the user to the screen they had just
 * come from, and the list they came from could not be reached again at all.
 *
 * The Bewerbung's Maschinenlesbarkeit rows lead out the same way, for the same reason: they leave
 * the flow for a place, and a place reached from anywhere has to be reached as the bar reaches it.
 */
private fun NavHostController.openFromOverview(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Moves along the flow, forwards or back.
 *
 * Popping up to the step itself is what makes going BACK to an earlier one mean going back rather
 * than stacking a second copy of it: the steps walked since are dropped, so the system back gesture
 * still leads out of the flow and not around it. Where the step is not on the stack at all — the
 * Übersicht deep-links straight into the Bewerbung of an application — nothing is popped and the
 * step is pushed as usual.
 */
private fun NavHostController.openStep(step: FlowStep) {
    navigate(step.route) {
        popUpTo(step.route) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Whether a step has already produced what the step after it reads.
 *
 * This is the whole of what makes the path sequential, and the rail says it out loud instead of
 * letting the user discover it by tapping something that then explains it has nothing to show.
 */
private fun AppState.hasProduced(step: FlowStep): Boolean = when {
    // An application that is still being fetched has produced every step behind it: the
    // Stellenanzeige it was written against and the Abgleich it was written out of arrive with it.
    // Read off the nulls it is being fetched into, the rail spent that fetch drawing a finished
    // Abgleich as still to do, beside a Bewerbung the user was looking at.
    //
    // A fetch that FAILED leaves exactly those nulls standing, so it says the same thing and says
    // it until the screen is left: the letter and the Abgleich behind it exist, this phone simply
    // did not get them. Neither wait is a step to be sent back to — and neither has anything to
    // show, which is what [FlowRailStep] decides separately.
    isOpeningApplication || unfetchedApplication != null -> true
    else -> when (step) {
        FlowStep.Posting -> posting != null
        FlowStep.Match -> match != null
        FlowStep.Application -> application != null
    }
}

/// The step the Übersicht's one action leads to: the furthest along the path the user already got.
private fun AppState.resumeStep(): FlowStep =
    FlowStep.entries.lastOrNull { hasProduced(it) } ?: FlowStep.Posting

/**
 * The frame every step of the flow is drawn in: the rail, and the step's own screen below it.
 *
 * The bar is deliberately still there underneath. The steps stopped posing as places, but the
 * places have to stay reachable from inside the flow — a user halfway through an application who
 * remembers something about their profile should not have to finish first or press back three times.
 */
@Composable
private fun ApplicationFlow(
    step: FlowStep,
    state: AppState,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FlowRail(step, state) { target -> navController.openStep(target) }
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * The rail: which step this is, what the path is made of, and how far along it the user has got.
 *
 * A step the user has already been through is tappable and carries a check; one whose input does
 * not exist yet is drawn muted and does nothing, because there is nothing there to look at. That
 * is the same rule the screens themselves follow — the Abgleich already says "Noch keine
 * Stellenanzeige" — said here before the tap instead of after it.
 */
@Composable
private fun FlowRail(current: FlowStep, state: AppState, onOpen: (FlowStep) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = CardElevation) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m, vertical = Space.s)
                .testTag("flow_rail"),
        ) {
            SectionLabel(
                stringResource(R.string.flow_step_of, current.ordinal + 1, FlowStep.entries.size),
                Modifier.testTag("flow_step_caption"),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s),
            ) {
                // All three step names, resolved once: every one of them is measured against every
                // step, so that the rail settles on ONE size instead of three. See the label slot
                // in FlowRailStep.
                val labels = FlowStep.entries.map { stringResource(it.label) }
                FitOneLineTextGroup {
                    FlowStep.entries.forEach { step ->
                        if (step.ordinal > 0) {
                            // The line between two steps is what makes the row read as a path
                            // rather than as three tabs that happen to be numbered.
                            Box(
                                Modifier
                                    .padding(top = StepBadgeSize / 2)
                                    .width(Space.m)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                        }
                        FlowRailStep(step, current, state, Modifier.weight(1f), labels, onOpen)
                    }
                }
            }
        }
    }
}

/// The badge is the size of a pill, not of an [IconRow] icon: it sits in a rail, not in a list row.
private val StepBadgeSize = 24.dp

@Composable
private fun FlowRailStep(
    step: FlowStep,
    current: FlowStep,
    state: AppState,
    modifier: Modifier,
    peers: List<String>,
    onOpen: (FlowStep) -> Unit,
) {
    val colors = LocalSemanticColors.current
    val isCurrent = step == current
    val isDone = state.hasProduced(step)
    // The first step needs nothing to have happened; every other one needs its own output to exist
    // — and done is not the same as in hand. Behind an application that is on its way or that never
    // arrived the steps are finished and EMPTY: openApplication() cleared the Abgleich they lead to
    // before it asked for it. A tap offered there opens on "Noch keine Anzeige eingelesen", which is
    // the very sentence about a finished step this rail stopped saying.
    val reachable = step == FlowStep.entries.first() ||
        (isDone && !state.isOpeningApplication && state.unfetchedApplication == null)

    // The three tones are the ones the app already uses for these three meanings: filled primary
    // for where you are, the success tint for what is done, the neutral pill for what is not there.
    val (foreground, background) = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimary to MaterialTheme.colorScheme.primary
        isDone -> colors.success to colors.successTint
        else -> colors.muted to MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier
            .then(
                if (reachable && !isCurrent) Modifier.clickable { onOpen(step) } else Modifier,
            )
            .testTag(step.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(StepBadgeSize)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (isDone && !isCurrent) {
                Icon(
                    BewerboIcons.Check, contentDescription = null,
                    tint = foreground, modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    "${step.ordinal + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground,
                )
            }
        }
        // Shrunk rather than wrapped, for the reason the bar's labels are: three step names share
        // the screen width and "Stellenanzeige" does not fit a third of it at every font scale.
        //
        // Every name is handed all three words, and the three are one group, and that is what
        // makes the rail ONE path: shrinking each name only as far as its own third needed left
        // "Bewerbung" at full size beside a much smaller "Stellenanzeige" at a raised font size,
        // and three sizes in a row of three read as three separate controls. This is the fix the
        // bottom bar carries; the rail is the component's other caller and had the same fault.
        FitOneLineText(
            text = stringResource(step.label),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xs),
            // The tag names the LINE, not the place it is measured in: asked for its text, the
            // tagged box answered with the empty string, and a driver could only reach the word by
            // walking down to the child below it.
            testTag = "${step.tag}_label",
            style = MaterialTheme.typography.labelMedium,
            color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                reachable -> MaterialTheme.colorScheme.onSurface
                else -> colors.muted
            },
            peers = peers,
        )
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        // All four labels, resolved once: every one of them is measured against every item, so that
        // the bar settles on ONE size instead of four. See the label slot below.
        val labels = Destination.entries.map { stringResource(it.label) }
        FitOneLineTextGroup {
            Destination.entries.forEach { destination ->
                NavigationBarItem(
                    // The tag goes on the item itself — the clickable wrapper — not on the label
                    // inside it, so a driver taps a node that is actually clickable.
                    modifier = Modifier.testTag(destination.tag),
                    // Inside the flow no item is selected, and that is the point: the user is on a
                    // step of a path, not at one of the places, and the bar should not claim
                    // otherwise.
                    selected = current == destination.route,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = stringResource(destination.label),
                        )
                    },
                    label = {
                        // The destinations share the screen width, so a long word — "Application",
                        // "Bewerbung", "Вакансия" — met the edge of its item and wrapped onto a
                        // second line. The label shrinks to fit instead; its own tag lets a driver
                        // read the line back and see that it is still one line.
                        //
                        // The margin is what makes a whole word LOOK whole: shrunk to the last
                        // pixel of its item, "Unterlagen" ran into the screen edge, and a tab that
                        // ends at the edge reads as one that was cut off there. Four dp on each
                        // side cost one step of the ladder and give the word an end the reader can
                        // see.
                        //
                        // Every label is handed all four words, and the four are one group, and
                        // that is what makes the bar ONE row: shrinking each word only as far as
                        // its own item needed left "Profil" at full size beside a much smaller
                        // "Übersicht" at a raised font size, and four sizes in a row of four items
                        // read as four separate controls.
                        FitOneLineText(
                            text = stringResource(destination.label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.xs),
                            // The tag names the LINE, not the place it is measured in: asked for
                            // its text, the tagged box answered with the empty string, and a driver
                            // could only reach the word by walking down to the child below it.
                            testTag = "${destination.tag}_label",
                            style = MaterialTheme.typography.labelMedium,
                            peers = labels,
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
}
