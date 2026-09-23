package com.medtrack.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.medtrack.app.R
import com.medtrack.app.hybrid.account.AuthSessionState
import com.medtrack.app.ui.theme.LocalPaperColors

private val LaunchBackground = Color(0xFFF4F8FA)

@Composable
fun SignInScreen(
    onGoogleClick: () -> Unit,
    onOfflineUnlock: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val colors = LocalPaperColors.current
    val state by viewModel.state.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunchBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "MedTrack",
                modifier = Modifier.size(196.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "MedTrack",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ward rounds, kept on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1.15f))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Use the Google account provisioned for this ward. You stay signed in until you log out from your profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    if (state.sessionState == AuthSessionState.AUTHENTICATED_UNPROVISIONED) {
                        Text(
                            text = "This Google account is signed in, and MedTrack care is not enabled for it yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.danger
                        )
                        OutlinedButton(
                            onClick = onProfileClick,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("My Profile")
                        }
                    }
                    if (state.error?.isNotBlank() == true) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.danger
                        )
                    }
                    OutlinedButton(
                        onClick = onGoogleClick,
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = colors.textPrimary
                        )
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    if (state.canOfflineUnlock) {
                        TextButton(
                            onClick = onOfflineUnlock,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unlock with device credential")
                        }
                    }
                    if (state.debugBypass) {
                        TextButton(
                            onClick = viewModel::continueSynthetic,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue with synthetic Ankita (debug)")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Charts stay on this phone. Signing in opens the records already here.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
