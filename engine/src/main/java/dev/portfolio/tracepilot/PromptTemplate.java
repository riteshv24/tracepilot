package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;
import java.util.regex.*;

/** Deliberately not a programming-language template engine. Two bounded substitutions only. */
public final class PromptTemplate {
    private static final Pattern VARIABLE=Pattern.compile("\\{\\{\\s*([a-zA-Z_]+)\\s*}}");
    private PromptTemplate() {
    }
    public static String validate(String template) {
        Checks.text(template, "template", 8000);
        Matcher matcher=VARIABLE.matcher(template);
        boolean question=false;
        while(matcher.find()) {
            String name=matcher.group(1);
            if(!Set.of("question", "context").contains(name))throw Problem.invalid("Unsupported prompt placeholder: "+name);
            if(name.equals("question"))question=true;
        }
        if(!question)throw Problem.invalid("Prompt must contain {{question}}");
        return template;
    }
    public static String render(String template, CaseSpec item) {
        validate(template);
        Matcher matcher=VARIABLE.matcher(template);
        StringBuilder out=new StringBuilder();
        while(matcher.find())matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1).equals("question")?item.question():item.context()));
        matcher.appendTail(out);
        return out.toString();
    }
}
