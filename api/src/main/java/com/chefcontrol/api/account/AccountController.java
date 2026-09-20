package com.chefcontrol.api.account;

import com.chefcontrol.api.account.dto.AccountResponse;
import com.chefcontrol.application.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ACCOUNT_VIEW')")
    public ResponseEntity<AccountResponse> getAccount() {
        return ResponseEntity.ok(AccountResponse.from(accountService.getCurrentAccount()));
    }

    /**
     * Cierra la cuenta y borra todo. Sin @PreAuthorize a propósito: no es un permiso delegable,
     * el servicio exige que seas el dueño de la cuenta.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        accountService.deleteCurrentAccount();
        return ResponseEntity.noContent().build();
    }
}
