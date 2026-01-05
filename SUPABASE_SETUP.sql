-- ===============================================
-- SUPABASE DATABASE SETUP
-- Copy & paste ke Supabase SQL Editor
-- ===============================================

-- ==============================================
-- 1. PROFILES TABLE
-- ==============================================
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    full_name TEXT,
    role TEXT NOT NULL CHECK (role IN ('user', 'guardian')),
    invitation_code TEXT UNIQUE NOT NULL DEFAULT substring(
        gen_random_uuid ()::text,
        1,
        8
    ),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_profiles_role ON profiles (role);

CREATE INDEX idx_profiles_invitation_code ON profiles (invitation_code);

-- ==============================================
-- 2. GUARDIANS TABLE
-- ==============================================
CREATE TABLE guardians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    user_id UUID NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    guardian_id UUID NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (
        status IN (
            'pending',
            'accepted',
            'rejected'
        )
    ),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT guardian_not_self CHECK (user_id != guardian_id),
    CONSTRAINT unique_guardian_user UNIQUE (user_id, guardian_id)
);

CREATE INDEX idx_guardians_user ON guardians (user_id);

CREATE INDEX idx_guardians_guardian ON guardians (guardian_id);

CREATE INDEX idx_guardians_status ON guardians (status);

-- ==============================================
-- 3. LOCATION_CURRENT TABLE
-- ==============================================
CREATE TABLE location_current (
    user_id UUID PRIMARY KEY REFERENCES profiles (id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy FLOAT,
    tracking_enabled BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_location_tracking_enabled ON location_current (tracking_enabled);

-- ==============================================
-- 4. ENABLE REALTIME
-- ==============================================
ALTER PUBLICATION supabase_realtime ADD TABLE location_current;

-- ==============================================
-- 5. AUTO UPDATE TIMESTAMP TRIGGER
-- ==============================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_updated_at
  BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER guardians_updated_at
  BEFORE UPDATE ON guardians
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER location_updated_at
  BEFORE UPDATE ON location_current
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ==============================================
-- 6. AUTO CREATE PROFILE ON SIGNUP
-- ==============================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, email, role)
  VALUES (
    NEW.id,
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'role', 'user')
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================
-- 7. ENABLE RLS
-- ==============================================
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

ALTER TABLE guardians ENABLE ROW LEVEL SECURITY;

ALTER TABLE location_current ENABLE ROW LEVEL SECURITY;

-- ==============================================
-- 8. RLS POLICIES - PROFILES
-- ==============================================
CREATE POLICY "Users can view own profile" ON profiles FOR
SELECT USING (auth.uid () = id);

CREATE POLICY "Users can update own profile" ON profiles
FOR UPDATE
    USING (auth.uid () = id);

CREATE POLICY "Guardian can view user profile by code" ON profiles FOR
SELECT USING (
        role = 'user'
        AND EXISTS (
            SELECT 1
            FROM guardians
            WHERE
                guardians.user_id = profiles.id
                AND guardians.guardian_id = auth.uid ()
                AND guardians.status = 'accepted'
        )
    );

CREATE POLICY "Guardian can search user by invitation code" ON profiles FOR
SELECT USING (
        role = 'user'
        AND (auth.uid () IS NOT NULL)
    );

-- ==============================================
-- 9. RLS POLICIES - GUARDIANS
-- ==============================================
CREATE POLICY "Users can view their guardian requests" ON guardians FOR
SELECT USING (user_id = auth.uid ());

CREATE POLICY "Guardians can view their requests" ON guardians FOR
SELECT USING (guardian_id = auth.uid ());

CREATE POLICY "Users can insert guardian requests" ON guardians FOR INSERT
WITH
    CHECK (user_id = auth.uid ());

CREATE POLICY "Users can update guardian status" ON guardians
FOR UPDATE
    USING (user_id = auth.uid ());

CREATE POLICY "Guardians can update their requests" ON guardians
FOR UPDATE
    USING (
        guardian_id = auth.uid ()
        AND status = 'pending'
    );

CREATE POLICY "Users can delete guardians" ON guardians FOR DELETE USING (user_id = auth.uid ());

-- ==============================================
-- 10. RLS POLICIES - LOCATION_CURRENT
-- ==============================================
CREATE POLICY "Users can upsert own location" ON location_current FOR INSERT
WITH
    CHECK (user_id = auth.uid ());

CREATE POLICY "Users can update own location" ON location_current
FOR UPDATE
    USING (user_id = auth.uid ());

CREATE POLICY "Users can view own location" ON location_current FOR
SELECT USING (user_id = auth.uid ());

CREATE POLICY "Guardian can view user location" ON location_current FOR
SELECT USING (
        tracking_enabled = TRUE
        AND user_id IN (
            SELECT user_id
            FROM guardians
            WHERE
                guardian_id = auth.uid ()
                AND status = 'accepted'
        )
    );

-- ===============================================
-- SETUP COMPLETE! 🎉
-- ===============================================
-- Selanjutnya:
-- 1. Test dengan signup user baru
-- 2. Cek table profiles auto-create
-- 3. Test RLS dengan query dari client
-- ===============================================