-- ============================================================
-- schema_grupal.sql - Unidad 5.2: Registro de Heroes persistente
-- Base de datos: rpg_grupal_db
-- Motor: MySQL 8.x
-- Ejecutar con: mysql -u root -p < schema_grupal.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS rpg_grupal_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rpg_grupal_db;

DROP TABLE IF EXISTS heroes;

CREATE TABLE heroes (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(100) UNIQUE NOT NULL,
    tipo        VARCHAR(50)  NOT NULL,
    vida_maxima INT          NOT NULL,
    nivel       INT          NOT NULL,
    CONSTRAINT chk_heroes_tipo   CHECK (tipo IN ('guerrero', 'mago')),
    CONSTRAINT chk_heroes_vida   CHECK (vida_maxima > 0),
    CONSTRAINT chk_heroes_nivel  CHECK (nivel >= 1)
);

-- Datos de ejemplo
INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES
    ('Aquiles', 'guerrero', 140, 3),
    ('Merlina', 'mago',      90, 2);
