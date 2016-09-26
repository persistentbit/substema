package com.persistentbit.substema.rod;

import com.persistentbit.core.tokenizer.SimpleTokenizer;
import com.persistentbit.core.tokenizer.TokenFound;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.persistentbit.substema.rod.RodTokenType.*;

/**
 * Created by petermuys on 12/09/16.
 */
public class RodTokenizer extends SimpleTokenizer<RodTokenType>{

    public RodTokenizer(){
        add(regExMatcher("/\\*.*\\*/",tComment).ignore());
        add(regExMatcher("\\n",tNl).ignore());
        add("\\(",tOpen);
        add("\\)",tClose);
        add("\\.",tPoint);
        add("<",tGenStart);
        add(">",tGenEnd);
        add("\\,",tComma);
        add("\\?",tQuestion);
        add("\\:",tColon);
        add("\\;",tSemiColon);
        add("\\{",tBlockStart);
        add("\\}",tBlockEnd);
        add("\\=",tAssign);
        add("\\-\\>",tMapMap);
        add("\\[",tArrayStart);
        add("\\]",tArrayEnd);
        add("[0-9]+\\.?[0-9]*",tNumber);
        add(RodTokenizer.stringMatcher(tString,'\'',false));
        add(RodTokenizer.stringMatcher(tString,'\"',false));
        add(RodTokenizer.stringMatcher(tString,'`',true));
        add(SimpleTokenizer.regExMatcher("[a-zA-Z_][a-zA-Z0-9_]*",tIdentifier).map(found -> {
            switch (found.text){
                case "package": return new TokenFound<>(found.text,tPackage,found.ignore);
                case "from": return new TokenFound<>(found.text,tFrom,found.ignore);
                case "class":return new TokenFound<>(found.text,tClass,found.ignore);
                case "import":return new TokenFound<>(found.text,tImport,found.ignore);
                case "cached":return new TokenFound<>(found.text,tCached,found.ignore);
                case "enum":return new TokenFound<>(found.text,tEnum,found.ignore);
                case "value" : return new TokenFound<>(found.text,tValue,found.ignore);
                case "remote": return new TokenFound<>(found.text,tRemote,found.ignore);
                case "void": return new TokenFound<>(found.text,tVoid,found.ignore);
                case "exception": return new TokenFound<>(found.text,tException,found.ignore);
                case "throws": return new TokenFound<>(found.text,tThrows,found.ignore);
                case "implements": return new TokenFound<>(found.text,tImplements,found.ignore);
                case "interface": return new TokenFound<>(found.text,tInterface,found.ignore);
                case "true": return new TokenFound<>(found.text,tTrue,found.ignore);
                case "false": return new TokenFound<>(found.text,tFalse,found.ignore);
                case "null": return new TokenFound<>(found.text,tNull,found.ignore);
                case "new": return new TokenFound<>(found.text,tNew,found.ignore);
                default: return found;
            }
        }));
        add(RodTokenizer.regExMatcher("\\s+",tWhiteSpace).ignore());

    }

    static public void main(String...args){
        try{
            RodTokenizer tokenizer = new RodTokenizer();
            String txt = new String(Files.readAllBytes(Paths.get(RodTokenizer.class.getResource("/app.rod").toURI())));
            System.out.println(txt);
            tokenizer.tokenize("app.rod",txt).forEach(System.out::println);

        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}