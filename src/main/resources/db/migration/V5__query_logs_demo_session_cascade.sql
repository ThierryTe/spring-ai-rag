-- documents.demo_session_id et chunks.demo_session_id ont deja ON DELETE CASCADE depuis V1.
-- query_logs.demo_session_id ne l'avait pas (NO ACTION) : sans ce correctif, le nettoyage d'une
-- session demo expiree (Phase 8, DemoSessionCleanupJob) echouerait des qu'un query_log lui est
-- rattache. Constat verifie empiriquement (nom de contrainte confirme via pg_constraint sur un
-- conteneur Postgres jetable avec les migrations V1-V4 rejouees).
ALTER TABLE query_logs DROP CONSTRAINT query_logs_demo_session_id_fkey;
ALTER TABLE query_logs ADD CONSTRAINT query_logs_demo_session_id_fkey
    FOREIGN KEY (demo_session_id) REFERENCES demo_sessions(id) ON DELETE CASCADE;
