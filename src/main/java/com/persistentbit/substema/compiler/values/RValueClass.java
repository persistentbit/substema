package com.persistentbit.substema.compiler.values;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.utils.BaseValueClass;

/**
 * A RValueClass holds the definition of a Substema Case Class.<br>
 * @since 14/09/16
 * @author Peter Muys
 */
public class RValueClass extends BaseValueClass {
    private final RTypeSig typeSig;
    private final PList<RProperty> properties;
    private final PList<RClass> interfaceClasses;
    private final PList<RAnnotation> annotations;

    public RValueClass(RTypeSig typeSig, PList<RProperty> properties,PList<RClass> interfaceClasses,PList<RAnnotation> annotations) {
        this.typeSig = typeSig;
        this.properties = properties;
        this.interfaceClasses = interfaceClasses;
        this.annotations = annotations;
    }

    public RTypeSig getTypeSig() {
        return typeSig;
    }

    public PList<RProperty> getProperties() {
        return properties;
    }

    public PList<RClass> getInterfaceClasses() {
        return interfaceClasses;
    }

    public PList<RAnnotation> getAnnotations() {
        return annotations;
    }

    public RValueClass withTypeSig(RTypeSig typeSig){
        return copyWith("typeSig",typeSig);
    }

    public RValueClass withAnnotations(PList<RAnnotation> annotations){
        return copyWith("annotations",annotations);
    }

    public RValueClass withProperties(PList<RProperty> properties){
        return copyWith("properties",properties);
    }

    public RValueClass withInterfaceClasses(PList<RClass> interfaceClasses){
        return copyWith("interfaceClasses",interfaceClasses);
    }
}