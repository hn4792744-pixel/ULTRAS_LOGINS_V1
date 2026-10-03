package me.uc_hussein.ultraslogin.common.db;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;

import java.util.Optional;
import java.util.UUID;

/** Blocking persistence API. Implementations throw {@link DataAccessException} on failure. */
public interface AccountRepository {
    Optional<Account> findByUuid(UUID uuid);

    /** Lookup by lowercase username within one account type. */
    Optional<Account> findByName(String usernameLower, AccountType type);

    /** Lookup by lowercase username, any type (admin tools). */
    Optional<Account> findByNameAny(String usernameLower);

    /** Inserts and assigns the generated id. */
    Account insert(Account account);

    void update(Account account);

    /** Clears the "authenticated" flag of every account (called at proxy start). */
    void resetAuthenticatedFlags();

    long count();

    class DataAccessException extends RuntimeException {
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
