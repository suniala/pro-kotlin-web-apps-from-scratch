ALTER TABLE user_t
    ADD COLUMN tos_accepted BOOLEAN
        NOT NULL
        DEFAULT false;
