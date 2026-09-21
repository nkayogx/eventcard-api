package com.kayogx.eventcard.tenant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs database work that must see ALL companies, in a fresh transaction.
 *
 * Used only in a few places where we do not know (or care about) the company yet:
 * login, signup, accepting an invitation, checking that an email is not used anywhere.
 *
 * Example:
 *   User user = allCompaniesTransaction.run(() -> userRepository.findByEmail(email));
 */
@Component
public class AllCompaniesTransaction {

    private final TransactionTemplate newTransaction;

    public AllCompaniesTransaction(PlatformTransactionManager transactionManager) {
        this.newTransaction = new TransactionTemplate(transactionManager);
        // Always start a NEW transaction (and so a new database session), even if one
        // is already running. Only a new session picks up the "all companies" setting.
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T run(Supplier<T> work) {
        return CurrentTenant.runAsAllCompanies(() -> newTransaction.execute(status -> work.get()));
    }
}
