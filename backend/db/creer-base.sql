-- Création de la base PostgreSQL de Dakar AutoTrack (installation sans Docker).
-- À exécuter une fois en tant que superutilisateur :
--   psql -U postgres -f db/creer-base.sql
-- Les tables et les données de démonstration sont créées par l'application au premier démarrage.

CREATE ROLE autotrack LOGIN PASSWORD 'autotrack';
CREATE DATABASE autotrack OWNER autotrack ENCODING 'UTF8' TEMPLATE template0;
