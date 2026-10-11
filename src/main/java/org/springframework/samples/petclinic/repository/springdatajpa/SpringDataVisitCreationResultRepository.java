/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.repository.springdatajpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.Repository;
import org.springframework.samples.petclinic.model.*;

@Profile("spring-data-jpa")
public interface SpringDataVisitCreationResultRepository extends Repository<VisitCreationResult, VisitCreationScope>,
        VisitCreationResultRepositoryCustom, org.springframework.samples.petclinic.repository.VisitCreationResultRepository { }
