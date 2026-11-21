-- Два уровня доступа: MODERATOR и ADMIN. Сохраняем учётные записи и объединяем старые роли.
ALTER TABLE user_roles DROP CONSTRAINT chk_user_roles_role;

INSERT INTO user_roles (user_id, role)
SELECT DISTINCT user_id, 'MODERATOR'
FROM user_roles
WHERE role IN ('VIEWER', 'EDITOR')
ON CONFLICT (user_id, role) DO NOTHING;

UPDATE users SET version = version + 1, updated_at = now()
WHERE id IN (SELECT user_id FROM user_roles WHERE role IN ('VIEWER', 'EDITOR'));

DELETE FROM user_roles WHERE role IN ('VIEWER', 'EDITOR');

ALTER TABLE user_roles ADD CONSTRAINT chk_user_roles_role CHECK (role IN ('MODERATOR', 'ADMIN'));
