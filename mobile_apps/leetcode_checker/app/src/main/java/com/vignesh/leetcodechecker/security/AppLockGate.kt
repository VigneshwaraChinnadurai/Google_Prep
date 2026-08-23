package com.vignesh.leetcodechecker.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vignesh.leetcodechecker.AppSettingsStore

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Gates [content] behind a biometric (or device PIN/pattern fallback) prompt, like a
 * payment app: re-locks whenever the app is backgrounded (ON_STOP), not just at cold
 * start. Auto-unlocks (fails open) on devices with no biometric/PIN/pattern enrolled at
 * all -- there's nothing to authenticate against, so gating would just lock the user out
 * permanently instead of protecting anything.
 */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var isUnlocked by remember { mutableStateOf(false) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Don't re-lock mid-prompt -- the device-credential fallback UI is a separate
            // system activity, which triggers ON_STOP on this one while it's showing.
            if (event == Lifecycle.Event.ON_STOP && !isAuthenticating) {
                isUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requireLock = remember(isUnlocked) {
        AppSettingsStore.load(context).requireBiometricLock
    }

    fun attemptUnlock() {
        val canAuthenticate = BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            isUnlocked = true
            return
        }
        isAuthenticating = true
        lockError = null
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock LeetCode Checker")
            .setSubtitle("Verify it's you to continue")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticating = false
                    isUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                    val isUserDismiss = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!isUserDismiss) lockError = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    // A single failed match, not a terminal error -- the prompt stays open.
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(requireLock, isUnlocked) {
        if (requireLock && !isUnlocked && !isAuthenticating) {
            attemptUnlock()
        }
    }

    if (!requireLock || isUnlocked) {
        content()
    } else {
        LockScreen(errorMessage = lockError, onRetry = ::attemptUnlock)
    }
}

@Composable
private fun LockScreen(errorMessage: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
            )
            Text("LeetCode Checker is locked", style = MaterialTheme.typography.titleMedium)
            errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(onClick = onRetry) {
                Text("Unlock")
            }
        }
    }
}
