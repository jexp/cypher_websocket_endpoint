package org.neo4j.remoting.transaction;

/**
 * @author mh
 * @since 20.01.13
 */

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.GraphDatabaseAPI;

import javax.transaction.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionRegistry {
    private final static AtomicLong txIds = new AtomicLong(0);

    private TransactionManager tm;
    private GraphDatabaseService db;


    private long currentTxId = -1l;

    private Map<Long, Transaction> txIdToTxMap = new ConcurrentHashMap<Long, Transaction>();

    public TransactionRegistry(GraphDatabaseService neo4j) {
        this.db = neo4j;
        this.tm = ((GraphDatabaseAPI) neo4j).getDependencyResolver().resolveDependency(TransactionManager.class);
    }

    public void selectCurrentTransaction(long txId)
            throws InvalidTransactionException, IllegalStateException,
            SystemException
    {
        if(currentTxId != txId)
        {
            suspendCurrentTransaction();
            Transaction tx = txIdToTxMap.get(txId);
            if (tx == null) {
                throw new InvalidTransactionException("No transaction with id "
                        + txId + " found.");
            }
            tm.resume(tx);
            currentTxId = txId;
        }
    }

    public void suspendCurrentTransaction()
    {
        if(currentTxId != -1l)
        {
            try
            {
                tm.suspend();
            } catch(SystemException e) {
                throw new RuntimeException(e);
            } finally
            {
                currentTxId = -1l;
            }
        }
    }

    public long createTransaction()
    {
        suspendCurrentTransaction();

        org.neo4j.graphdb.Transaction neo4jTx = db.beginTx();
        try {
            Transaction tx = tm.suspend();
            final long id = txIds.incrementAndGet();
            txIdToTxMap.put(id, tx);
            return id;
        } catch (Exception e) {
            neo4jTx.finish();
            throw new RuntimeException(e);
        }
    }

    public void commitCurrentTransaction() throws IllegalStateException,
            SecurityException, HeuristicMixedException,
            HeuristicRollbackException, RollbackException, SystemException,
            InvalidTransactionException
    {
        if(currentTxId != -1l)
        {
            try {
                tm.commit();
            } finally {
                txIdToTxMap.remove(currentTxId);
                currentTxId = -1l;
            }
        } else {
            throw new InvalidTransactionException("Can't commit, no transaction selected.");
        }
    }

    public void rollbackCurrentTransaction() throws IllegalStateException,
            SecurityException, HeuristicMixedException,
            HeuristicRollbackException, RollbackException, SystemException,
            InvalidTransactionException
    {
        if(currentTxId != -1l)
        {
            try {
                tm.rollback();
            } finally {
                txIdToTxMap.remove(currentTxId);
                currentTxId = -1l;
            }
        } else {
            throw new InvalidTransactionException("Can't roll back, no transaction selected.");
        }

    }

    public void setTransactionTimeout(int sec) throws SystemException {
        tm.setTransactionTimeout(sec);
    }
}
