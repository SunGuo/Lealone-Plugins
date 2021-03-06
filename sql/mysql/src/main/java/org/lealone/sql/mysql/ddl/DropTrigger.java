/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.sql.mysql.ddl;

import org.lealone.api.ErrorCode;
import org.lealone.common.exceptions.DbException;
import org.lealone.db.Database;
import org.lealone.db.ServerSession;
import org.lealone.db.auth.Right;
import org.lealone.db.schema.Schema;
import org.lealone.db.schema.TriggerObject;
import org.lealone.db.table.Table;
import org.lealone.sql.SQLStatement;

/**
 * This class represents the statement
 * DROP TRIGGER
 */
public class DropTrigger extends SchemaStatement {

    private String triggerName;
    private boolean ifExists;

    public DropTrigger(ServerSession session, Schema schema) {
        super(session, schema);
    }

    public void setIfExists(boolean b) {
        ifExists = b;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    @Override
    public int update() {
        session.commit(true);
        Database db = session.getDatabase();
        TriggerObject trigger = getSchema().findTrigger(triggerName);
        if (trigger == null) {
            if (!ifExists) {
                throw DbException.get(ErrorCode.TRIGGER_NOT_FOUND_1, triggerName);
            }
        } else {
            Table table = trigger.getTable();
            session.getUser().checkRight(table, Right.ALL);
            db.removeSchemaObject(session, trigger);
        }
        return 0;
    }

    @Override
    public int getType() {
        return SQLStatement.DROP_TRIGGER;
    }

}
