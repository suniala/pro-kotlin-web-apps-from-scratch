DELETE
FROM suburb;
DELETE
FROM town;

INSERT INTO town (name)
VALUES ('Tampere'),
       ('Helsinki');

INSERT INTO suburb (town_id, name)
SELECT t.id, s.name
FROM (VALUES ('Tampere', 'Tohloppi'),
             ('Tampere', 'Tesoma'),
             ('Tampere', 'Tammela'),
             ('Helsinki', 'Hermanni'),
             ('Helsinki', 'Hietaniemi'),
             ('Helsinki', 'Haaga')
         ) AS s(town_name, name),
     town t
WHERE t.name = s.town_name;

