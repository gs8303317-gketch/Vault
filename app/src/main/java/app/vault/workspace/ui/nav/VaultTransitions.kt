package app.vault.workspace.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Centralized Navigation Compose transition specs (Phase 1 motion).
 *
 * Forward hub / detail: Material-ish horizontal slide + fade.
 * Auth (FirstRun / Setup / Unlock): soft fade + slight scale (no harsh slide over PIN pad).
 * Viewer: same forward horizontal family for a consistent library→detail open.
 */
object VaultTransitions {

    private const val ForwardMs = 280
    private const val AuthMs = 240
    private const val ViewerMs = 300

    private val forwardEasing = FastOutSlowInEasing

    /** Push: new screen slides in from end. */
    val forwardEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ForwardMs, easing = forwardEasing),
            initialOffsetX = { fullWidth -> fullWidth },
        ) + fadeIn(animationSpec = tween(ForwardMs, easing = forwardEasing))

    /** Push: outgoing screen drifts slightly toward start + fades. */
    val forwardExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(ForwardMs, easing = forwardEasing),
            targetOffsetX = { fullWidth -> -fullWidth / 5 },
        ) + fadeOut(animationSpec = tween(ForwardMs, easing = forwardEasing))

    /** Pop: underlying screen returns from slight start offset. */
    val forwardPopEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ForwardMs, easing = forwardEasing),
            initialOffsetX = { fullWidth -> -fullWidth / 5 },
        ) + fadeIn(animationSpec = tween(ForwardMs, easing = forwardEasing))

    /** Pop: top screen slides out toward end. */
    val forwardPopExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(ForwardMs, easing = forwardEasing),
            targetOffsetX = { fullWidth -> fullWidth },
        ) + fadeOut(animationSpec = tween(ForwardMs, easing = forwardEasing))

    /** Soft auth enter — fade + gentle scale (no horizontal slide over credential UI). */
    val authEnter: EnterTransition =
        fadeIn(animationSpec = tween(AuthMs, easing = forwardEasing)) +
            scaleIn(
                animationSpec = tween(AuthMs, easing = forwardEasing),
                initialScale = 0.96f,
            )

    val authExit: ExitTransition =
        fadeOut(animationSpec = tween(AuthMs, easing = forwardEasing)) +
            scaleOut(
                animationSpec = tween(AuthMs, easing = forwardEasing),
                targetScale = 1.02f,
            )

    val authPopEnter: EnterTransition = authEnter

    val authPopExit: ExitTransition = authExit

    /**
     * Library→viewer open: horizontal slide + fade (same family as hub forward).
     * Kept as named aliases so viewer composables can override without drifting from defaults.
     */
    val viewerEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ViewerMs, easing = forwardEasing),
            initialOffsetX = { fullWidth -> fullWidth },
        ) + fadeIn(animationSpec = tween(ViewerMs, easing = forwardEasing))

    val viewerExit: ExitTransition = forwardExit

    val viewerPopEnter: EnterTransition = forwardPopEnter

    val viewerPopExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(ViewerMs, easing = forwardEasing),
            targetOffsetX = { fullWidth -> fullWidth },
        ) + fadeOut(animationSpec = tween(ViewerMs, easing = forwardEasing))
}
