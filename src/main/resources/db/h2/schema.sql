-- Modification notice: This file was changed for this repository's independent
-- GitHub Spec Kit experiment on Spring PetClinic REST.
CREATE TABLE IF NOT EXISTS vets (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  first_name VARCHAR(30) NOT NULL,
  last_name VARCHAR(30) NOT NULL
);

CREATE INDEX idx_vets_last_name ON vets(last_name);

CREATE TABLE IF NOT EXISTS specialties (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(80) NOT NULL
);

CREATE INDEX idx_specialties_name ON specialties(name);

CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INTEGER NOT NULL,
  specialty_id INTEGER NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id) ON DELETE CASCADE,
  FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE CASCADE,
  UNIQUE (vet_id, specialty_id)
);

CREATE TABLE IF NOT EXISTS types (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(80) NOT NULL
);

CREATE INDEX idx_types_name ON types(name);

CREATE TABLE IF NOT EXISTS owners (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  first_name VARCHAR(30) NOT NULL,
  last_name VARCHAR(30) NOT NULL,
  address VARCHAR(255) NOT NULL,
  city VARCHAR(80) NOT NULL,
  telephone VARCHAR(20) NOT NULL
);

CREATE INDEX idx_owners_last_name ON owners(last_name);

CREATE TABLE IF NOT EXISTS pets (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(30) NOT NULL,
  birth_date DATE NOT NULL,
  type_id INTEGER NOT NULL,
  owner_id INTEGER NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES owners(id) ON DELETE CASCADE,
  FOREIGN KEY (type_id) REFERENCES types(id) ON DELETE CASCADE
);

CREATE INDEX idx_pets_name ON pets(name);

CREATE TABLE IF NOT EXISTS visits (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pet_id INTEGER NOT NULL,
  visit_date DATE NOT NULL,
  description VARCHAR(255) NOT NULL,
  FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS users (
  username VARCHAR(20) NOT NULL PRIMARY KEY,
  password VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS roles (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username VARCHAR(20) NOT NULL,
  role VARCHAR(20) NOT NULL,
  UNIQUE (role, username),
  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);
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
