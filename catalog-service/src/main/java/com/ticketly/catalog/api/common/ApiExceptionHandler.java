package com.ticketly.catalog.api.common;

import com.ticketly.catalog.domain.common.DomainRuleViolationException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// One advice for the whole API: controllers stay free of error plumbing.
// ProblemDetail is Spring's built-in RFC-7807 body — the response goes out as
// application/problem+json with type/title/status/detail plus our extensions.
@RestControllerAdvice
public class ApiExceptionHandler {

	// Field error as a record: it is a pure value for the wire.
	public record FieldValidationError(String field, String message) {
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail onValidationFailure(MethodArgumentNotValidException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Validation failed");
		List<FieldValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
				.toList();
		// setProperty adds a non-standard extension member to the RFC-7807 body
		// — this is the errors[] list the acceptance criterion asks for.
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ProblemDetail onNotFound(EntityNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Resource not found");
		return problem;
	}

	// 422: the request was well-formed (else 400) and the resource exists (else
	// 404), but a business rule says no. One handler for the whole hierarchy
	// — F-04's state-transition failures will map here without a new method.
	// UNPROCESSABLE_CONTENT is RFC 9110's name for 422; Spring 7 deprecates the
	// older UNPROCESSABLE_ENTITY constant in its favour.
	@ExceptionHandler(DomainRuleViolationException.class)
	public ProblemDetail onDomainRuleViolation(DomainRuleViolationException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage());
		problem.setTitle("Business rule violated");
		return problem;
	}

}
