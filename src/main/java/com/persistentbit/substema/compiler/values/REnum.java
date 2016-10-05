package com.persistentbit.substema.compiler.values;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.utils.BaseValueClass;

/**
 * Created by petermuys on 14/09/16.
 */
public class REnum extends BaseValueClass {
    private final RClass name;
    private final PList<String> values;

    public REnum(RClass name, PList<String> values) {
        this.name = name;
        this.values = values;
    }

    public RClass getName() {
        return name;
    }

    public PList<String> getValues() {
        return values;
    }
}