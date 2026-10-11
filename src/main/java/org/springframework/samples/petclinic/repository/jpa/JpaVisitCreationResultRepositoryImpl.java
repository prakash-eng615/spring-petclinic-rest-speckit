package org.springframework.samples.petclinic.repository.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

@Repository
@Profile("jpa")
public class JpaVisitCreationResultRepositoryImpl extends VisitCreationResultJpaOperations
        implements VisitCreationResultRepository { }
