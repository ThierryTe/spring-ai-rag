-- Comptes de demonstration, un par role, pour permettre un login JWT reel sans passer par un
-- endpoint d'inscription (qui n'existe pas). Mot de passe identique pour les trois : "Demo1234!"
-- (hash BCrypt genere hors ligne via PasswordEncoder, meme mecanisme qu'AuthService.login).
INSERT INTO users (email, password_hash, full_name, role_id)
SELECT 'employe.demo@aicompliancecopilot.dev',
       '$2a$10$.h9WS6/wHrAh/M4EYeDdw.e0/Z.7y85ZrLjjeNIvjpASmJ6n9nZbi',
       'Employe Demo',
       r.id
FROM roles r WHERE r.code = 'EMPLOYEE';

INSERT INTO users (email, password_hash, full_name, role_id)
SELECT 'manager.demo@aicompliancecopilot.dev',
       '$2a$10$BQ9A1xXjPMUCxgiRHzvpFep9fe37VXKSlnrB/18df/o03qUxqq0VO',
       'Manager Demo',
       r.id
FROM roles r WHERE r.code = 'MANAGER';

INSERT INTO users (email, password_hash, full_name, role_id)
SELECT 'admin.demo@aicompliancecopilot.dev',
       '$2a$10$wvnQqUYngXdz7cJo7Ec/hukZDc//2pt8yi24Yx.sh0wlvM1Ms9g4C',
       'Admin Demo',
       r.id
FROM roles r WHERE r.code = 'ADMIN';
