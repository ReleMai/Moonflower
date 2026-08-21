package io.havenbot.server.api;

import io.havenbot.server.model.AccountRecord;
import io.havenbot.server.service.AccountService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {
    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountView> list() {
        return accountService.list().stream().map(AccountView::from).toList();
    }

    @PostMapping
    public AccountView create(@RequestBody UpsertAccountRequest request) {
        return AccountView.from(accountService.create(request.name(), request.username(), request.secret(), request.characterName()));
    }

    @PutMapping("/{id}")
    public AccountView update(@PathVariable UUID id, @RequestBody UpsertAccountRequest request) {
        return AccountView.from(accountService.update(id, request.name(), request.username(), request.secret(), request.characterName()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        accountService.delete(id);
    }

    public record UpsertAccountRequest(String name, String username, String secret, String characterName) {
    }
}
