-- OdontoAgenda — reinicio de la base local de demo (C-16, design D2).
-- Borra TODO el esquema `public` de la base `turnotarde` (datos e historial de Flyway) para que el
-- próximo arranque con el perfil demo la reconstruya desde cero. Con la aplicación detenida:
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U turnotarde -d turnotarde -f backend/scripts/reiniciar-base-demo.sql
-- psql pide la contraseña por consola; no queda escrita en ningún archivo.
--
-- Guardas: el script se niega a seguir si la conexión no es local (socket o loopback), si la base no
-- se llama exactamente `turnotarde` (nunca `turnotarde_test`) o si el usuario no es `turnotarde`.

\set ON_ERROR_STOP on

SELECT current_database() AS base,
       current_user AS usuario,
       coalesce(host(inet_server_addr()), 'socket local') AS servidor,
       inet_server_port() AS puerto;

DO $$
BEGIN
    IF current_database() <> 'turnotarde' THEN
        RAISE EXCEPTION 'Reinicio cancelado: la base es "%", se esperaba "turnotarde".', current_database();
    END IF;
    IF current_user <> 'turnotarde' THEN
        RAISE EXCEPTION 'Reinicio cancelado: el usuario es "%", se esperaba "turnotarde".', current_user;
    END IF;
    IF inet_server_addr() IS NOT NULL
            AND host(inet_server_addr()) NOT IN ('127.0.0.1', '::1') THEN
        RAISE EXCEPTION 'Reinicio cancelado: el servidor % no es local.', host(inet_server_addr());
    END IF;
END
$$;

-- Los mensajes que imprime psql van sin tildes: la consola de Windows no los muestra bien.
\prompt 'Se borra el esquema public de la base turnotarde. Escribi BORRAR para confirmar: ' confirmacion

SELECT :'confirmacion' = 'BORRAR' AS confirmado \gset
\if :confirmado
    DROP SCHEMA public CASCADE;
    CREATE SCHEMA public;
    \echo 'Listo: esquema public vacio. Arranca la app con el perfil demo y Flyway lo reconstruye.'
\else
    \echo 'Reinicio cancelado: no se borro nada.'
\endif
