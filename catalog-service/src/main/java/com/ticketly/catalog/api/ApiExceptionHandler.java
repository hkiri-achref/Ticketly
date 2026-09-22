package com.ticketly.catalog.api;

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

}
