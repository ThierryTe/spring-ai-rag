INSERT INTO departments (code, label, sensitive) VALUES
    ('RH', 'Ressources humaines', false),
    ('IT', 'Informatique', false),
    ('GENERAL', 'Procedures generales', false),
    ('FINANCE', 'Finance', true),
    ('OPERATIONS', 'Operations sensibles', true);

INSERT INTO roles (code, label) VALUES
    ('EMPLOYEE', 'Employe'),
    ('MANAGER', 'Manager');

INSERT INTO role_permissions (role_id, department_id)
SELECT r.id, d.id FROM roles r, departments d
WHERE r.code = 'EMPLOYEE' AND d.code IN ('RH', 'IT', 'GENERAL');

INSERT INTO role_permissions (role_id, department_id)
SELECT r.id, d.id FROM roles r, departments d
WHERE r.code = 'MANAGER';
