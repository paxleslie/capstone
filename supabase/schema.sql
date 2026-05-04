


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "public";


ALTER SCHEMA "public" OWNER TO "pg_database_owner";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE OR REPLACE FUNCTION "public"."add_user_to_household_by_email"("p_new_member_email" "text", "p_household_id" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_member_id uuid;
  v_new_user_id uuid;
  v_new_user_nickname text;
BEGIN
  -- determine if caller is Admin of this household
  IF NOT EXISTS (
    SELECT 1 FROM household_members
    WHERE household_id = p_household_id
      AND user_id = auth.uid()
      AND role = 'admin'
  ) THEN
    RAISE EXCEPTION 'Denied. Caller is not an admin of this household.';
  END IF;
  
  -- find the user's id and current nickname through their email
  SELECT
    user_id, 
    display_name
  INTO
    v_new_user_id,
    v_new_user_nickname
  FROM users
  WHERE email = p_new_member_email;

  -- make sure this email has a user associated with it
  IF v_new_user_id IS NULL THEN
    RAISE EXCEPTION 'No user associated with %.', p_new_user_email;
  END IF;

  -- insert into household_member 
  INSERT INTO household_members(household_id, user_id, nickname, role)
  VALUES (p_household_id, v_new_user_id, v_new_user_nickname, 'member')
  ON CONFLICT (household_id, user_id) DO NOTHING
  RETURNING member_id into v_member_id;

  RETURN v_member_id;
END;$$;


ALTER FUNCTION "public"."add_user_to_household_by_email"("p_new_member_email" "text", "p_household_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."complete_chore"("p_post_id" "uuid", "p_member_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
    v_points integer;
    v_household_id uuid;
BEGIN


    -- 1. Get the point value and household_id of the chore
    SELECT point_value, household_id INTO v_points, v_household_id
    FROM public.posts
    WHERE post_id = p_post_id;

  IF NOT is_household_member(v_household_id, (SELECT auth.uid())) THEN
    RAISE EXCEPTION 'Denied. Caller is not a member of this household.';
  END IF;

    -- 2. Update the post status to 'completed'
    UPDATE public.posts
    SET status = 'completed'
    WHERE post_id = p_post_id;

    -- 3. Award the points to the member who completed it
    IF v_points IS NOT NULL AND v_points > 0 THEN
        UPDATE public.household_members
        SET total_points = COALESCE(total_points, 0) + v_points,
            weekly_points = COALESCE(weekly_points, 0) + v_points
        WHERE member_id = p_member_id;
    END IF;
END;$$;


ALTER FUNCTION "public"."complete_chore"("p_post_id" "uuid", "p_member_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."create_household"("p_household_name" "text", "p_user_id" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$DECLARE
  v_household_id uuid;
  v_member_id uuid;
BEGIN
  -- Step 1: Insert household without owner
  INSERT INTO households (household_name)
  VALUES (p_household_name)
  RETURNING household_id INTO v_household_id;

  -- Step 2: Insert user as a member
  INSERT INTO household_members (household_id, user_id, role)
  VALUES (v_household_id, auth.uid(), 'admin')
  RETURNING member_id INTO v_member_id;

  RETURN v_member_id;
END;$$;


ALTER FUNCTION "public"."create_household"("p_household_name" "text", "p_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."delete_household"("householdid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN

  IF NOT EXISTS (
    SELECT 1 FROM household_members
    WHERE household_id = householdID
      AND user_id = auth.uid()
      AND role = 'admin'
  ) THEN
    RAISE EXCEPTION 'Denied. Caller is not an admin of this household.';
  END IF;

  DELETE FROM households
  WHERE (households.household_id = householdID);

END$$;


ALTER FUNCTION "public"."delete_household"("householdid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."delete_message"("messageid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN
  DELETE FROM messages
  WHERE messages.message_id = messageID;
END$$;


ALTER FUNCTION "public"."delete_message"("messageid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."edit_message"("newcontent" "text", "messageid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN

UPDATE messages
SET content = newContent, edited_at = (now())
WHERE message_id = messageID;

END$$;


ALTER FUNCTION "public"."edit_message"("newcontent" "text", "messageid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$begin
  insert into public.users (user_id, email, name, phone, display_name)
  values (
    new.id,
    new.email,
    new.raw_user_meta_data->>'full_name',
    new.raw_user_meta_data->>'phone',
    new.raw_user_meta_data->>'display_name'
  );
  return new;
end;$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    SELECT EXISTS (
      SELECT 1 FROM public.household_members
      WHERE household_id = p_household_id
        AND user_id = p_user_id
        AND role = 'admin'
    );
  $$;


ALTER FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."is_household_member"("p_household_id" "uuid", "p_user_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    AS $$BEGIN
  RETURN EXISTS 
  (
    SELECT 1 FROM household_members WHERE
    household_members.household_id = p_household_id
    AND household_members.user_id = p_user_id
  );
END;$$;


ALTER FUNCTION "public"."is_household_member"("p_household_id" "uuid", "p_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."purchase_shop_item"("p_member_id" "uuid", "p_household_id" "uuid", "p_item_id" "uuid", "p_price" integer) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
    -- Check if user has enough points
    IF (SELECT total_points FROM public.household_members WHERE member_id = p_member_id AND user_id = auth.uid() AND household_id = p_household_id) < p_price THEN
        RAISE EXCEPTION 'Not enough points!';
    END IF;

    -- Deduct points
    UPDATE public.household_members
    SET total_points = total_points - p_price
    WHERE member_id = p_member_id  AND user_id = auth.uid() AND household_id = p_household_id;

    -- Add to member_rewards
    INSERT INTO public.member_rewards (member_id, reward_id)
    VALUES (p_member_id, p_item_id);
END;$$;


ALTER FUNCTION "public"."purchase_shop_item"("p_member_id" "uuid", "p_household_id" "uuid", "p_item_id" "uuid", "p_price" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."remove_household_member"("userid" "uuid", "householdid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM household_members hm
    WHERE hm.household_id = householdID
      AND (
        -- admin can remove anyone in household
        (
          hm.user_id = auth.uid()
          AND hm.role = 'admin'
        )
        OR
        -- regular user can remove only their own member row
        (
          hm.member_id = userID
          AND hm.user_id = auth.uid()
        )
      )
  ) THEN
    RAISE EXCEPTION 'Denied. Caller is not an admin of this household or the member being removed.';
  END IF;

  DELETE FROM household_members
  WHERE member_id = userID
    AND household_id = householdID;
END;$$;


ALTER FUNCTION "public"."remove_household_member"("userid" "uuid", "householdid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rls_auto_enable"() RETURNS "event_trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


ALTER FUNCTION "public"."rls_auto_enable"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."send_message"("p_household_id" "uuid", "p_from_member_id" "uuid", "p_content" "text") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN
  INSERT INTO messages (household_id, author_id, from_member_id, content, sent_at)
  VALUES (p_household_id, auth.uid(), p_from_member_id, p_content, now());
END;$$;


ALTER FUNCTION "public"."send_message"("p_household_id" "uuid", "p_from_member_id" "uuid", "p_content" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."view_messages"("householdid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$BEGIN

SELECT *
FROM messages
WHERE messages.household_id = householdID;

END;$$;


ALTER FUNCTION "public"."view_messages"("householdid" "uuid") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."household_members" (
    "member_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "household_id" "uuid" NOT NULL,
    "role" character varying(20) DEFAULT 'member'::character varying,
    "total_points" integer DEFAULT 0,
    "weekly_points" integer DEFAULT 0,
    "nickname" "text" DEFAULT ''::"text" NOT NULL,
    CONSTRAINT "household_members_nickname_check" CHECK (("length"("nickname") <= 20))
);


ALTER TABLE "public"."household_members" OWNER TO "postgres";


COMMENT ON COLUMN "public"."household_members"."nickname" IS 'Display name for the user in this particular household';



CREATE TABLE IF NOT EXISTS "public"."households" (
    "household_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "household_name" character varying(255) NOT NULL,
    "owner_member_id" "uuid"
);


ALTER TABLE "public"."households" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."member_rewards" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "member_id" "uuid",
    "reward_id" "uuid",
    "note" character varying(255)
);


ALTER TABLE "public"."member_rewards" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."messages" (
    "message_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "author_id" "uuid",
    "from_member_id" "uuid",
    "sent_at" timestamp with time zone DEFAULT "now"(),
    "content" "text" NOT NULL,
    "household_id" "uuid" NOT NULL,
    "edited_at" timestamp with time zone
);


ALTER TABLE "public"."messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."posts" (
    "post_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "author_id" "uuid",
    "member_id" "uuid",
    "type" character varying(20),
    "title" character varying(255),
    "body" "text",
    "point_value" integer,
    "status" character varying(20),
    "created_at" "date" DEFAULT CURRENT_DATE,
    "due_at" "date",
    "household_id" "uuid" NOT NULL,
    "color" "text",
    "sticker" "text"
);


ALTER TABLE "public"."posts" OWNER TO "postgres";


COMMENT ON COLUMN "public"."posts"."household_id" IS 'Household that this post belongs to';



CREATE TABLE IF NOT EXISTS "public"."rewards" (
    "reward_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "household_id" "uuid",
    "cost" integer,
    "title" "text" DEFAULT 'New Reward'::"text",
    "type" "text" DEFAULT 'color'::"text",
    "value" "text" DEFAULT '#90EE90'::"text"
);


ALTER TABLE "public"."rewards" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."users" (
    "user_id" "uuid" NOT NULL,
    "email" character varying(255) NOT NULL,
    "phone" character varying(20),
    "name" character varying(100),
    "display_name" character varying(100)
);


ALTER TABLE "public"."users" OWNER TO "postgres";


ALTER TABLE ONLY "public"."household_members"
    ADD CONSTRAINT "household_members_pkey" PRIMARY KEY ("member_id");



ALTER TABLE ONLY "public"."households"
    ADD CONSTRAINT "households_pkey" PRIMARY KEY ("household_id");



ALTER TABLE ONLY "public"."member_rewards"
    ADD CONSTRAINT "member_rewards_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_pkey" PRIMARY KEY ("message_id");



ALTER TABLE ONLY "public"."posts"
    ADD CONSTRAINT "posts_pkey" PRIMARY KEY ("post_id");



ALTER TABLE ONLY "public"."rewards"
    ADD CONSTRAINT "rewards_pkey" PRIMARY KEY ("reward_id");



ALTER TABLE ONLY "public"."household_members"
    ADD CONSTRAINT "unique_household_member" UNIQUE ("household_id", "user_id");



ALTER TABLE ONLY "public"."member_rewards"
    ADD CONSTRAINT "unique_member_reward" UNIQUE ("member_id", "reward_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("user_id");



ALTER TABLE ONLY "public"."household_members"
    ADD CONSTRAINT "household_members_household_id_fkey" FOREIGN KEY ("household_id") REFERENCES "public"."households"("household_id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."household_members"
    ADD CONSTRAINT "household_members_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("user_id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."households"
    ADD CONSTRAINT "households_owner_member_id_fkey" FOREIGN KEY ("owner_member_id") REFERENCES "public"."household_members"("member_id");



ALTER TABLE ONLY "public"."member_rewards"
    ADD CONSTRAINT "member_rewards_member_id_fkey" FOREIGN KEY ("member_id") REFERENCES "public"."household_members"("member_id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."member_rewards"
    ADD CONSTRAINT "member_rewards_reward_id_fkey" FOREIGN KEY ("reward_id") REFERENCES "public"."rewards"("reward_id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_author_id_fkey" FOREIGN KEY ("author_id") REFERENCES "public"."users"("user_id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_from_member_id_fkey" FOREIGN KEY ("from_member_id") REFERENCES "public"."household_members"("member_id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_household_id_fkey" FOREIGN KEY ("household_id") REFERENCES "public"."households"("household_id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."posts"
    ADD CONSTRAINT "posts_author_id_fkey" FOREIGN KEY ("author_id") REFERENCES "public"."users"("user_id");



ALTER TABLE ONLY "public"."posts"
    ADD CONSTRAINT "posts_household_id_fkey" FOREIGN KEY ("household_id") REFERENCES "public"."households"("household_id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."posts"
    ADD CONSTRAINT "posts_member_id_fkey" FOREIGN KEY ("member_id") REFERENCES "public"."household_members"("member_id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."rewards"
    ADD CONSTRAINT "rewards_household_id_fkey" FOREIGN KEY ("household_id") REFERENCES "public"."households"("household_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



CREATE POLICY "Allow admin to access data for their household" ON "public"."households" FOR SELECT TO "authenticated" USING (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "households"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text")))));



CREATE POLICY "Allow admin to add user as household member" ON "public"."household_members" FOR INSERT TO "authenticated" WITH CHECK ("public"."is_household_admin"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Allow admin to delete household" ON "public"."households" FOR DELETE TO "authenticated" USING (("auth"."uid"() IN ( SELECT "households"."household_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "households"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text")))));



CREATE POLICY "Allow admin to delete household posts" ON "public"."posts" FOR DELETE TO "authenticated" USING (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "posts"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text")))));



CREATE POLICY "Allow admin to remove household members" ON "public"."household_members" FOR DELETE TO "authenticated" USING ("public"."is_household_admin"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Allow admin to update household members" ON "public"."household_members" FOR UPDATE TO "authenticated" USING ("public"."is_household_admin"("household_id", ( SELECT "auth"."uid"() AS "uid"))) WITH CHECK ("public"."is_household_admin"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Allow admin to update posts" ON "public"."posts" FOR UPDATE TO "authenticated" USING (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "posts"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text"))))) WITH CHECK (true);



CREATE POLICY "Allow household members to add message" ON "public"."messages" FOR INSERT TO "authenticated" WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."member_id" = "messages"."from_member_id") AND ("household_members"."user_id" = "auth"."uid"())))));



CREATE POLICY "Allow household owner to update household" ON "public"."households" FOR UPDATE TO "authenticated" USING (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "households"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text"))))) WITH CHECK (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "households"."household_id") AND (("household_members"."role")::"text" = 'admin'::"text")))));



CREATE POLICY "Allow user to read posts in household" ON "public"."posts" FOR SELECT TO "authenticated" USING (("auth"."uid"() IN ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE ("household_members"."household_id" = "posts"."household_id"))));



CREATE POLICY "Allow users to delete own posts" ON "public"."posts" FOR DELETE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "author_id"));



CREATE POLICY "Allow users to delete their own messages" ON "public"."messages" FOR DELETE TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."member_id" = "messages"."from_member_id") AND ("household_members"."user_id" = "auth"."uid"())))));



CREATE POLICY "Allow users to edit their own messages" ON "public"."messages" FOR UPDATE TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."member_id" = "messages"."from_member_id") AND ("household_members"."user_id" = "auth"."uid"()))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."member_id" = "messages"."from_member_id") AND ("household_members"."user_id" = "auth"."uid"())))));



CREATE POLICY "Allow users to remove themselves from household" ON "public"."household_members" FOR DELETE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "user_id"));



CREATE POLICY "Allow users to update own membership" ON "public"."household_members" FOR UPDATE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "user_id")) WITH CHECK ((( SELECT "auth"."uid"() AS "uid") = "user_id"));



CREATE POLICY "Allow users to update own posts" ON "public"."posts" FOR UPDATE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "author_id")) WITH CHECK (true);



CREATE POLICY "Allow users to update their own information" ON "public"."users" FOR UPDATE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "user_id")) WITH CHECK (true);



CREATE POLICY "Allow users to view household members" ON "public"."household_members" FOR SELECT TO "authenticated" USING ("public"."is_household_member"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Allow users to view joined households" ON "public"."households" FOR SELECT TO "authenticated" USING (("auth"."uid"() IN ( SELECT "users"."user_id"
   FROM "public"."users",
    "public"."household_members"
  WHERE (("household_members"."household_id" = "households"."household_id") AND ("users"."user_id" = "household_members"."user_id")))));



CREATE POLICY "Create post, tied to uid" ON "public"."posts" FOR INSERT TO "authenticated" WITH CHECK ("public"."is_household_member"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Enable creation of household for auth users" ON "public"."households" FOR INSERT TO "authenticated" WITH CHECK (true);



CREATE POLICY "Enable read access for household members" ON "public"."messages" FOR SELECT TO "authenticated" USING ("public"."is_household_member"("household_id", ( SELECT "auth"."uid"() AS "uid")));



CREATE POLICY "Enable users to view their own data only" ON "public"."users" FOR SELECT TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "user_id"));



CREATE POLICY "Users can insert their own member rewards" ON "public"."member_rewards" FOR INSERT TO "authenticated" WITH CHECK (("auth"."uid"() = ( SELECT "household_members"."user_id"
   FROM "public"."household_members"
  WHERE ("household_members"."member_id" = "member_rewards"."member_id"))));



CREATE POLICY "Users can view rewards for their households or global ones" ON "public"."rewards" FOR SELECT TO "authenticated" USING ((("household_id" IS NULL) OR (EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."household_id" = "rewards"."household_id") AND ("household_members"."user_id" = "auth"."uid"()))))));



CREATE POLICY "Users can view their own owned rewards" ON "public"."member_rewards" FOR SELECT TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."household_members"
  WHERE (("household_members"."member_id" = "member_rewards"."member_id") AND ("household_members"."user_id" = "auth"."uid"())))));



ALTER TABLE "public"."household_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."households" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."member_rewards" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."posts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."rewards" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT ALL ON FUNCTION "public"."add_user_to_household_by_email"("p_new_member_email" "text", "p_household_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."add_user_to_household_by_email"("p_new_member_email" "text", "p_household_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."add_user_to_household_by_email"("p_new_member_email" "text", "p_household_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."complete_chore"("p_post_id" "uuid", "p_member_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."complete_chore"("p_post_id" "uuid", "p_member_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."complete_chore"("p_post_id" "uuid", "p_member_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."create_household"("p_household_name" "text", "p_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."create_household"("p_household_name" "text", "p_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_household"("p_household_name" "text", "p_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."delete_household"("householdid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_household"("householdid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_household"("householdid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."delete_message"("messageid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_message"("messageid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_message"("messageid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."edit_message"("newcontent" "text", "messageid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."edit_message"("newcontent" "text", "messageid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."edit_message"("newcontent" "text", "messageid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."is_household_admin"("p_household_id" "uuid", "p_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."is_household_member"("p_household_id" "uuid", "p_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."is_household_member"("p_household_id" "uuid", "p_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."is_household_member"("p_household_id" "uuid", "p_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."purchase_shop_item"("p_member_id" "uuid", "p_household_id" "uuid", "p_item_id" "uuid", "p_price" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."purchase_shop_item"("p_member_id" "uuid", "p_household_id" "uuid", "p_item_id" "uuid", "p_price" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."purchase_shop_item"("p_member_id" "uuid", "p_household_id" "uuid", "p_item_id" "uuid", "p_price" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."remove_household_member"("userid" "uuid", "householdid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."remove_household_member"("userid" "uuid", "householdid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."remove_household_member"("userid" "uuid", "householdid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "anon";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "service_role";



GRANT ALL ON FUNCTION "public"."send_message"("p_household_id" "uuid", "p_from_member_id" "uuid", "p_content" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."send_message"("p_household_id" "uuid", "p_from_member_id" "uuid", "p_content" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."send_message"("p_household_id" "uuid", "p_from_member_id" "uuid", "p_content" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."view_messages"("householdid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."view_messages"("householdid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."view_messages"("householdid" "uuid") TO "service_role";



GRANT ALL ON TABLE "public"."household_members" TO "anon";
GRANT ALL ON TABLE "public"."household_members" TO "authenticated";
GRANT ALL ON TABLE "public"."household_members" TO "service_role";



GRANT ALL ON TABLE "public"."households" TO "anon";
GRANT ALL ON TABLE "public"."households" TO "authenticated";
GRANT ALL ON TABLE "public"."households" TO "service_role";



GRANT ALL ON TABLE "public"."member_rewards" TO "anon";
GRANT ALL ON TABLE "public"."member_rewards" TO "authenticated";
GRANT ALL ON TABLE "public"."member_rewards" TO "service_role";



GRANT ALL ON TABLE "public"."messages" TO "anon";
GRANT ALL ON TABLE "public"."messages" TO "authenticated";
GRANT ALL ON TABLE "public"."messages" TO "service_role";



GRANT ALL ON TABLE "public"."posts" TO "anon";
GRANT ALL ON TABLE "public"."posts" TO "authenticated";
GRANT ALL ON TABLE "public"."posts" TO "service_role";



GRANT ALL ON TABLE "public"."rewards" TO "anon";
GRANT ALL ON TABLE "public"."rewards" TO "authenticated";
GRANT ALL ON TABLE "public"."rewards" TO "service_role";



GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";







