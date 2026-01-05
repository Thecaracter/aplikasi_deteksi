package com.example.aplikasi_deteksi_jalan.tracking.data.repository

import com.example.aplikasi_deteksi_jalan.tracking.data.SupabaseClientManager
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Guardian
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

/**
 * GUARDIAN REPOSITORY
 * 
 * Handle CRUD guardian relationships
 * 
 * FLOW:
 * 1. User add guardian by email
 * 2. Check guardian exists & role = guardian
 * 3. Insert guardian request (status = pending)
 * 4. User accept/reject guardian request
 * 5. Guardian dapat lihat lokasi jika status = accepted
 */
class GuardianRepository {
    
    private val client = SupabaseClientManager.client
    
    /**
     * ADD GUARDIAN (Request Izin via Invitation Code)
     * 
     * @param invitationCode Kode unik dari user yang akan ditambahkan sebagai guardian
     * @return Result<Guardian> jika sukses, Result<Exception> jika gagal
     */
    suspend fun addGuardian(invitationCode: String): Result<Guardian> {
        return try {
            val currentUserId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User tidak login"))
            
            android.util.Log.d("GuardianRepo", "Looking for invitation code: $invitationCode")
            
            // 1. Cari user by invitation code
            val codeToSearch = invitationCode.trim().lowercase()
            android.util.Log.d("GuardianRepo", "Query with code: $codeToSearch")
            
            val response = try {
                client.from("profiles")
                    .select {
                        filter {
                            eq("invitation_code", codeToSearch)
                        }
                    }
                    .decodeList<Profile>()
            } catch (e: Exception) {
                android.util.Log.e("GuardianRepo", "Query error: ${e.message}", e)
                e.printStackTrace()
                emptyList()
            }
            
            android.util.Log.d("GuardianRepo", "Search code: $invitationCode, Found: ${response.size}")
            response.forEach { profile ->
                android.util.Log.d("GuardianRepo", "Profile found: email=${profile.email}, code=${profile.invitationCode}, role=${profile.role}")
            }
            
            val userProfile = response.firstOrNull()
            
            // 2. Cek apakah kode ditemukan
            if (userProfile == null) {
                return Result.failure(Exception("Kode '$invitationCode' tidak ditemukan"))
            }
            
            android.util.Log.d("GuardianRepo", "Found profile: ${userProfile.email} with role ${userProfile.role}")
            
            // 3. Cek apakah role = guardian (bukan user)
            if (userProfile.role != "guardian") {
                return Result.failure(Exception("Kode ini milik ${userProfile.role}, bukan guardian"))
            }
            
            // 4. Cek apakah guardian sudah ditambahkan sebelumnya
            val existing = client.from("guardians")
                .select {
                    filter {
                        eq("user_id", currentUserId)
                        eq("guardian_id", userProfile.id)
                    }
                }
                .decodeList<Guardian>()
                .filter { it.userId == currentUserId && it.guardianId == userProfile.id }
            
            if (existing.isNotEmpty()) {
                return Result.failure(Exception("Guardian ini sudah ditambahkan"))
            }
            
            // 5. Insert guardian request
            val guardian = Guardian(
                userId = currentUserId,
                guardianId = userProfile.id,
                status = "pending"
            )
            
            val inserted = client.from("guardians")
                .insert(guardian)
                .decodeSingle<Guardian>()
            
            println("[GuardianRepo] ✅ Guardian request created: ${userProfile.email}")
            Result.success(inserted)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error adding guardian: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * ACCEPT GUARDIAN REQUEST
     * 
     * @param guardianRequestId ID dari guardian request
     */
    suspend fun acceptGuardian(guardianRequestId: String): Result<Unit> {
        return try {
            client.from("guardians")
                .update(mapOf("status" to "accepted")) {
                    filter {
                        eq("id", guardianRequestId)
                    }
                }
            
            println("[GuardianRepo] ✅ Guardian request accepted: $guardianRequestId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error accepting guardian: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * REJECT GUARDIAN REQUEST
     * 
     * @param guardianRequestId ID dari guardian request
     */
    suspend fun rejectGuardian(guardianRequestId: String): Result<Unit> {
        return try {
            client.from("guardians")
                .update(mapOf("status" to "rejected")) {
                    filter {
                        eq("id", guardianRequestId)
                    }
                }
            
            println("[GuardianRepo] ✅ Guardian request rejected: $guardianRequestId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error rejecting guardian: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET PENDING GUARDIAN REQUESTS
     * 
     * Untuk user melihat guardian requests yang belum di-accept/reject
     */
    suspend fun getPendingGuardians(): Result<List<Guardian>> {
        return try {
            val currentUserId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User not logged in"))
            
            val guardians = client.from("guardians")
                .select {
                    filter {
                        eq("user_id", currentUserId)
                        eq("status", "pending")
                    }
                }
                .decodeList<Guardian>()
            
            println("[GuardianRepo] ✅ Found ${guardians.size} pending guardians")
            Result.success(guardians)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting pending guardians: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET ACCEPTED GUARDIANS
     * 
     * Untuk user melihat guardian yang sudah accepted
     */
    suspend fun getAcceptedGuardians(): Result<List<Guardian>> {
        return try {
            val currentUserId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User not logged in"))
            
            val guardians = client.from("guardians")
                .select {
                    filter {
                        eq("user_id", currentUserId)
                        eq("status", "accepted")
                    }
                }
                .decodeList<Guardian>()
            
            println("[GuardianRepo] ✅ Found ${guardians.size} accepted guardians")
            Result.success(guardians)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting accepted guardians: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * REMOVE GUARDIAN (Revoke Access)
     * 
     * @param guardianRequestId ID dari guardian request yang akan dihapus
     */
    suspend fun removeGuardian(guardianRequestId: String): Result<Unit> {
        return try {
            client.from("guardians")
                .delete {
                    filter {
                        eq("id", guardianRequestId)
                    }
                }
            
            println("[GuardianRepo] ✅ Guardian removed: $guardianRequestId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error removing guardian: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET GUARDIAN PROFILE BY ID
     * 
     * Helper function untuk get profile guardian
     */
    suspend fun getGuardianProfile(guardianId: String): Result<Profile> {
        return try {
            val profile = client.from("profiles")
                .select {
                    filter {
                        eq("id", guardianId)
                    }
                }
                .decodeSingle<Profile>()
            
            Result.success(profile)
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting guardian profile: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET APPROVED USERS - For Guardian to see which users they can monitor
     * 
     * Returns list of users who have accepted this guardian
     */
    suspend fun getApprovedUsers(guardianId: String): List<Guardian> {
        return try {
            val guardians = client.from("guardians")
                .select {
                    filter {
                        eq("guardian_id", guardianId)
                        eq("status", "accepted")
                    }
                }
                .decodeList<Guardian>()
            
            println("[GuardianRepo] ✅ Found ${guardians.size} approved users for guardian")
            guardians
            
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting approved users: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * GET PROFILE BY ID
     * 
     * @param userId ID user yang akan diambil profilenya
     * @return Profile atau null jika tidak ditemukan
     */
    suspend fun getProfileById(userId: String): Profile? {
        return try {
            val response = client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<Profile>()
            
            response.firstOrNull()
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting profile: ${e.message}")
            null
        }
    }
    
    /**
     * GET PENDING REQUESTS FOR GUARDIAN
     * 
     * Returns list of pending guardian requests where guardian_id = currentUserId
     */
    suspend fun getPendingRequestsForGuardian(guardianId: String): Result<List<Guardian>> {
        return try {
            val requests = client.from("guardians")
                .select {
                    filter {
                        eq("guardian_id", guardianId)
                        eq("status", "pending")
                    }
                }
                .decodeList<Guardian>()
            
            println("[GuardianRepo] ✅ Found ${requests.size} pending requests for guardian")
            Result.success(requests)
        } catch (e: Exception) {
            println("[GuardianRepo] ❌ Error getting pending requests: ${e.message}")
            Result.failure(e)
        }
    }}