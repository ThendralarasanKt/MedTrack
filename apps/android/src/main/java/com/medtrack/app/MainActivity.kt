package com.medtrack.app

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medtrack.app.hybrid.account.GoogleSignInCancelled
import com.medtrack.app.hybrid.account.GoogleSignInHelper
import com.medtrack.app.ui.auth.AuthViewModel
import com.medtrack.app.ui.auth.SignInScreen
import com.medtrack.app.ui.common.components.WorkspaceBottomBar
import com.medtrack.app.ui.navigation.AppNavGraph
import com.medtrack.app.ui.navigation.Screen
import com.medtrack.app.ui.profile.ProfileScreen
import com.medtrack.app.ui.theme.MedTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var authViewModel: AuthViewModel? = null

    private val localUnlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val viewModel = authViewModel ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            viewModel.onLocalUnlockVerified()
        } else {
            viewModel.onLocalUnlockCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedTrackTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                this.authViewModel = authViewModel
                val auth by authViewModel.state.collectAsState()
                if (!auth.unlocked) {
                    if (auth.viewingProfile) {
                        ProfileScreen(
                            onBack = { authViewModel.closeProfile() },
                            onSignedOut = { authViewModel.signOut() },
                            viewModel = hiltViewModel()
                        )
                    } else {
                        SignInScreen(
                            onGoogleClick = { startGoogleSignIn(authViewModel) },
                            onOfflineUnlock = { startLocalUnlock(authViewModel) },
                            onProfileClick = { authViewModel.openProfile() },
                            viewModel = authViewModel
                        )
                    }
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (Screen.showsBottomBar(currentRoute)) {
                                WorkspaceBottomBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(Screen.Census.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            AppNavGraph(
                                navController = navController,
                                onSignedOut = { authViewModel.signOut() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startLocalUnlock(viewModel: AuthViewModel) {
        viewModel.requestOfflineUnlock()
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock MedTrack",
            "Confirm it is you to open local records on this device."
        )
        if (intent == null) {
            viewModel.onGoogleFailure("Set a screen lock to unlock records offline.")
            return
        }
        localUnlockLauncher.launch(intent)
    }

    private fun startGoogleSignIn(viewModel: AuthViewModel) {
        lifecycleScope.launch {
            viewModel.beginGoogleSignIn()
            runCatching { GoogleSignInHelper(this@MainActivity).signIn() }
                .onSuccess { result ->
                    viewModel.onGoogleToken(
                        idToken = result.firebaseIdToken,
                        subject = result.subject,
                        displayName = result.displayName
                    )
                }
                .onFailure { error ->
                    if (error is GoogleSignInCancelled) {
                        viewModel.onGoogleFailure("")
                        return@launch
                    }
                    viewModel.onGoogleFailure(
                        error.message ?: "Google sign-in could not be completed."
                    )
                }
        }
    }
}
