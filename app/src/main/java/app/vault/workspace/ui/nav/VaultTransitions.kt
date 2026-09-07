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
 * Centralized Navigation Compose transition specs.
 *
 * Phase 1: Material-ish horizontal slide + fade for hub / detail; soft fade+scale for auth.
 * Phase 2: Viewer uses soft fade (+ slight scale) so shared-element thumb morph is the hero
 * motion — horizontal slide fought [SharedTransitionLayout] sharedElement / sharedBounds.
 * Phase 3: Unlock/setup → Library uses [authToLibraryEnter] (fade only) so it does not
 * stack with auth [authExit] scale; overlay micro-motion lives in [VaultMotion].
 *
 * Predictive back: foundation enabled via manifest; viewer [BackHandler] still calls
 * exitViewer() (bars restore + pause) before pop. Shared-element scrub during the
 * predictive gesture is best-effort when immersive restore runs first.
 */
object VaultTransitions {

    private const val ForwardMs = 280
    private const val AuthMs = 240
    private const val ViewerMs = 320

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
     * Unlock / setup → Library: fade only (no scale, no horizontal slide).
     * Avoids stacking with [authExit] scaleOut (Phase 3 double-animation fix).
     */
    val authToLibraryEnter: EnterTransition =
        fadeIn(animationSpec = tween(AuthMs, easing = forwardEasing))

    /**
     * Library→viewer: soft fade + slight scale so Phase 2 shared-element thumb morph
     * reads as the primary motion (horizontal slide competed with sharedElement).
     */
    val viewerEnter: EnterTransition =
        fadeIn(animationSpec = tween(ViewerMs, easing = forwardEasing)) +
            scaleIn(
                animationSpec = tween(ViewerMs, easing = forwardEasing),
                initialScale = 0.96f,
            )

    val viewerExit: ExitTransition =
        fadeOut(animationSpec = tween(ViewerMs / 2, easing = forwardEasing))

    val viewerPopEnter: EnterTransition =
        fadeIn(animationSpec = tween(ViewerMs, easing = forwardEasing))

    val viewerPopExit: ExitTransition =
        fadeOut(animationSpec = tween(ViewerMs, easing = forwardEasing)) +
            scaleOut(
                animationSpec = tween(ViewerMs, easing = forwardEasing),
                targetScale = 0.96f,
            )
}
