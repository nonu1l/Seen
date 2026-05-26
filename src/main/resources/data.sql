INSERT OR IGNORE INTO subject_type (code, label) VALUES (2, '动画');
INSERT OR IGNORE INTO subject_type (code, label) VALUES (6, '真人');

INSERT OR IGNORE INTO record_status (code, label, sort_order) VALUES ('wish', '想看', 1);
INSERT OR IGNORE INTO record_status (code, label, sort_order) VALUES ('doing', '在看', 2);
INSERT OR IGNORE INTO record_status (code, label, sort_order) VALUES ('collect', '看过', 3);
INSERT OR IGNORE INTO record_status (code, label, sort_order) VALUES ('on_hold', '搁置', 4);
INSERT OR IGNORE INTO record_status (code, label, sort_order) VALUES ('dropped', '抛弃', 5);
