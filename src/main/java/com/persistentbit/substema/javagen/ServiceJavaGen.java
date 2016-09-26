package com.persistentbit.substema.javagen;


import com.persistentbit.core.Nullable;
import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PMap;
import com.persistentbit.core.collections.PSet;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.core.sourcegen.SourceGen;
import com.persistentbit.core.tokenizer.Token;
import com.persistentbit.substema.annotations.Remotable;
import com.persistentbit.substema.annotations.RemoteCache;
import com.persistentbit.substema.rod.RServiceValidator;
import com.persistentbit.substema.rod.RodParser;
import com.persistentbit.substema.rod.RodTokenType;
import com.persistentbit.substema.rod.RodTokenizer;
import com.persistentbit.substema.rod.values.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Created by petermuys on 14/09/16.
 */
public class ServiceJavaGen {

    private final JavaGenOptions options;
    private final RSubstema service;
    private PList<GeneratedJava>    generatedJava = PList.empty();

    private final String servicePackageName;

    private ServiceJavaGen(JavaGenOptions options,String packageName,RSubstema service) {
        this.servicePackageName = packageName;
        this.options = options;
        this.service = service;
    }

    static public PList<GeneratedJava>  generate(JavaGenOptions options,String packageName,RSubstema service){
        return new ServiceJavaGen(options,packageName,service).generateService();
    }

    public PList<GeneratedJava> generateService(){
        //RServiceValidator.validate(service);
        PList<GeneratedJava> result = PList.empty();
        result = result.plusAll(service.getEnums().map(e -> new Generator().generateEnum(e)));
        result = result.plusAll(service.getValueClasses().map(vc -> new Generator().generateValueClass(vc)));
        result = result.plusAll(service.getRemoteClasses().map(rc -> new Generator().generateRemoteClass(rc)));
        result = result.plusAll(service.getInterfaceClasses().map(ic -> new Generator().generateInterfaceClass(ic)));
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
            imports.filter(i -> i.getPackageName().equals(servicePackageName) == false).forEach(i -> {
                sg.println("import " + i.getPackageName() + "." + i.getClassName() + ";");
            });
            sg.println("");
            sg.add(this);
            return new GeneratedJava(cls,sg.writeToString());
        }

        public GeneratedJava    generateEnum(REnum e ){
            bs("public enum " + e.name.getClassName());{
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

        public GeneratedJava generateInterfaceClass(RInterfaceClass ic){
            bs("public interface " + ic.getName().getClassName()); {
                //****** GETTERS AND UPDATERS
                ic.getProperties().forEach(p -> {
                    if(options.generateGetters){
                        String rt = toString(p.getValueType().getTypeSig(),p.getValueType().isRequired());
                        String vn = p.getName();
                        if(p.getValueType().isRequired() == false){
                            addImport(Optional.class);
                            rt ="Optional<" + rt + ">";
                            vn = "Optional.ofNullable(" + vn + ")";
                        }
                        println("public " + rt + " get" +firstUpper(p.getName()) + "();");
                    }
                    if(options.generateUpdaters){
                        String s = "public " + ic.getName().getClassName() + " with" + firstUpper(p.getName()) + "("+ toString(p.getValueType().getTypeSig(),p.getValueType().isRequired()) + " " + p.getName() +");";

                        println(s);
                    }
                    if(options.generateGetters || options.generateUpdaters) {
                        println("");
                    }
                });
            }be();
            return toGenJava(ic.getName());
        }

        public GeneratedJava    generateValueClass(RValueClass vc){
            String impl = vc.getInterfaceClasses().isEmpty() ? "" :
                    " implements " + vc.getInterfaceClasses().map(ic -> ic.getClassName()).toString(",");
            bs("public class " + toString(vc.getTypeSig())+ impl);{
                vc.getProperties().forEach(p -> {

                    println(toString(p.getValueType()) + " " + p.getName() + ";");
                });
                println("");
                //***** MAIN CONSTRUCTOR
                bs("public " + vc.getTypeSig().getName().getClassName() + "(" +
                        vc.getProperties().map(p -> toString(p.getValueType().getTypeSig(),p.getValueType().isRequired()) + " " + p.getName() ).toString(", ")
                        +")");{
                    vc.getProperties().forEach(p -> {
                        String fromValue = p.getName();
                        if(p.getValueType().isRequired()){
                            addImport(Objects.class);
                            if(isPrimitive(p.getValueType().getTypeSig()) == false){
                                fromValue = "Objects.requireNonNull(" + p.getName() + ",\"" + p.getName()  + " in " + vc.getTypeSig().getName().getClassName() + " can\'t be null\")";
                            }

                        }
                        else {
                            if(options.generateGetters == false) {
                                fromValue = "Optional.ofNullable(" + fromValue + ")";
                            }
                        }
                        println("this." + p.getName() + " = " + fromValue + ";");
                    });
                }be();
                //****** EXTRA CONSTRUCTORS FOR NULLABLE PROPERTIES
                PList<RProperty> l = vc.getProperties();
                PList<String> nullValues = PList.empty();
                while(l.lastOpt().isPresent() && l.lastOpt().get().getValueType().isRequired() == false){
                    l = l.dropLast();
                    nullValues = nullValues.plus("null");
                    bs("public " + vc.getTypeSig().getName().getClassName() + "(" +
                            l.map(p -> toString(p.getValueType().getTypeSig(),p.getValueType().isRequired()) + " " + p.getName() ).toString(", ")
                            +")");{
                        println("this(" + l.map(p -> p.getName()).plusAll(nullValues).toString(",") + ");");
                    }be();

                }
                //****** GETTERS AND UPDATERS
                vc.getProperties().forEach(p -> {
                    if(options.generateGetters){
                        String rt = toString(p.getValueType().getTypeSig(),p.getValueType().isRequired());
                        String vn = p.getName();
                        if(p.getValueType().isRequired() == false){
                            addImport(Optional.class);
                            rt ="Optional<" + rt + ">";
                            vn = "Optional.ofNullable(" + vn + ")";
                        }
                        println("public " + rt + " get" +firstUpper(p.getName()) + "() { return " + vn + "; }");
                    }
                    if(options.generateUpdaters){
                        String s = "public " + toString(vc.getTypeSig()) + " with" + firstUpper(p.getName()) + "("+ toString(p.getValueType().getTypeSig(),p.getValueType().isRequired()) + " " + p.getName() +") { return new ";
                        s += vc.getTypeSig().getName().getClassName();
                        if(vc.getTypeSig().getGenerics().isEmpty() == false){
                            s += "<>";
                        }
                        s+= "(" + vc.getProperties().map(param -> {
                            return (param.getName().equals(p.getName()) ? "" : "this.") + param.getName();
                        }).toString(", ") + ")";
                        s+= "; }";
                        println(s);
                    }
                    if(options.generateGetters || options.generateUpdaters) {
                        println("");
                    }
                });
                //******* EQUALS
                println("@Override");
                bs("public boolean equals(Object o)");{
                    println("if (this == o) return true;");
                    println("if (o == null || getClass() != o.getClass()) return false;");
                    println("");
                    if(vc.getProperties().isEmpty() == false) {
                        println(vc.getTypeSig().getName().getClassName() + " that = (" + vc.getTypeSig().getName().getClassName() + ")o;");
                        println("");
                    }
                    vc.getProperties().forEach(p -> {
                        String thisVal = p.getName();
                        String thatVal = "that." + thisVal;
                        if(p.getValueType().isRequired()){
                            boolean isPrim = isPrimitive(p.getValueType().getTypeSig());
                            if(isPrim){
                                if(p.getValueType().getTypeSig().getName().equals("float")){
                                    println("if(Float.compare(" + thisVal + "," + thatVal + " != 0) return false;");
                                } else if(p.getValueType().getTypeSig().getName().equals("double")){
                                    println("if(Double.compare(" + thisVal + "," + thatVal + " != 0) return false;");
                                } else {
                                    println("if(" + thisVal + " != " + thatVal + ") return false;");
                                }
                            } else {
                                println("if(!" + thisVal + ".equals(" + thatVal + ")) return false;");
                            }
                        } else {
                            println("if(" + thisVal + "!= null ? !" + thisVal + ".equals(" + thatVal + ") : " + thatVal + " != null) return false;");
                        }
                    });
                    println("return true;");
                }be();
                //******* HASHCODE
                println("@Override");
                bs("public int hashCode()");{
                    if(vc.getProperties().isEmpty()){
                        println("return 0;");
                    } else {
                        println("int result;");
                        vc.getProperties().headMiddleEnd().forEach(t -> {
                            if(t._1 == PStream.HeadMiddleEnd.head || t._1 == PStream.HeadMiddleEnd.headAndEnd){
                                print("result = ");
                            } else {
                                print("result = 31 * result + ");
                            }
                            String value = t._2.getName();
                            String hash = value + ".hashCode()";

                            if(t._2.getValueType().isRequired()) {
                                switch (t._2.getValueType().getTypeSig().getName().getClassName()) {
                                    case "Float":
                                        hash = "Float.hashCode(" + value + ")";
                                        break;
                                    case "Long":
                                        hash = "Long.hashCode(" + value + ")";
                                        break;
                                    case "Double":
                                        hash = "Double.hashCode(" + value + ")";
                                        break;
                                    case "Short":
                                        hash = "Short.hashCode(" + value + ")";
                                        break;
                                    case "Byte":
                                        hash = "Byte.hashCode(" + value + ")";
                                        break;
                                    case "Boolean":
                                        hash = "Boolean.hashCode(" + value + ")";
                                        break;
                                    case "Integer":
                                        hash = "Integer.hashCode(" + value + ")";
                                        break;

                                }

                            } else {
                                hash = "(" + value + " != null ? " + hash + ": 0)";
                            }
                            println(hash + ";");
                        });
                        println("return result;");
                    }

                }be();
            }be();
            return toGenJava(vc.getTypeSig().getName());
        }
        private String toString(RTypeSig sig){
            return toString(sig,false);
        }
        private String toPrimString(RTypeSig sig){
            return toString(sig,true);
        }


        private String toString(RTypeSig sig,boolean asPrimitive){
            String gen = sig.getGenerics().isEmpty() ? "" : sig.getGenerics().map(g -> toString(g)).toString("<",",",">");
            String pname = sig.getName().getPackageName();
            String name = sig.getName().getClassName();

            switch(name){
                case "List": name = "PList"; addImport(PList.class); break;
                case "Set": name = "PSet"; addImport(PSet.class); break;
                case "Map": name= "PMap"; addImport(PMap.class); break;

                case "Boolean": name = asPrimitive ? "boolean" : name; break;
                case "Byte": name = asPrimitive ? "byte" : name; break;
                case "Short": name = asPrimitive ? "short" : name; break;
                case "Integer": name = asPrimitive ? "int" : name; break;
                case "Long": name = asPrimitive ? "long" : name; break;
                case "Float": name = asPrimitive ? "float" : name; break;
                case "Double": name = asPrimitive ? "double" : name; break;

                case "String": break;

                default:
                    addImport(new RClass(pname,name));
                    break;
            }

            return name + gen;
        }

        private boolean isPrimitive(RTypeSig sig){
            return toString(sig,true).equals(toString(sig,false)) == false;
        }

        private String firstUpper(String s){
            return s.substring(0,1).toUpperCase() + s.substring(1);
        }

        private String toString(RValueType vt){
            String res = "";
            String value = vt.isRequired() ? toPrimString(vt.getTypeSig()) : toString(vt.getTypeSig());
            if(vt.isRequired() == false){
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
            addImport(Remotable.class);
            println("@Remotable");
            bs("public interface " + rc.getName().getClassName()); {
                rc.getFunctions().forEach(f -> {
                    String retType;
                    addImport(CompletableFuture.class);
                    if(f.getResultType().isPresent() == false) {
                        retType = "Object";
                    } else {
                        retType = toString(f.getResultType().get().getTypeSig());
                        if(f.getResultType().get().isRequired() == false){
                            retType = "Optional<" + retType + ">";
                            addImport(Optional.class);
                        }
                    }
                    if(f.isCached()){
                        addImport(RemoteCache.class);
                        println("@RemoteCache");
                    }
                    println("CompletableFuture<" + retType + ">\t" + f.getName() + "(" +
                            f.getParams().map( p -> toString(p.getValueType().getTypeSig()) + " " + p.getName()).toString(", ") + ");"
                    );
                });

            }be();

            return toGenJava(rc.getName());
        }

    }




    static public void main(String...args) throws Exception{
        String rodFileName= "com.persistentbit.parser.substema";
        URL url = ServiceJavaGen.class.getResource("/" + rodFileName);
        System.out.println("URL: " + url);
        Path path = Paths.get(url.toURI());
        System.out.println("Path  = " + path);
        String rod = new String(Files.readAllBytes(path));
        RodTokenizer tokenizer = new RodTokenizer();
        PList<Token<RodTokenType>> tokens = tokenizer.tokenize(rodFileName,rod);
        String packageName  = "com.persistentbit.test";
        RodParser parser = new RodParser(packageName,tokens);
        RSubstema service = parser.parseSubstema();
        System.out.println(service);
        PList<GeneratedJava> gen = ServiceJavaGen.generate(new JavaGenOptions(),packageName,service);
        gen.forEach(gj -> {
            System.out.println(gj.code);
            System.out.println("-----------------------------------");
        });
    }
}
