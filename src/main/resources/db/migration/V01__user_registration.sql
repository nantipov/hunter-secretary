CREATE TABLE registered_user (
  id BIGSERIAL PRIMARY KEY,
  email_address VARCHAR(255) NOT NULL,
  token_response TEXT,
  expires_in TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uniq_user_email ON registered_user (email_address);
