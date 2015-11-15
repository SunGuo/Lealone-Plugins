/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.sql.mysql.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.lealone.common.exceptions.DbException;
import org.lealone.common.util.New;
import org.lealone.db.Constants;
import org.lealone.db.Database;
import org.lealone.db.util.IntIntHashMap;
import org.lealone.db.util.ValueHashMap;
import org.lealone.db.value.CompareMode;
import org.lealone.db.value.DataType;
import org.lealone.db.value.Value;
import org.lealone.db.value.ValueArray;
import org.lealone.db.value.ValueBoolean;
import org.lealone.db.value.ValueDouble;
import org.lealone.db.value.ValueInt;
import org.lealone.db.value.ValueLong;
import org.lealone.db.value.ValueNull;

/**
 * Data stored while calculating an aggregate.
 */
class AggregateDataOld {
    private final int aggregateType;
    private final int dataType;
    private long count;
    private IntIntHashMap distinctHashes;
    private ValueHashMap<AggregateDataOld> distinctValues;
    private Value value;
    private double m2, mean;
    private ArrayList<Value> list;

    AggregateDataOld(int aggregateType, int dataType) {
        this.aggregateType = aggregateType;
        this.dataType = dataType;
    }

    /**
     * Add a value to this aggregate.
     *
     * @param database the database
     * @param distinct if the calculation should be distinct
     * @param v the value
     */
    void add(Database database, boolean distinct, Value v) {
        if (aggregateType == Aggregate.SELECTIVITY) {
            count++;
            if (distinctHashes == null) {
                distinctHashes = new IntIntHashMap();
            }
            int size = distinctHashes.size();
            if (size > Constants.SELECTIVITY_DISTINCT_COUNT) {
                distinctHashes = new IntIntHashMap();
                m2 += size;
            }
            int hash = v.hashCode();
            // the value -1 is not supported
            distinctHashes.put(hash, 1);
            return;
        } else if (aggregateType == Aggregate.COUNT_ALL) {
            count++;
            return;
        } else if (aggregateType == Aggregate.HISTOGRAM) {
            if (distinctValues == null) {
                distinctValues = ValueHashMap.newInstance();
            }
            AggregateDataOld a = distinctValues.get(v);
            if (a == null) {
                if (distinctValues.size() < Constants.SELECTIVITY_DISTINCT_COUNT) {
                    a = new AggregateDataOld(Aggregate.HISTOGRAM, dataType);
                    distinctValues.put(v, a);
                }
            }
            if (a != null) {
                a.count++;
            }
            return;
        }
        if (v == ValueNull.INSTANCE) {
            return;
        }
        count++;
        if (distinct) {
            if (distinctValues == null) {
                distinctValues = ValueHashMap.newInstance();
            }
            distinctValues.put(v, this);
            return;
        }
        switch (aggregateType) {
        case Aggregate.COUNT:
        case Aggregate.HISTOGRAM:
            return;
        case Aggregate.SUM:
            if (value == null) {
                value = v.convertTo(dataType);
            } else {
                v = v.convertTo(value.getType());
                value = value.add(v);
            }
            break;
        case Aggregate.AVG:
            if (value == null) {
                value = v.convertTo(DataType.getAddProofType(dataType));
            } else {
                v = v.convertTo(value.getType());
                value = value.add(v);
            }
            break;
        case Aggregate.MIN:
            if (value == null || database.compare(v, value) < 0) {
                value = v;
            }
            break;
        case Aggregate.MAX:
            if (value == null || database.compare(v, value) > 0) {
                value = v;
            }
            break;
        case Aggregate.GROUP_CONCAT: {
            if (list == null) {
                list = New.arrayList();
            }
            list.add(v);
            break;
        }
        case Aggregate.STDDEV_POP:
        case Aggregate.STDDEV_SAMP:
        case Aggregate.VAR_POP:
        case Aggregate.VAR_SAMP: {
            // Using Welford's method, see also
            // http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
            // http://www.johndcook.com/standard_deviation.html
            double x = v.getDouble();
            if (count == 1) {
                mean = x;
                m2 = 0;
            } else {
                double delta = x - mean;
                mean += delta / count;
                m2 += delta * (x - mean);
            }
            break;
        }
        case Aggregate.BOOL_AND:
            v = v.convertTo(Value.BOOLEAN);
            if (value == null) {
                value = v;
            } else {
                value = ValueBoolean.get(value.getBoolean().booleanValue() && v.getBoolean().booleanValue());
            }
            break;
        case Aggregate.BOOL_OR:
            v = v.convertTo(Value.BOOLEAN);
            if (value == null) {
                value = v;
            } else {
                value = ValueBoolean.get(value.getBoolean().booleanValue() || v.getBoolean().booleanValue());
            }
            break;
        default:
            DbException.throwInternalError("type=" + aggregateType);
        }
    }

    void merge(Database database, boolean distinct, Value v) {
        if (aggregateType == Aggregate.COUNT || aggregateType == Aggregate.COUNT_ALL) {
            count += v.getLong();
            return;
        } else if (aggregateType == Aggregate.HISTOGRAM) {
            if (distinctValues == null) {
                distinctValues = ValueHashMap.newInstance();
            }
            AggregateDataOld a = distinctValues.get(v);
            if (a == null) {
                if (distinctValues.size() < Constants.SELECTIVITY_DISTINCT_COUNT) {
                    a = new AggregateDataOld(Aggregate.HISTOGRAM, dataType);
                    distinctValues.put(v, a);
                }
            }
            if (a != null) {
                a.count++;
            }
            return;
        }
        if (v == ValueNull.INSTANCE) {
            return;
        }
        count++;
        if (distinct) {
            if (distinctValues == null) {
                distinctValues = ValueHashMap.newInstance();
            }
            distinctValues.put(v, this);
            return;
        }
        switch (aggregateType) {
        case Aggregate.COUNT:
        case Aggregate.HISTOGRAM:
            return;
        case Aggregate.SUM:
        case Aggregate.SELECTIVITY:
            if (value == null) {
                value = v.convertTo(dataType);
            } else {
                v = v.convertTo(value.getType());
                value = value.add(v);
            }
            break;
        case Aggregate.AVG:
            if (value == null) {
                value = v.convertTo(DataType.getAddProofType(dataType));
            } else {
                // AVG聚合函数merge的次数不会超过1
                DbException.throwInternalError("type=" + aggregateType);
            }
            break;
        case Aggregate.MIN:
            if (value == null || database.compare(v, value) < 0) {
                value = v;
            }
            break;
        case Aggregate.MAX:
            if (value == null || database.compare(v, value) > 0) {
                value = v;
            }
            break;
        case Aggregate.GROUP_CONCAT: {
            if (list == null) {
                list = New.arrayList();
            }
            list.add(v);
            break;
        }
        case Aggregate.STDDEV_POP:
        case Aggregate.STDDEV_SAMP:
        case Aggregate.VAR_POP:
        case Aggregate.VAR_SAMP: {
            v = v.convertTo(Value.DOUBLE);
            if (value == null) {
                value = v;
            } else {
                // 这4种聚合函数merge的次数不会超过1
                DbException.throwInternalError("type=" + aggregateType);
            }
            break;
        }
        case Aggregate.BOOL_AND:
            v = v.convertTo(Value.BOOLEAN);
            if (value == null) {
                value = v;
            } else {
                value = ValueBoolean.get(value.getBoolean().booleanValue() && v.getBoolean().booleanValue());
            }
            break;
        case Aggregate.BOOL_OR:
            v = v.convertTo(Value.BOOLEAN);
            if (value == null) {
                value = v;
            } else {
                value = ValueBoolean.get(value.getBoolean().booleanValue() || v.getBoolean().booleanValue());
            }
            break;
        default:
            DbException.throwInternalError("type=" + aggregateType);
        }
    }

    ArrayList<Value> getList() {
        return list;
    }

    /**
     * Get the aggregate result.
     *
     * @param database the database
     * @param distinct if distinct is used
     * @return the value
     */
    Value getValue(Database database, boolean distinct) {
        if (distinct) {
            count = 0;
            groupDistinct(database);
        }
        Value v = null;
        switch (aggregateType) {
        case Aggregate.SELECTIVITY: {
            int s = 0;
            if (count == 0) {
                s = 0;
            } else {
                m2 += distinctHashes.size();
                m2 = 100 * m2 / count;
                s = (int) m2;
                s = s <= 0 ? 1 : s > 100 ? 100 : s;
            }
            v = ValueInt.get(s);
            break;
        }
        case Aggregate.COUNT:
        case Aggregate.COUNT_ALL:
            v = ValueLong.get(count);
            break;
        case Aggregate.SUM:
        case Aggregate.MIN:
        case Aggregate.MAX:
        case Aggregate.BOOL_OR:
        case Aggregate.BOOL_AND:
            v = value;
            break;
        case Aggregate.AVG:
            if (value != null) {
                v = divide(value, count);
            }
            break;
        case Aggregate.GROUP_CONCAT:
            return null;
        case Aggregate.STDDEV_POP: {
            if (count < 1) {
                return ValueNull.INSTANCE;
            }
            v = ValueDouble.get(Math.sqrt(m2 / count));
            break;
        }
        case Aggregate.STDDEV_SAMP: {
            if (count < 2) {
                return ValueNull.INSTANCE;
            }
            v = ValueDouble.get(Math.sqrt(m2 / (count - 1)));
            break;
        }
        case Aggregate.VAR_POP: {
            if (count < 1) {
                return ValueNull.INSTANCE;
            }
            v = ValueDouble.get(m2 / count);
            break;
        }
        case Aggregate.VAR_SAMP: {
            if (count < 2) {
                return ValueNull.INSTANCE;
            }
            v = ValueDouble.get(m2 / (count - 1));
            break;
        }
        case Aggregate.HISTOGRAM:
            ValueArray[] values = new ValueArray[distinctValues.size()];
            int i = 0;
            for (Value dv : distinctValues.keys()) {
                AggregateDataOld d = distinctValues.get(dv);
                values[i] = ValueArray.get(new Value[] { dv, ValueLong.get(d.count) });
                i++;
            }
            final CompareMode compareMode = database.getCompareMode();
            Arrays.sort(values, new Comparator<ValueArray>() {
                @Override
                public int compare(ValueArray v1, ValueArray v2) {
                    Value a1 = v1.getList()[0];
                    Value a2 = v2.getList()[0];
                    return a1.compareTo(a2, compareMode);
                }
            });
            v = ValueArray.get(values);
            break;
        default:
            DbException.throwInternalError("type=" + aggregateType);
        }
        return v == null ? ValueNull.INSTANCE : v.convertTo(dataType);
    }

    Value getMergedValue(Database database, boolean distinct) {
        if (distinct) {
            count = 0;
            groupDistinct(database);
        }
        Value v = null;
        switch (aggregateType) {
        case Aggregate.COUNT:
        case Aggregate.COUNT_ALL:
            v = ValueLong.get(count);
            break;
        case Aggregate.SUM:
        case Aggregate.MIN:
        case Aggregate.MAX:
        case Aggregate.BOOL_OR:
        case Aggregate.BOOL_AND:
            v = value;
            break;
        case Aggregate.AVG:
        case Aggregate.STDDEV_POP:
        case Aggregate.STDDEV_SAMP:
        case Aggregate.VAR_POP:
        case Aggregate.VAR_SAMP:
            return value == null ? ValueNull.INSTANCE : value;

        case Aggregate.SELECTIVITY:

            if (value != null) {
                v = divide(value, count);
            }
            break;
        case Aggregate.GROUP_CONCAT:
            return null;

        case Aggregate.HISTOGRAM:
            ValueArray[] values = new ValueArray[distinctValues.size()];
            int i = 0;
            for (Value dv : distinctValues.keys()) {
                AggregateDataOld d = distinctValues.get(dv);
                values[i] = ValueArray.get(new Value[] { dv, ValueLong.get(d.count) });
                i++;
            }
            final CompareMode compareMode = database.getCompareMode();
            Arrays.sort(values, new Comparator<ValueArray>() {
                @Override
                public int compare(ValueArray v1, ValueArray v2) {
                    Value a1 = v1.getList()[0];
                    Value a2 = v2.getList()[0];
                    return a1.compareTo(a2, compareMode);
                }
            });
            v = ValueArray.get(values);
            break;
        default:
            DbException.throwInternalError("type=" + aggregateType);
        }
        return v == null ? ValueNull.INSTANCE : v.convertTo(dataType);
    }

    static Value divide(Value a, long by) {
        if (by == 0) {
            return ValueNull.INSTANCE;
        }
        int type = Value.getHigherOrder(a.getType(), Value.LONG);
        Value b = ValueLong.get(by).convertTo(type);
        a = a.convertTo(type).divide(b);
        return a;
    }

    private void groupDistinct(Database database) {
        if (distinctValues == null) {
            return;
        }
        if (aggregateType == Aggregate.COUNT) {
            count = distinctValues.size();
        } else {
            count = 0;
            for (Value v : distinctValues.keys()) {
                add(database, false, v);
            }
        }
    }
}
