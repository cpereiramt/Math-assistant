package com.claySoftware.MathExpAssistant.repositories;

import com.mongodb.MongoException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class CommunityTransactions {
    private final TransactionTemplate transaction;

    public CommunityTransactions(MongoDatabaseFactory factory) {
        transaction = new TransactionTemplate(new MongoTransactionManager(factory) {
            @Override
            protected void doCommit(MongoTransactionObject transactionObject) throws Exception {
                for (int attempt = 1; ; attempt++) {
                    try {
                        super.doCommit(transactionObject);
                        return;
                    } catch (MongoException ex) {
                        if (attempt == 3 || !ex.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                            throw ex;
                        }
                    }
                }
            }
        });
    }

    public <T> T write(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> action.get());
            } catch (RuntimeException ex) {
                if (ex instanceof DuplicateKeyException && attempt < 3) {
                    continue;
                }
                if (!isTransient(ex)) {
                    throw ex;
                }
                if (attempt == 3) {
                    throw new OptimisticLockingFailureException("community data changed concurrently", ex);
                }
            }
        }
    }

    private boolean isTransient(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }
}
