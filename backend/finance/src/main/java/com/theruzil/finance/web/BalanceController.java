package com.theruzil.finance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.finance.balance.BalanceService;
import com.theruzil.finance.balance.dto.BalanceRequest;
import com.theruzil.finance.balance.dto.BalanceResponse;

@RestController
@RequestMapping("/balance")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public BalanceResponse get(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId) {
        return balanceService.get(userId);
    }

    @PutMapping
    public BalanceResponse update(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody BalanceRequest request) {
        return balanceService.update(userId, request);
    }
}
