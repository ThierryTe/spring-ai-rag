INSERT INTO roles (code, label) VALUES ('ADMIN', 'Administrateur');

INSERT INTO role_permissions (role_id, department_id)
SELECT r.id, d.id FROM roles r, departments d
WHERE r.code = 'ADMIN';
