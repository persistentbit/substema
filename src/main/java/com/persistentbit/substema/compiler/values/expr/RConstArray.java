package com.persistentbit.substema.compiler.values.expr;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.utils.BaseValueClass;

/**
 * @author Peter Muys
 * @since 20/09/2016
 */
public class RConstArray extends BaseValueClass implements RConst {
    private final PList<RConst>  values;


    public RConstArray(PList<RConst> values) {
        this.values = values;
    }

}