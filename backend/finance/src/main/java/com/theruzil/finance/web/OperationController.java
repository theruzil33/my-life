package com.theruzil.finance.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.finance.operation.OperationService;
import com.theruzil.finance.operation.dto.OperationRequest;
import com.theruzil.finance.operation.dto.OperationResponse;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    @GetMapping
    public List<OperationResponse> list(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (date != null) {
            return operationService.listByDate(userId, date);
        }
        return operationService.listByRange(userId, from, to);
    }

    @PutMapping
    public OperationResponse save(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody OperationRequest request) {
        return operationService.save(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId, @PathVariable Long id) {
        operationService.delete(userId, id);
    }
}
