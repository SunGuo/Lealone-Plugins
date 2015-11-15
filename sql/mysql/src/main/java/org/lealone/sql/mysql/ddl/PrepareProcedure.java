/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.sql.mysql.ddl;

import java.util.ArrayList;

import org.lealone.common.util.New;
import org.lealone.db.Procedure;
import org.lealone.db.ServerSession;
import org.lealone.sql.SQLStatement;
import org.lealone.sql.mysql.StatementBase;
import org.lealone.sql.mysql.expression.Parameter;

/**
 * This class represents the statement
 * PREPARE
 */
public class PrepareProcedure extends DefineStatement {

    private String procedureName;
    private StatementBase prepared;

    public PrepareProcedure(ServerSession session) {
        super(session);
    }

    @Override
    public void checkParameters() {
        // no not check parameters
    }

    @Override
    public int update() {
        Procedure proc = new Procedure(procedureName, prepared);
        prepared.setParameterList(parameters);
        prepared.setPrepareAlways(prepareAlways);
        prepared.prepare();
        session.addProcedure(proc);
        return 0;
    }

    public void setProcedureName(String name) {
        this.procedureName = name;
    }

    public void setPrepared(StatementBase prep) {
        this.prepared = prep;
    }

    @Override
    public ArrayList<Parameter> getParameters() {
        return New.arrayList();
    }

    @Override
    public int getType() {
        return SQLStatement.PREPARE;
    }

}
