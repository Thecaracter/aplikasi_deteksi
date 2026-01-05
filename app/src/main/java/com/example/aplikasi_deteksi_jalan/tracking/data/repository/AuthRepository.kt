package com.example.aplikasi_deteksi_jalan.tracking.data.repository

import com.example.aplikasi_deteksi_jalan.tracking.data.SupabaseClientManager
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

/**
 * AUTH REPOSITORY
 * 
 * Handle authentication dengan Supabase Auth
 * 
 * FLOW:
 * 1. Signup → Supabase Auth creates user
 * 2. Trigger function di database auto-create profile
 * 3. Login → Get user ID & token
 * 4. Logout → Clear session
 */
class AuthRepository {
    
    private val client = SupabaseClientManager.client
    
    /**
     * SIGNUP (Register User Baru)
     * 
     * @param email Email user
     * @param password Password (min 6 characters)
     * @param fullName Nama lengkap (opsional)
     * @param role "user" (tunanetra) atau "guardian" (wali)
     * 
     * NOTE: Untuk menghindari "Email not confirmed" error:
     * 1. Buka Supabase Dashboard → Authentication → Settings
     * 2. Scroll ke "Email Auth" section
     * 3. Disable "Enable email confirmations"
     * ATAU gunakan email yang sudah di-confirm manual
     */
    suspend fun signup(
        email: String,
        password: String,
        fullName: String? = null,
        role: String = "user"
    ): Result<Profile> {
        return try {
            // 1. Signup ke Supabase Auth
            val signUpResult = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            println("[AuthRepo] 📧 Signup result - User ID: ${signUpResult?.id}")
            
            // 2. Check if email confirmation is required
            val session = client.auth.currentSessionOrNull()
            if (session == null) {
                throw Exception("Email confirmation diperlukan. Cek inbox email Anda atau disable email confirmation di Supabase Dashboard (Authentication → Settings → Email Auth)")
            }
            
            // 3. Wait for profile to be created by trigger
            kotlinx.coroutines.delay(2000)
            
            // 4. Get user ID from session
            val userId = client.auth.currentUserOrNull()?.id 
                ?: throw Exception("Login failed after signup")
            
            // 5. Update role di database
            val updateResult = client.from("profiles")
                .update({
                    set("role", role)
                }) {
                    filter {
                        eq("id", userId)
                    }
                }
            
            println("[AuthRepo] 📝 Role update executed for: $userId with role: $role")
            
            // 6. Wait a bit for update to propagate
            kotlinx.coroutines.delay(500)
            
            // 7. Query profile dari database (fetch fresh data after update)
            val profile = client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<Profile>()
            
            println("[AuthRepo] ✅ Signup success: ${profile.email}, role: ${profile.role}")
            Result.success(profile)
            
        } catch (e: Exception) {
            println("[AuthRepo] ❌ Signup error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * LOGIN
     * 
     * @param email Email user
     * @param password Password
     */
    suspend fun login(email: String, password: String): Result<Profile> {
        return try {
            // 1. Login ke Supabase Auth
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            val userId = client.auth.currentUserOrNull()?.id
                ?: throw Exception("User not found after login")
            
            // 2. Get profile dari database
            val profile = client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<Profile>()
            
            println("[AuthRepo] ✅ Login success: ${profile.email} (${profile.role})")
            Result.success(profile)
            
        } catch (e: Exception) {
            println("[AuthRepo] ❌ Login error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * LOGOUT
     */
    suspend fun logout(): Result<Unit> {
        return try {
            client.auth.signOut()
            println("[AuthRepo] ✅ Logout success")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[AuthRepo] ❌ Logout error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET CURRENT USER PROFILE
     */
    suspend fun getCurrentProfile(): Result<Profile> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User not logged in"))
            
            val profile = client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<Profile>()
            
            Result.success(profile)
            
        } catch (e: Exception) {
            println("[AuthRepo] ❌ Error getting profile: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * CHECK IF USER IS LOGGED IN
     */
    fun isLoggedIn(): Boolean {
        return client.auth.currentUserOrNull() != null
    }
    
    /**
     * GET CURRENT USER ID
     */
    fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }
}
