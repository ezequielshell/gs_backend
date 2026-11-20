-- Solo Vivo - Ajustes do banco exigidos pela API (executar DEPOIS de 01-dump-iot.sql)
--
-- 1) Perfis de acesso (Parte 5 - Seguranca: "perfis com permissoes diferentes").
--    O dump original nao tinha perfil de usuario; aqui criamos admin x produtor.
-- 2) Senha real (hash BCrypt) para a usuaria de demonstracao. O dump trazia
--    o placeholder '$2b$10$SUBSTITUA_POR_UM_HASH_BCRYPT_REAL', que impede o login.
-- 3) Usuario administrador de demonstracao.
--
-- Credenciais de demonstracao (trocar em producao):
--   maria@email.com     / solovivo123  (perfil produtor)
--   admin@solovivo.com  / admin12345   (perfil admin)

BEGIN;

CREATE TYPE perfil_usuario AS ENUM ('admin', 'produtor');

ALTER TABLE usuarios
    ADD COLUMN perfil perfil_usuario NOT NULL DEFAULT 'produtor';

UPDATE usuarios
   SET senha_hash = '$2a$10$0NUg07qi69RoC7cdx2UkPuFDVZ84ZjqoFEA1BQyeuGVaw1Jvp3wke'
 WHERE email = 'maria@email.com';

INSERT INTO usuarios (nome, email, senha_hash, perfil)
VALUES ('Administrador Solo Vivo', 'admin@solovivo.com',
        '$2a$10$akTEK2Et/qrJf1EgPSdSEO5nI/olEIZl9INdM7VyEC7EYCG474pTK', 'admin');

COMMIT;
