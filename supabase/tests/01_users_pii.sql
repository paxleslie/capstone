-- RLS test: PII isolation in public.users
-- Verifies:
--   * a user can only SELECT their own row
--   * an admin of a shared household cannot see members' rows
--   * a user cannot see anyone in a different household

CREATE EXTENSION IF NOT EXISTS pgtap;

BEGIN;
SELECT plan(4);

-- ===== seed (runs as postgres / superuser, bypasses RLS) =====
INSERT INTO auth.users (instance_id, id, aud, role, email, email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
VALUES
  ('00000000-0000-0000-0000-000000000000', '11111111-1111-1111-1111-111111111111', 'authenticated', 'authenticated', 'alice@test.local', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '22222222-2222-2222-2222-222222222222', 'authenticated', 'authenticated', 'bob@test.local',   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '33333333-3333-3333-3333-333333333333', 'authenticated', 'authenticated', 'carol@test.local', now(), now(), now(), '{}', '{}');

INSERT INTO public.users (user_id, email, name, display_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice@real.example', 'Alice A', 'alice'),
  ('22222222-2222-2222-2222-222222222222', 'bob@real.example',   'Bob B',   'bob'),
  ('33333333-3333-3333-3333-333333333333', 'carol@real.example', 'Carol C', 'carol');

INSERT INTO public.households (household_id, household_name) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'House Alpha'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'House Beta');

INSERT INTO public.household_members (user_id, household_id, role, nickname) VALUES
  ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin',  'Alice'),
  ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'member', 'Bob'),
  ('33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'admin',  'Carol');

-- ===== impersonate Bob (regular member of Alpha) =====
SET LOCAL "request.jwt.claims" = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';
SET LOCAL ROLE authenticated;

SELECT results_eq(
  $$ SELECT user_id FROM public.users ORDER BY user_id $$,
  $$ VALUES ('22222222-2222-2222-2222-222222222222'::uuid) $$,
  'member sees only their own users row'
);

SELECT is_empty(
  $$ SELECT user_id FROM public.users WHERE user_id = '11111111-1111-1111-1111-111111111111' $$,
  'member cannot read fellow household member PII'
);

-- ===== impersonate Alice (admin of Alpha) =====
RESET ROLE;
SET LOCAL "request.jwt.claims" = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';
SET LOCAL ROLE authenticated;

SELECT is_empty(
  $$ SELECT user_id FROM public.users WHERE user_id = '22222222-2222-2222-2222-222222222222' $$,
  'admin cannot read members PII in their own household'
);

SELECT is_empty(
  $$ SELECT user_id FROM public.users WHERE user_id = '33333333-3333-3333-3333-333333333333' $$,
  'admin cannot read users in other households'
);

SELECT * FROM finish();
ROLLBACK;
