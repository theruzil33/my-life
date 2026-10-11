package com.theruzil.finance.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.Category;
import com.theruzil.finance.category.CategoryNotFoundException;
import com.theruzil.finance.category.CategoryRepository;
import com.theruzil.finance.operation.dto.OperationRequest;
import com.theruzil.finance.operation.dto.OperationResponse;

@Service
public class OperationService {

    private static final int MAX_SCALE = 2;

    private final OperationRepository operationRepository;

    private final CategoryRepository categoryRepository;

    public OperationService(OperationRepository operationRepository, CategoryRepository categoryRepository) {
        this.operationRepository = operationRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public OperationResponse save(Long userId, OperationRequest request) {
        validate(request);
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
        Instant now = Instant.now();
        Operation operation = operationRepository
                .findByCategoryIdAndOperationDate(category.getId(), request.date())
                .orElseGet(() -> new Operation(userId, category.getId(), request.date(), request.amount(), now));
        operation.updateAmount(request.amount(), now);
        return toResponse(operationRepository.save(operation));
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> listByDate(Long userId, LocalDate date) {
        if (date == null) {
            throw new InvalidOperationDataException("Operation date is required");
        }
        return operationRepository.findByUserIdAndOperationDateOrderById(userId, date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> listByRange(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidOperationDataException("Operation range bounds are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidOperationDataException("Operation range start must not be after end");
        }
        return operationRepository.findByUserIdAndOperationDateBetweenOrderById(userId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long operationId) {
        Operation operation = operationRepository.findByIdAndUserId(operationId, userId)
                .orElseThrow(() -> new OperationNotFoundException("Operation not found"));
        operationRepository.delete(operation);
    }

    private void validate(OperationRequest request) {
        if (request == null) {
            throw new InvalidOperationDataException("Operation data is required");
        }
        if (request.categoryId() == null) {
            throw new InvalidOperationDataException("Operation category is required");
        }
        if (request.date() == null) {
            throw new InvalidOperationDataException("Operation date is required");
        }
        if (request.amount() == null) {
            throw new InvalidOperationDataException("Operation amount is required");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationDataException("Operation amount must be greater than zero");
        }
        if (request.amount().scale() > MAX_SCALE) {
            throw new InvalidOperationDataException("Operation amount must have at most two decimal places");
        }
    }

    private OperationResponse toResponse(Operation operation) {
        return new OperationResponse(operation.getId(), operation.getCategoryId(), operation.getOperationDate(),
                operation.getAmount());
    }
}
