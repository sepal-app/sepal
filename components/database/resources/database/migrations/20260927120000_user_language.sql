-- The interface language a user has chosen. Null means follow the browser's
-- Accept-Language, which is what every user starts with.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

ALTER TABLE "user" ADD COLUMN language text;
