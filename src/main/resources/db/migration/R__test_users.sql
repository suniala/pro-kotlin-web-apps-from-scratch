DELETE FROM user_t;

-- Create hashes in kotlin with:
--     println(hex(
--         bcryptHasher.hash(
--             12,
--             "qwer".toByteArray(Charsets.UTF_8)
--         )
--     ))

INSERT INTO user_t (email, name, password_hash, tos_accepted)
VALUES
    -- 1234
    ('uiv@example.com', 'Uiv Xoblaa', x'243261243132245a4c776b5a6c6c336c6147372e4851513376714930756b634535766e4b37624a5932453136784a57654a544b684b55784438587a71', true),
    -- qwer
   ('ert@example.com', 'Ert Akoor', x'243261243132242e432f76684975584c6e4952646b374d57624a316e2e7948595369473678452f4c5474332f4a466543386b792f7443746a504d4c65', false);
