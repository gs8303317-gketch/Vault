package app.vault.workspace.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties

/**
 * Overlay / micro-interaction motion specs (Motion Phase 3).
 *
 * Nav route transitions live in [VaultTransitions]. This object covers dialogs,
 * FABs, import banners, and light press feedback — keep timings short so overlays
 * feel snappy without fighting shared-element or immersive chrome.
 */
object VaultMotion {

    const val DialogMs = 200
    const val FabMs = 180
    const val OverlayMs = 200
    const val PressMs = 100

    /** Subtle card press scale (Library grid). Keep mild to avoid scroll lag. */
    const val PressScale = 0.96f

    /** Dialog / sheet content enter: fade + gentle scale. */
    const val DialogInitialScale = 0.92f

    private val easing = FastOutSlowInEasing

    val dialogEnter: EnterTransition =
        fadeIn(animationSpec = tween(DialogMs, easing = easing)) +
            scaleIn(
                animationSpec = tween(DialogMs, easing = easing),
                initialScale = DialogInitialScale,
            )

    val dialogExit: ExitTransition =
        fadeOut(animationSpec = tween(DialogMs * 3 / 4, easing = easing)) +
            scaleOut(
                animationSpec = tween(DialogMs * 3 / 4, easing = easing),
                targetScale = DialogInitialScale,
            )

    val fabEnter: EnterTransition =
        fadeIn(animationSpec = tween(FabMs, easing = easing)) +
            scaleIn(
                animationSpec = tween(FabMs, easing = easing),
                initialScale = 0.85f,
            )

    val fabExit: ExitTransition =
        fadeOut(animationSpec = tween(FabMs * 3 / 4, easing = easing)) +
            scaleOut(
                animationSpec = tween(FabMs * 3 / 4, easing = easing),
                targetScale = 0.85f,
            )

    /** Import progress / status strip. */
    val overlayEnter: EnterTransition =
        fadeIn(animationSpec = tween(OverlayMs, easing = easing)) +
            slideInVertically(
                animationSpec = tween(OverlayMs, easing = easing),
                initialOffsetY = { -it / 3 },
            )

    val overlayExit: ExitTransition =
        fadeOut(animationSpec = tween(OverlayMs * 3 / 4, easing = easing)) +
            slideOutVertically(
                animationSpec = tween(OverlayMs * 3 / 4, easing = easing),
                targetOffsetY = { -it / 3 },
            )

    /**
     * Material [AlertDialog] / [Dialog] defaults already animate via the window;
     * these properties keep dismiss (scrim tap + back) enabled and snappy.
     */
    val dialogProperties: DialogProperties =
        DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true,
        )

    /** Full-bleed custom dialogs (e.g. PDF page grid). */
    val fullWidthDialogProperties: DialogProperties =
        DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        )
}

/**
 * Runs [content] with a one-shot enter animation when the hosting Dialog first appears.
 * Exit is best-effort (Dialog removes the window immediately on dismiss); Material
 * window fade still covers the hide.
 */
@Composable
fun VaultDialogEnter(
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = VaultMotion.dialogEnter,
        exit = VaultMotion.dialogExit,
    ) {
        content()
    }
}
