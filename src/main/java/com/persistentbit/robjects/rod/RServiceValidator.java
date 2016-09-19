package com.persistentbit.robjects.rod;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PSet;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.robjects.rod.values.*;

/**
 * Created by petermuys on 17/09/16.
 */
public class RServiceValidator {
    private final RService  service;

    private RServiceValidator(RService service) {
        this.service = service;
    }

    private void validate() {
        checkClassesDefined();
        checkOverloading();
    }

    private void checkOverloading() {
        PList<RClass> dup =service.remoteClasses.map(rc -> rc.name)
                .plusAll(service.valueClasses.map(vc->vc.typeSig.name))
                .plusAll(service.enums.map(e -> e.name))
                .duplicates();
        if(dup.isEmpty() == false){
            throw new RServiceException("Duplicated type definitions: " + dup.map(c -> c.packageName +"." + c.className).toString(", "));
        }
        service.remoteClasses.forEach(rc -> checkOverloading(rc));
        service.valueClasses.forEach(vc -> checkOverloading(vc));
        service.enums.forEach(e -> checkOverloading(e));
    }
    private void checkOverloading(RRemoteClass rc){
        PList<String> dupFunNames = rc.functions.map(f -> f.name).duplicates();
        PList<String> wrong = dupFunNames.filter(n -> rc.functions.filter(f -> f.name.equals(n)).map(f2-> f2.params.size()).duplicates().isEmpty() == false);
        if(wrong.isEmpty() == false){
            throw new RServiceException("Remote class " + rc.name.className + " has duplicated functions with the same parameter count: " + wrong.toString(", "));
        }
        rc.functions.forEach(f -> checkOverloading(rc,f));
    }
    private void checkOverloading(RRemoteClass rc,RFunction f){
        PList<String> dup = f.params.map(p -> p.name).duplicates();
        if(dup.isEmpty() == false){
            throw new RServiceException("Remote class " + rc.name.className + " function " + f.name + " has duplicated parameters");
        }
    }
    private void checkOverloading(REnum e){
        PList<String> dup = e.values.duplicates();
        if(dup.isEmpty() == false){
            throw new RServiceException("enum " + e.name.className + " has duplicated values: " + dup.toString(", "));
        }
    }


    private void checkOverloading(RValueClass vc){
        PStream<String> dup = vc.typeSig.generics.map(sig -> sig.name.className).duplicates();
        if(dup.isEmpty() == false){
            throw new RServiceException("value class " + vc.typeSig.name.className + " has duplicated Generics parameters: " + dup.toString(", "));
        }
        dup = vc.properties.map(p->p.name).duplicates();
        if(dup.isEmpty() == false){
            throw new RServiceException("value class " + vc.typeSig.name.className + " has duplicated property names: " + dup.toString(", "));
        }

    }



    private void checkClassesDefined(){
        PSet<RClass>   needed   =   PSet.empty();
        PSet<RClass>   defined  =   PSet.empty();
        needed = needed.plusAll(service.valueClasses.map(vc -> needed(vc)).flatten());
        needed = needed.plusAll(service.remoteClasses.map(rc -> needed(rc)).flatten());
        defined = defined.plusAll(service.enums.map(e -> e.name));
        defined = defined.plusAll(service.valueClasses.map(vc -> vc.typeSig.name));
        defined = defined.plusAll(service.remoteClasses.map(rc -> rc.name));
        PSet<RClass> buildIn = PSet.empty();
        buildIn = buildIn.plusAll(PSet.val("Byte","Short","Integer","Long","Float","Double","String","Boolean","List","Map","Set").map(n -> new RClass(service.packageName,n)));
        PSet<RClass> all = defined.plusAll(buildIn);
        PSet<RClass> undef = needed.filter(c -> all.contains(c) == false);
        if(undef.isEmpty() == false){
            throw new RServiceException("Following types are Undefined: " + undef.map(r -> r.packageName + "." + r.className).toString(", "));
        }
    }




    private PSet<RClass>   needed(RRemoteClass rc){
        PSet<RClass> res = PSet.empty();
        res = res.plusAll(rc.functions.map(f -> needed(f)).flatten());
        return res;
    }
    private PSet<RClass> needed(RFunction f){
        PSet<RClass> res = PSet.empty();
        if(f.resultType != null){
            res = res.plusAll(needed(f.resultType.typeSig));
        }
        PStream<RClass> ap =f.params.map(p -> needed(p.valueType.typeSig)).flatten();
        return res.plusAll(ap);
    }



    private PSet<RClass>   needed(RValueClass vc){
        PSet<RClass> res = PSet.empty();
        PSet<String> genNames = vc.typeSig.generics.map(sig -> sig.name.className).pset();
        return res.plusAll(vc.properties.map(p -> needed(p.valueType.typeSig)).flatten()).filter(c -> genNames.contains(c.className) == false);
    }

    private PSet<RClass> needed(RTypeSig sig){
        PSet<RClass> res = PSet.empty();
        res = res.plus(sig.name);
        return res.plusAll(sig.generics.map(g -> needed(g)).flatten());
    }

    static public RService  validate(RService service){
        new RServiceValidator(service).validate();
        return service;
    }
}