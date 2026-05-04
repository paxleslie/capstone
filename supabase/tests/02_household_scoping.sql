-- RLS test: household scoping
-- Verifies a user can only SELECT rows in posts / messages / household_members
-- for households they belong to.
--
-- NOTE: against the current schema, the negative assertions on posts / messages /
-- household_members are EXPECTED TO FAIL — those tables' SELECT policies are
-- USING (true), so any authenticated user reads everything. This test exists
-- to surface that gap.

CREATE EXTENSION IF NOT EXISTS pgtap;

BEGIN;
SELECT plan(6);

-- ===== seed =====
INSERT INTO auth.users (instance_id, id, aud, role, email, email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
VALUES
  ('00000000-0000-0000-0000-000000000000', '11111111-1111-1111-1111-111111111111', 'authenticated', 'authenticated', 'alice@test.local', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '22222222-2222-2222-2222-222222222222', 'authenticated', 'authenticated', 'bob@test.local',   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '33333333-3333-3333-3333-333333333333', 'authenticated', 'authenticated', 'carol@test.local', now(), now(), now(), '{}', '{}');

INSERT INTO public.users (user_id, email, name, display_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice@real.example', 'Alice', 'alice'),
  ('22222222-2222-2222-2222-222222222222', 'bob@real.example',   'Bob',   'bob'),
  ('33333333-3333-3333-3333-333333333333', 'carol@real.example', 'Carol', 'carol');

INSERT INTO public.households (household_id, household_name) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'House Alpha'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'House Beta');

INSERT INTO public.household_members (user_id, household_id, role, nickname) VALUES
  ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin',  'Alice'),
  ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'member', 'Bob'),
  ('33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'admin',  'Carol');

INSERT INTO public.posts (post_id, household_id, author_id, title, body) VALUES
  ('11111111-aaaa-aaaa-aaaa-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'Alpha post', 'a body'),
  ('22222222-bbbb-bbbb-bbbb-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333', 'Beta post',  'b body');

INSERT INTO public.messages (message_id, household_id, author_id, content) VALUES
  ('11111111-cccc-cccc-cccc-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'Alpha message'),
  ('22222222-dddd-dddd-dddd-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333', 'Beta message');

-- ===== impersonate Bob (member of Alpha) =====
SET LOCAL "request.jwt.claims" = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';
SET LOCAL ROLE authenticated;

-- posts
SELECT isnt_empty(
  $$ SELECT post_id FROM public.posts WHERE household_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' $$,
  'member sees posts in their own household'
);
SELECT is_empty(
  $$ SELECT post_id FROM public.posts WHERE household_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' $$,
  'member does NOT see posts in another household'
);

-- messages
SELECT isnt_empty(
  $$ SELECT message_id FROM public.messages WHERE household_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' $$,
  'member sees messages in their own household'
);
SELECT is_empty(
  $$ SELECT message_id FROM public.messages WHERE household_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' $$,
  'member does NOT see messages in another household'
);

-- household_members
SELECT isnt_empty(
  $$ SELECT member_id FROM public.household_members WHERE household_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' $$,
  'member sees rows in their own household_members'
);
SELECT is_empty(
  $$ SELECT member_id FROM public.household_members WHERE household_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' $$,
  'member does NOT see rows in another household_members'
);

SELECT * FROM finish();
ROLLBACK;
