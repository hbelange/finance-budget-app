CREATE TABLE users (
  sub     TEXT           PRIMARY KEY,
  email   TEXT           NOT NULL UNIQUE
);

INSERT INTO users (sub, email) VALUES
('auth0|6a0676341f694498881e439d', 'harrisonbelanger@gmail.com');

ALTER TABLE accounts ADD COLUMN user_sub TEXT REFERENCES users(sub);
ALTER TABLE category_groups ADD COLUMN user_sub TEXT REFERENCES users(sub);

UPDATE accounts SET user_sub = 'auth0|6a0676341f694498881e439d';
UPDATE category_groups SET user_sub = 'auth0|6a0676341f694498881e439d';

ALTER TABLE accounts ALTER COLUMN user_sub SET NOT NULL;
ALTER TABLE category_groups ALTER COLUMN user_sub SET NOT NULL;

