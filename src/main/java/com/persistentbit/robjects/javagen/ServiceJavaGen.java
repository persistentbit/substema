package com.persistentbit.robjects.javagen;


import com.persistentbit.core.Nullable;
import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PSet;
import com.persistentbit.core.sourcegen.SourceGen;
import com.persistentbit.core.tokenizer.Token;
import com.persistentbit.robjects.rod.RodParser;
import com.persistentbit.robjects.rod.RodTokenType;
import com.persistentbit.robjects.rod.RodTokenizer;
import com.persistentbit.robjects.rod.values.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

/**
 * Created by petermuys on 14/09/16.
 */
public class ServiceJavaGen {

    private final JavaGenOptions options;
    private final RService       service;
    private PList<GeneratedJava>    generatedJava = PList.empty();
    //TODO
    private final String servicePackageName = "com.persistentbit.test";

    private ServiceJavaGen(JavaGenOptions options,RService service) {
        this.options = options;
        this.service = service;
    }

    static public PList<GeneratedJava>  generate(JavaGenOptions options,RService service){
        return new ServiceJavaGen(options,service).generateService();
    }

    public PList<GeneratedJava> generateService(){
        PList<GeneratedJava> result = PList.empty();
        result = result.plusAll(service.enums.map(e -> new Generator().generateEnum(e)));
        result = result.plusAll(service.valueClasses.map(vc -> new Generator().generateValueClass(vc)));
        result = result.plusAll(service.remoteClasses.map(rc -> new Generator().generateRemoteClass(rc)));
        return result.filterNulls().plist();
    }

    private class Generator extends SourceGen{
        private PSet<RClass>    imports = PSet.empty();
        private SourceGen       header = new SourceGen();
        private String          packageName;

        public Generator() {
            header.println("// GENERATED CODE: DO NOT CHANGE!");
            header.println("");
        }

        public GeneratedJava    toGenJava(RClass cls){
            SourceGen sg = new SourceGen();
            header.println("package " + servicePackageName + ";");
            header.println("");
            sg.add(header);
            imports.filter(i -> i.packageName.equals(servicePackageName) == false).forEach(i -> {
                sg.println("import " + i.packageName + "." + i.className + ";");
            });
            sg.println("");
            sg.add(this);
            return new GeneratedJava(cls,sg.writeToString());
        }

        public GeneratedJava    generateEnum(REnum e ){
            bs("public enum " + e.name);{
                println(e.values.toString(","));
            }be();
            return toGenJava(e.name);
        }
        private void addImport(RClass cls){
            imports = imports.plus(cls);
        }
        private void addImport(Class<?> cls){
            addImport(new RClass(cls.getPackage().getName(),cls.getSimpleName()));
        }

        public GeneratedJava    generateValueClass(RValueClass vc){
            bs("public class " + toString(vc.typeSig));{
                vc.properties.forEach(p -> {

                    println(toString(p.valueType) + " " + p.name + ";");
                });
                println("");
                //***** MAIN CONSTRUCTOR
                bs("public " + vc.typeSig.name.className + "(" +
                        vc.properties.map(p -> toString(p.valueType.typeSig) + " " + p.name ).toString(", ")
                        +")");{
                    vc.properties.forEach(p -> {
                        String fromValue = p.name;
                        if(p.valueType.required){
                            addImport(Objects.class);
                            fromValue = "Objects.requireNonNull(" + p.name + ",\"" + p.name  + " in " + vc.typeSig.name.className + " can\'t be null\")";
                        }
                        else {
                            if(options.generateGetters == false) {
                                fromValue = "Optional.ofNullable(" + fromValue + ")";
                            }
                        }
                        println("this." + p.name + " = " + fromValue + ";");
                    });
                }be();
                //****** EXTRA CONSTRUCTORS FOR NULLABLE PROPERTIES
                PList<RProperty> l = vc.properties;
                PList<String> nullValues = PList.empty();
                while(l.lastOpt().isPresent() && l.lastOpt().get().valueType.required == false){
                    l = l.dropLast();
                    nullValues = nullValues.plus("null");
                    bs("public " + vc.typeSig.name.className + "(" +
                            l.map(p -> toString(p.valueType.typeSig) + " " + p.name ).toString(", ")
                            +")");{
                        println("this(" + l.map(p -> p.name).plusAll(nullValues).toString(",") + ");");
                    }be();

                }
                //****** GETTERS AND UPDATERS
                vc.properties.forEach(p -> {
                    if(options.generateGetters){
                        String rt = toString(p.valueType.typeSig);
                        String vn = p.name;
                        if(p.valueType.required == false){
                            addImport(Optional.class);
                            rt ="Optional<" + rt + ">";
                            vn = "Optional.ofNullable(" + vn + ")";
                        }
                        println("public " + rt + " get" +firstUpper(p.name) + "() { return " + vn + "; }");
                    }
                    if(options.generateUpdaters){
                        String s = "public " + toString(vc.typeSig) + " with" + firstUpper(p.name) + "("+ toString(p.valueType.typeSig) + " " + p.name +") { return new ";
                        s += vc.typeSig.name.className;
                        if(vc.typeSig.generics.isEmpty()){
                            s += "<>";
                        }
                        s+= "(" + vc.properties.map(param -> {
                            return (param.name.equals(p.name) ? "" : "this.") + param.name;
                        }).toString(", ") + ")";
                        s+= "; }";
                        println(s);
                    }
                    println("");
                });

            }be();
            return toGenJava(vc.typeSig.name);
        }
        private String toString(RTypeSig sig){
            String gen = sig.generics.isEmpty() ? "" : sig.generics.map(g -> toString(g)).toString("<",",",">");
            String pname = sig.name.packageName;
            String name = sig.name.className;

            switch(name){
                case "Array": name = "PList"; addImport(PList.class); break;
                case "Set": name = "PSet"; addImport(PSet.class); break;
                default:
                    addImport(new RClass(pname,name));
                    break;
            }

            return name + gen;
        }

        private String firstUpper(String s){
            return s.substring(0,1).toUpperCase() + s.substring(1);
        }

        private String toString(RValueType vt){
            String res = "";
            String value = toString(vt.typeSig);
            if(vt.required == false){
                addImport(Nullable.class);

                if(options.generateGetters == false){
                    addImport(Optional.class);
                    value = "Optional<" + value + ">";
                } else {
                    res += "@Nullable ";
                }
            }
            String access = options.generateGetters ? "private" : "public";

            return res + access + " final " + value;

        }

        public GeneratedJava    generateRemoteClass(RRemoteClass rc){
            return null;
        }

    }




    static public void main(String...args) throws Exception{
        String rodFileName= "com.persistentbit.robjects_rodparser_1.0.0.rod";
        URL url = ServiceJavaGen.class.getResource("/" + rodFileName);
        System.out.println("URL: " + url);
        Path path = Paths.get(url.toURI());
        System.out.println("Path  = " + path);
        String rod = new String(Files.readAllBytes(path));
        RodTokenizer tokenizer = new RodTokenizer();
        PList<Token<RodTokenType>> tokens = tokenizer.tokenize(rodFileName,rod);
        RodParser parser = new RodParser("com.persistentbit.test",tokens);
        RService service = parser.parseService();
        System.out.println(service);
        PList<GeneratedJava> gen = ServiceJavaGen.generate(new JavaGenOptions(),service);
        gen.forEach(gj -> {
            System.out.println(gj.code);
            System.out.println("-----------------------------------");
        });
    }
}
