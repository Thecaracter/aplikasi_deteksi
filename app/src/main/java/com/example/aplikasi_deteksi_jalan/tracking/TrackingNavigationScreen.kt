package com.example.aplikasi_deteksi_jalan.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.AuthRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import com.example.aplikasi_deteksi_jalan.tracking.presentation.GuardianMonitorScreenWithOSM
import com.example.aplikasi_deteksi_jalan.tracking.presentation.GuardianMapDetailScreen
import com.example.aplikasi_deteksi_jalan.tracking.ui.UserTrackingScreen
import kotlinx.coroutines.launch

/**
 * Wrapper screen untuk tracking navigation dengan back button
 * Menampilkan UI berbeda berdasarkan role (user atau guardian)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingNavigationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val authRepository = remember { AuthRepository() }
    val trackingPreferences = remember { TrackingPreferences(context) }
    val trackingLocationManager = remember { TrackingLocationManager(context) }
    
    var isLoading by remember { mutableStateOf(true) }
    var currentRole by remember { mutableStateOf<String?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMapDetail by remember { mutableStateOf(false) }
    var mapDetailUserId by remember { mutableStateOf<String?>(null) }
    var mapDetailUserName by remember { mutableStateOf<String?>(null) }
    
    // Check login status dan role
    LaunchedEffect(Unit) {
        try {
            isLoggedIn = trackingPreferences.isLoggedIn()
            
            if (isLoggedIn) {
                // Get role from preferences or database
                currentRole = trackingPreferences.userRole
                
                if (currentRole == null) {
                    // Query dari database jika belum di-cache
                    val profileResult = authRepository.getCurrentProfile()
                    if (profileResult.isSuccess) {
                        currentRole = profileResult.getOrNull()?.role
                        currentRole?.let { trackingPreferences.saveUserRole(it) }
                    }
                }
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
        }
    }
    
    // Removed Scaffold topBar to fix double topbar issue
    // Each screen (UserTrackingScreen, GuardianMonitorScreen) has its own topbar
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading -> {
                // Loading state
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            errorMessage != null -> {
                // Error state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⚠️ Error",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Terjadi kesalahan",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Kembali")
                    }
                }
            }
            
            !isLoggedIn -> {
                // Show login/register screen at start
                var showRegister by remember { mutableStateOf(false) }
                
                if (showRegister) {
                    RegisterScreen(
                        onRegisterSuccess = { role: String ->
                            isLoggedIn = true
                            currentRole = role
                            trackingPreferences.saveUserRole(role)
                        },
                        onNavigateToLogin = { showRegister = false }
                    )
                } else {
                    LoginScreen(
                        onLoginSuccess = { role: String ->
                            isLoggedIn = true
                            currentRole = role
                            trackingPreferences.saveUserRole(role)
                        },
                        onNavigateToRegister = { showRegister = true }
                    )
                }
            }
            
            currentRole == "guardian" -> {
                // Show map detail or guardian monitor screen
                if (showMapDetail && mapDetailUserId != null) {
                    GuardianMapDetailScreen(
                        userId = mapDetailUserId!!,
                        userName = mapDetailUserName ?: "User",
                        onNavigateBack = {
                            showMapDetail = false
                            mapDetailUserId = null
                            mapDetailUserName = null
                        }
                    )
                } else {
                    // Guardian list view
                    GuardianMonitorScreenWithOSM(
                        onNavigateBack = onBack,
                        onNavigateToMapDetail = { userId, userEmail ->
                            showMapDetail = true
                            mapDetailUserId = userId
                            mapDetailUserName = userEmail
                        },
                        onLogout = {
                            isLoggedIn = false
                            currentRole = null
                        }
                    )
                }
            }
            
            else -> {
                // Default: User screen (tunanetra)
                UserTrackingScreen(
                    trackingPreferences = trackingPreferences,
                    trackingLocationManager = trackingLocationManager,
                    onNavigateBack = onBack,
                    onLogout = {
                        // Reset state to show login screen
                        isLoggedIn = false
                        currentRole = null
                    }
                )
            }
        }
    }
}

/**
 * Login Screen - Modern & Accessible Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val authRepository = remember { AuthRepository() }
    val trackingPreferences = remember { TrackingPreferences(context) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Icon/Title
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Header
                    Text(
                        text = "Selamat Datang",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Masuk untuk melanjutkan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = errorMessage != null,
                        shape = MaterialTheme.shapes.medium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password"
                                )
                            }
                        },
                        isError = errorMessage != null,
                        shape = MaterialTheme.shapes.medium
                    )
                    
                    // Error Message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Login Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                
                                val result = authRepository.login(email, password)
                                
                                result.fold(
                                    onSuccess = { profile ->
                                        trackingPreferences.saveLoginState(true, profile.id, profile.email)
                                        trackingPreferences.saveUserRole(profile.role)
                                        onLoginSuccess(profile.role)
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message ?: "Login gagal"
                                        isLoading = false
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Masuk",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Navigate to Register
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Belum punya akun? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onNavigateToRegister,
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "Daftar Sekarang",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Register Screen - Modern & Accessible Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val authRepository = remember { AuthRepository() }
    val trackingPreferences = remember { TrackingPreferences(context) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("user") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Daftar Akun",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Buat akun baru untuk mulai tracking",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Role Selection
            Text(
                text = "Pilih Role",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedRole == "user",
                    onClick = { selectedRole = "user" },
                    label = { Text("User (Tunanetra)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedRole == "guardian",
                    onClick = { selectedRole = "guardian" },
                    label = { Text("Guardian (Wali)") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = errorMessage != null
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                },
                isError = errorMessage != null,
                supportingText = { Text("Minimal 6 karakter") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Konfirmasi Password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Text(if (confirmPasswordVisible) "Hide" else "Show")
                    }
                },
                isError = errorMessage != null || (confirmPassword.isNotEmpty() && password != confirmPassword)
            )
            
            // Error Message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            } else if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Password tidak cocok",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Register Button
            Button(
                onClick = {
                    if (password != confirmPassword) {
                        errorMessage = "Password tidak cocok"
                        return@Button
                    }
                    
                    if (password.length < 6) {
                        errorMessage = "Password minimal 6 karakter"
                        return@Button
                    }
                    
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        val result = authRepository.signup(email, password, role = selectedRole)
                        
                        result.fold(
                            onSuccess = { profile ->
                                trackingPreferences.saveLoginState(true, profile.id, profile.email)
                                trackingPreferences.saveUserRole(profile.role)
                                onRegisterSuccess(profile.role)
                            },
                            onFailure = { error ->
                                errorMessage = error.message ?: "Pendaftaran gagal"
                                isLoading = false
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && 
                         confirmPassword.isNotBlank() && password == confirmPassword
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Daftar",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Navigate to Login
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Sudah punya akun? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onNavigateToLogin,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
