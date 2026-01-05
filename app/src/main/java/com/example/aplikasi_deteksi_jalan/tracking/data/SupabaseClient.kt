package com.example.aplikasi_deteksi_jalan.tracking.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * SUPABASE CONFIGURATION
 * 
 * ✅ Configured dengan Supabase project credentials
 * Project: vvevoabcxdghvsrhshjq
 * 
 * ⚠️ WARNING: JANGAN PERNAH commit service_role key ke git!
 * Service_role key bypass RLS dan hanya untuk server-side.
 * Disini kita pakai anon key yang aman untuk client.
 */
object SupabaseConfig {
    // Project URL
    const val SUPABASE_URL = "https://vvevoabcxdghvsrhshjq.supabase.co"
    
    // Anon Key (aman untuk client-side, RLS aktif)
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ2ZXZvYWJjeGRnaHZzcmhzaGpxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjczODM2ODMsImV4cCI6MjA4Mjk1OTY4M30.OCVI0PyOa-4RnG5aMLWRxcFjrPvAAYRZG7zzllaLAu4"
}

/**
 * SUPABASE CLIENT (Singleton)
 * 
 * DESAIN:
 * - Singleton pattern untuk reuse connection
 * - Lazy initialization
 * - Thread-safe
 * 
 * TIDAK MENGGANGGU AI:
 * - Client terpisah dari AI module
 * - Network call async (coroutines)
 * - Tidak ada blocking operation
 */
object SupabaseClientManager {
    
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_KEY
        ) {
            // Auth module untuk login/signup
            install(Auth)
            
            // Postgrest untuk database CRUD
            install(Postgrest)
            
            // Realtime untuk subscribe location updates (with OkHttp engine)
            install(Realtime)
        }
    }
    
    /**
     * Check if client is configured
     */
    fun isConfigured(): Boolean {
        return !SupabaseConfig.SUPABASE_URL.contains("YOUR-PROJECT") &&
               !SupabaseConfig.SUPABASE_KEY.contains("YOUR-ANON")
    }
}
