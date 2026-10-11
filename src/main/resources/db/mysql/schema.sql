CREATE TABLE IF NOT EXISTS vets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS specialties (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INT(4) UNSIGNED NOT NULL,
  specialty_id INT(4) UNSIGNED NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  UNIQUE (vet_id,specialty_id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS types (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS owners (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS pets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(30),
  birth_date DATE,
  type_id INT(4) UNSIGNED NOT NULL,
  owner_id INT(4) UNSIGNED NOT NULL,
  INDEX(name),
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (type_id) REFERENCES types(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS visits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED NOT NULL,
  visit_date DATE,
  description VARCHAR(255),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS users (
  username VARCHAR(20) NOT NULL ,
  password VARCHAR(60) NOT NULL ,
  enabled TINYINT NOT NULL DEFAULT 1 ,
  PRIMARY KEY (username)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
  id int(11) NOT NULL AUTO_INCREMENT,
  username varchar(20) NOT NULL,
  role varchar(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uni_username_role (role,username),
  KEY fk_username_idx (username),
  CONSTRAINT fk_username FOREIGN KEY (username) REFERENCES users (username)
) engine=InnoDB;

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
  INDEX visit_creation_results_expiry (expires_at_ms),
  PRIMARY KEY (operation, owner_id, pet_id, key_hex),
  CONSTRAINT visit_result_deadline_pair CHECK (
    (completed_at_ms IS NULL AND expires_at_ms IS NULL) OR
    (completed_at_ms IS NOT NULL AND expires_at_ms IS NOT NULL AND expires_at_ms = completed_at_ms + 86400000)
  )
);
