-- Modification notice: This file was changed for this repository's independent
-- GitHub Spec Kit experiment on Spring PetClinic REST.
DROP TABLE visit_creation_results IF EXISTS;
DROP TABLE vet_specialties IF EXISTS;
DROP TABLE vets IF EXISTS;
DROP TABLE specialties IF EXISTS;
DROP TABLE visits IF EXISTS;
DROP TABLE pets IF EXISTS;
DROP TABLE types IF EXISTS;
DROP TABLE owners IF EXISTS;
DROP TABLE roles IF EXISTS;
DROP TABLE users IF EXISTS;


CREATE TABLE vets (
  id         INTEGER IDENTITY PRIMARY KEY,
  first_name VARCHAR(30),
  last_name  VARCHAR(30)
);
CREATE INDEX vets_last_name ON vets (last_name);

CREATE TABLE specialties (
  id   INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80)
);
CREATE INDEX specialties_name ON specialties (name);

CREATE TABLE vet_specialties (
  vet_id       INTEGER NOT NULL,
  specialty_id INTEGER NOT NULL
);
ALTER TABLE vet_specialties ADD CONSTRAINT fk_vet_specialties_vets FOREIGN KEY (vet_id) REFERENCES vets (id);
ALTER TABLE vet_specialties ADD CONSTRAINT fk_vet_specialties_specialties FOREIGN KEY (specialty_id) REFERENCES specialties (id);

CREATE TABLE types (
  id   INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80)
);
CREATE INDEX types_name ON types (name);

CREATE TABLE owners (
  id         INTEGER IDENTITY PRIMARY KEY,
  first_name VARCHAR(30),
  last_name  VARCHAR_IGNORECASE(30),
  address    VARCHAR(255),
  city       VARCHAR(80),
  telephone  VARCHAR(20)
);
CREATE INDEX owners_last_name ON owners (last_name);

CREATE TABLE pets (
  id         INTEGER IDENTITY PRIMARY KEY,
  name       VARCHAR(30),
  birth_date DATE,
  type_id    INTEGER NOT NULL,
  owner_id   INTEGER NOT NULL
);
ALTER TABLE pets ADD CONSTRAINT fk_pets_owners FOREIGN KEY (owner_id) REFERENCES owners (id);
ALTER TABLE pets ADD CONSTRAINT fk_pets_types FOREIGN KEY (type_id) REFERENCES types (id);
CREATE INDEX pets_name ON pets (name);

CREATE TABLE visits (
  id          INTEGER IDENTITY PRIMARY KEY,
  pet_id      INTEGER NOT NULL,
  visit_date  DATE,
  description VARCHAR(255)
);
ALTER TABLE visits ADD CONSTRAINT fk_visits_pets FOREIGN KEY (pet_id) REFERENCES pets (id);
CREATE INDEX visits_pet_id ON visits (pet_id);

CREATE  TABLE users (
  username    VARCHAR(20) NOT NULL ,
  password    VARCHAR(60) NOT NULL ,
  enabled     BOOLEAN DEFAULT TRUE NOT NULL ,
  PRIMARY KEY (username)
);

CREATE TABLE roles (
  id              INTEGER IDENTITY PRIMARY KEY,
  username        VARCHAR(20) NOT NULL,
  role            VARCHAR(20) NOT NULL
);
ALTER TABLE roles ADD CONSTRAINT fk_username FOREIGN KEY (username) REFERENCES users (username);
CREATE INDEX fk_username_idx ON roles (username);


-- Retained visit creation successes; no FK to mutable clinic entities.
CREATE TABLE IF NOT EXISTS visit_creation_results (
  operation VARCHAR(32) NOT NULL,
  owner_id INTEGER NOT NULL,
  pet_id INTEGER NOT NULL,
  key_hex VARCHAR(256) NOT NULL,
  request_date DATE,
  request_description VARCHAR(255) NOT NULL,
  response_visit_id INTEGER NOT NULL,
  response_pet_id INTEGER NOT NULL,
  response_date DATE,
  response_description VARCHAR(255) NOT NULL,
  response_status INTEGER NOT NULL CHECK (response_status = 201),
  response_location VARCHAR(255) NOT NULL,
  completed_at_ms BIGINT,
  expires_at_ms BIGINT,
  PRIMARY KEY (operation, owner_id, pet_id, key_hex),
  CONSTRAINT visit_result_deadline_pair CHECK (
    (completed_at_ms IS NULL AND expires_at_ms IS NULL) OR
    (completed_at_ms IS NOT NULL AND expires_at_ms IS NOT NULL AND expires_at_ms = completed_at_ms + 86400000)
  )
);
CREATE INDEX IF NOT EXISTS visit_creation_results_expiry ON visit_creation_results (expires_at_ms);
