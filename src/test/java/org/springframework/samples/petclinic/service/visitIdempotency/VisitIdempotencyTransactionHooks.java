package org.springframework.samples.petclinic.service.visitIdempotency;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
public class VisitIdempotencyTransactionHooks {
    public final ThreadLocal<Boolean> forbidResultAccess = new ThreadLocal<>();
    public volatile Runnable beforeInsert = () -> { };
    public volatile Runnable afterInsert = () -> { };
    public volatile Runnable beforeFlush = () -> { };
    public volatile Runnable beforeCommit = () -> { };
    public volatile Runnable afterCommit = () -> { };
    public volatile Runnable beforeFinalize = () -> { };
    public volatile Runnable afterFinalize = () -> { };
    public void reset() {
        forbidResultAccess.remove();
        beforeInsert=afterInsert=beforeFlush=beforeCommit=afterCommit=beforeFinalize=afterFinalize=() -> { };
    }
    @Around("execution(* org.springframework.samples.petclinic.repository.VisitCreationResultRepository.*(..)) || execution(* org.springframework.samples.petclinic.repository.jpa.VisitCreationResultJpaOperations.*(..))")
    public Object guardResultAccess(ProceedingJoinPoint call) throws Throwable {
        if (Boolean.TRUE.equals(forbidResultAccess.get())) {
            throw new AssertionError("Active duplicate accessed retained-result storage");
        }
        return call.proceed();
    }
    @Around("execution(* org.springframework.samples.petclinic.repository.VisitCreationResultRepository.insert(..)) || execution(* org.springframework.samples.petclinic.repository.jpa.VisitCreationResultJpaOperations.insert(..))")
    public Object insert(ProceedingJoinPoint call) throws Throwable {
        beforeInsert.run();
        Object result=call.proceed();
        afterInsert.run();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { beforeCommit.run(); }
                @Override public void afterCommit() { afterCommit.run(); }
            });
        return result;
    }
    @Around("execution(* org.springframework.samples.petclinic.repository.VisitCreationResultRepository.flush(..)) || execution(* org.springframework.samples.petclinic.repository.jpa.VisitCreationResultJpaOperations.flush(..))")
    public Object flush(ProceedingJoinPoint call) throws Throwable { beforeFlush.run(); return call.proceed(); }
    @Around("execution(* org.springframework.samples.petclinic.repository.VisitCreationResultRepository.finalizeDeadline(..)) || execution(* org.springframework.samples.petclinic.repository.jpa.VisitCreationResultJpaOperations.finalizeDeadline(..))")
    public Object finalizeDeadline(ProceedingJoinPoint call) throws Throwable {
        beforeFinalize.run();
        Object result=call.proceed();
        afterFinalize.run();
        return result;
    }
}
