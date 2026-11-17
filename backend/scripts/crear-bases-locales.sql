-- OdontoAgenda — creación de las bases locales (C-01, design D8).
-- Correr una vez como superusuario (es idempotente: se puede repetir sin romper nada):
--   "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f backend/scripts/crear-bases-locales.sql
-- Pide la contraseña del rol `turnotarde` por consola; no queda escrita en ningún archivo.
-- Esa misma contraseña va después en tu `.env` como DB_PASSWORD (y TEST_DB_PASSWORD si la usás).

\set ON_ERROR_STOP on
\prompt 'Contraseña para el rol turnotarde: ' turnotarde_pw

-- Rol de la aplicación (se crea si no existe; si existe se actualiza la contraseña).
SELECT 'CREATE ROLE turnotarde LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'turnotarde')
\gexec

SELECT format('ALTER ROLE turnotarde WITH LOGIN PASSWORD %L', :'turnotarde_pw')
\gexec

-- Base de desarrollo / demo.
SELECT 'CREATE DATABASE turnotarde OWNER turnotarde ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'turnotarde')
\gexec

-- Base de tests de integración (nunca H2).
SELECT 'CREATE DATABASE turnotarde_test OWNER turnotarde ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'turnotarde_test')
\gexec

\echo 'Listo: rol turnotarde y bases turnotarde / turnotarde_test disponibles.'
