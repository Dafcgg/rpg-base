-- ============================================================
-- schema.sql - Unidad 5.2: Arena Segura
-- Base de datos: rpg_arena_db
-- Motor: MySQL 8.x
-- Ejecutar con: mysql -u root -p < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS rpg_arena_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rpg_arena_db;

-- ------------------------------------------------------------
-- Eliminar tablas en orden inverso de dependencias FK
-- ------------------------------------------------------------
DROP TABLE IF EXISTS victorias;
DROP TABLE IF EXISTS monstruos;
DROP TABLE IF EXISTS jugadores;

-- ------------------------------------------------------------
-- Tabla: jugadores
-- Almacena las credenciales (clave hasheada SHA-256) y el rol
-- ------------------------------------------------------------
CREATE TABLE jugadores (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    usuario VARCHAR(50)  NOT NULL UNIQUE,
    clave   VARCHAR(255) NOT NULL COMMENT 'Hash SHA-256 de la contrasena (64 chars hex)',
    rol     VARCHAR(10)  NOT NULL DEFAULT 'jugador'
                         CHECK (rol IN ('admin', 'jugador'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Tabla: monstruos
-- Catalogo de monstruos del bestiario
-- ------------------------------------------------------------
CREATE TABLE monstruos (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL UNIQUE,
    vida    INT          NOT NULL CHECK (vida > 0),
    ataque  INT          NOT NULL CHECK (ataque > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Tabla: victorias
-- Historial de victorias (FK con restriccion ON DELETE RESTRICT)
-- ON DELETE RESTRICT impide eliminar jugadores/monstruos que
-- tengan victorias asociadas, protegiendo la integridad del historial.
-- ------------------------------------------------------------
CREATE TABLE victorias (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    jugador_id  INT      NOT NULL,
    monstruo_id INT      NOT NULL,
    fecha       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_victoria_jugador
        FOREIGN KEY (jugador_id)  REFERENCES jugadores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_victoria_monstruo
        FOREIGN KEY (monstruo_id) REFERENCES monstruos (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Datos de ejemplo
-- Contrasenas hasheadas en SHA-256:
--   admin123  -> a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3
--   jugador1  -> 0a041b9462caa4a31bac3567e0b6e6fd9100787db2ab433d96f6d178cabfce90
--
-- Para generar el hash de una contrasena propia en Linux:
--   echo -n "tu_contrasena" | sha256sum
-- En Java: Jugador.hashSHA256("tu_contrasena")
-- ------------------------------------------------------------
INSERT INTO jugadores (usuario, clave, rol) VALUES
    ('admin',    'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3', 'admin'),
    ('jugador1', '0a041b9462caa4a31bac3567e0b6e6fd9100787db2ab433d96f6d178cabfce90', 'jugador');

INSERT INTO monstruos (nombre, vida, ataque) VALUES
    ('Goblin',        50,  8),
    ('Arana Gigante', 60, 10),
    ('Ogro',         100, 14),
    ('Dragon Joven', 160, 20);

-- Verificar estructura creada
SELECT 'Tablas creadas exitosamente:' AS estado;
SHOW TABLES;
