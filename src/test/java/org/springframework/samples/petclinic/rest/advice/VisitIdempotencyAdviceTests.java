package org.springframework.samples.petclinic.rest.advice;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.samples.petclinic.rest.api.OwnersApi;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import static org.assertj.core.api.Assertions.*;

class VisitIdempotencyAdviceTests {
    private final ExceptionControllerAdvice advice=new ExceptionControllerAdvice();
    private final MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/owners/1/pets/1/visits");

    @Test void generatedHeaderMethodValidationMapsOnlyHeaderFailuresToNewProblem() throws Exception {
        var method=OwnersApi.class.getMethod("addVisitToOwner",Integer.class,Integer.class,VisitFieldsDto.class,String.class);
        var header=result(method,3);
        var response=advice.handleMethodValidation(new HandlerMethodValidationException(
            MethodValidationResult.create(this,method,List.of(header))),request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getBody().getType().toString()).isEqualTo("urn:petclinic:problem:invalid-idempotency-key");
        // Other generated parameter failures and mixed failures retain the legacy fallback.
        for(var errors:List.of(List.of(result(method,0)),List.of(header,result(method,1)))) {
            var legacy=advice.handleMethodValidation(new HandlerMethodValidationException(
                MethodValidationResult.create(this,method,errors)),request);
            assertThat(legacy.getStatusCode().value()).isEqualTo(500);
            assertThat(legacy.getBody().getTitle()).isEqualTo("HandlerMethodValidationException");
        }
    }
    @Test void unrelatedMethodAndEmptyConstraintFailuresRetainLegacyBehavior() throws Exception {
        var method=OtherOperation.class.getMethod("other",String.class);
        var response=advice.handleMethodValidation(new HandlerMethodValidationException(
            MethodValidationResult.create(this,method,List.of(result(method,0)))),request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        var empty=advice.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(Set.of()),request);
        assertThat(empty.getStatusCode().value()).isEqualTo(500);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var violations=factory.getValidator().forExecutables().validateParameters(new OtherOperation(),method,new Object[]{null});
            assertThat(violations).isNotEmpty();
            var unrelated=advice.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(violations),request);
            assertThat(unrelated.getStatusCode().value()).isEqualTo(500);
        }
    }
    private ParameterValidationResult result(java.lang.reflect.Method method,int index) {
        return new ParameterValidationResult(new MethodParameter(method,index),"invalid",
            List.of(new DefaultMessageSourceResolvable("invalid parameter")),null,null,null,null);
    }
    public static class OtherOperation { public void other(@jakarta.validation.constraints.NotNull String value) { } }
}
