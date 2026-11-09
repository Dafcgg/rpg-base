CREATE DATABASE IF NOT EXISTS rpg_grupal_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE rpg_grupal_db;

-- WARNING: Enabling this line deletes all saved demonstration heroes.
-- DROP TABLE IF EXISTS heroes;

CREATE TABLE IF NOT EXISTS heroes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    tipo VARCHAR(50) NOT NULL,
    vida_maxima INT NOT NULL,
    nivel INT NOT NULL,
    CONSTRAINT chk_heroes_tipo CHECK (tipo IN ('guerrero', 'mago')),
    CONSTRAINT chk_heroes_vida CHECK (vida_maxima > 0),
    CONSTRAINT chk_heroes_nivel CHECK (nivel >= 1)
) ENGINE=InnoDB;

INSERT INTO heroes (nombre, tipo, vida_maxima, nivel)
VALUES
    ('Aria', 'guerrero', 150, 4),
    ('Merlin', 'mago', 110, 3)
-- FIX: Re-running the demo must not overwrite a player's stored health or level.
ON DUPLICATE KEY UPDATE
    id = id;
