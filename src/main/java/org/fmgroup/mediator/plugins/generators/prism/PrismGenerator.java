package org.fmgroup.mediator.plugins.generators.prism;
import org.fmgroup.mediator.common.UtilCode;
import org.fmgroup.mediator.plugins.scheduler.Scheduler;
import org.fmgroup.mediator.language.Program;
import org.fmgroup.mediator.language.RawElement;
import org.fmgroup.mediator.language.ValidationException;
import org.fmgroup.mediator.language.entity.Entity;
import org.fmgroup.mediator.language.entity.automaton.Automaton;
import org.fmgroup.mediator.language.entity.automaton.Transition;
import org.fmgroup.mediator.language.entity.automaton.TransitionGroup;
import org.fmgroup.mediator.language.entity.automaton.TransitionSingle;
import org.fmgroup.mediator.language.entity.system.System;
import org.fmgroup.mediator.language.scope.Declaration;
import org.fmgroup.mediator.language.scope.TypeDeclaration;
import org.fmgroup.mediator.language.scope.VariableDeclaration;
import org.fmgroup.mediator.language.statement.AssignmentStatement;
import org.fmgroup.mediator.language.statement.IteStatement;
import org.fmgroup.mediator.language.statement.Statement;
import org.fmgroup.mediator.language.statement.SynchronizingStatement;
import org.fmgroup.mediator.language.term.*;
import org.fmgroup.mediator.language.type.*;
import org.fmgroup.mediator.language.type.termType.*;
import org.fmgroup.mediator.plugin.generator.FileSet;
import org.fmgroup.mediator.plugin.generator.Generator;

import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

enum PrismPinDirection {
    IN,
    OUT
}


public class PrismGenerator implements Generator {
    private Map<Integer, PrismPinDirection> pinStatus = new HashMap<>();

    public String entityGenerate(Entity elem) throws PrismGeneratorException {

        pinStatus = new HashMap<>();

        if (elem instanceof Automaton) {
            try {

                return automatonGenerate((Automaton) elem,typedefGenerate((Program) elem.getParent()));

            } catch (ValidationException e) {
                e.printStackTrace();
            }
        } else if (elem instanceof System) {
            try {
                return entityGenerate(Scheduler.Schedule((System) elem));
            } catch (ValidationException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public FileSet generate(RawElement elem) throws PrismGeneratorException {
        // todo put dependencies
        FileSet files = new FileSet();
        if (elem instanceof Entity) {
            try {
                files.add(((Entity) elem).getName() + ".c", entityGenerate((Entity) elem));

            } catch (FileAlreadyExistsException e) {
                e.printStackTrace();
            }

            return files;
        } else {
            throw new PrismGeneratorException(String.format(
                    "unsupport language element %s",
                    elem.getClass().getName()
            ));
        }
    }

    @Override
    public String getSupportedPlatform() {
        return "prism";
    }


    private Map<String,Type> typedefGenerate(Program p) throws PrismGeneratorException {
        HashMap<String,Type> typedef = new HashMap();
        if(((List) (p.getTypedefs().getDeclarationList())).isEmpty()){
        return null;}
        // TODO only CommandGenerate typedefs used
        for (Declaration typedecl : p.getTypedefs().getDeclarationList()) {

            List<String> aliases = typedecl.getIdentifiers();

            Type rawname = ((TypeDeclaration) typedecl).getType();
            assert typedecl instanceof TypeDeclaration;
            for (String alias :aliases){
                typedef.put(alias, rawname);
            }
        }

        return typedef;
    }
    //We layered the transition and when we meet an iteStatement we go to the next level, the maxlayer tells us that the maximum number of the level
    private int maxlayer = 0;
    //The transitionmark treats an iteStatement as a transition
    private int transitionmark = 0;
    private String globalDeclarations = "";
    private ArrayList<String> variables = new ArrayList<>();
    private String automatonGenerate(Automaton a, Map<String, Type> b) throws PrismGeneratorException, ValidationException {

        if (a.getEntityInterface().getDeclarationList().size() > 0) {
            throw PrismGeneratorException.UnclosedEntity(a);
        }

        String module = "dtmc\n"+"module" + " "+a.getName()+"\n";
        String prog = "";
        for (Declaration var : a.getLocalVars().getDeclarationList()) {

            assert var instanceof VariableDeclaration;

            Type newtype = null;

            if(!(b==null)){

                if (b.containsKey(((VariableDeclaration) var).getType().toString())){
                    newtype = b.get(((VariableDeclaration) var).getType().toString());
                }else{
                    newtype = ((VariableDeclaration) var).getType();
                }
            }else{
                newtype = ((VariableDeclaration) var).getType();
            }
            String strType = typeGenerate(newtype);

            for (String name : var.getIdentifiers()) {

                switch (strType){
                    case "init":
                        Type inibasetype = ((InitType) newtype).getBaseType();

                        String basestrtype = typeGenerate(inibasetype);
                        switch (basestrtype){
                            case "int":
                                globalDeclarations += (name + " : "+typeGenerate(inibasetype)+
                                        String.format(" init %s",((InitType)(newtype)).getInitValue()) +";\n");
                                variables.add(name);
                                break;
                            case "[%s..%s]":
                                BoundedIntType boundedtype =  (BoundedIntType) inibasetype;
                                String upper = boundedtype.getUpperBound().toString();
                                String lower = boundedtype.getLowerBound().toString();
                                globalDeclarations +=(name + ":"+String.format(basestrtype,lower,upper )+
                                        String.format(" init %s",((InitType)(newtype)).getInitValue()) +";\n");
                                variables.add(name);
                                break;
                            case "bool":
                                globalDeclarations +=(name + ":"+strType+
                                        String.format(" init %s",((InitType)(newtype)).getInitValue())+";\n");
                                variables.add(name);
                                break;
                        }
                        break;
                    case "int":
                        globalDeclarations += (name + ":"+strType+";\n");
                        variables.add(name);
                        break;
                    case "[%s..%s]":
                        BoundedIntType boundedtype =  (BoundedIntType) newtype;
                        String upper = boundedtype.getUpperBound().toString();
                        String lower = boundedtype.getLowerBound().toString();
                        globalDeclarations +=(name + ":"+String.format(strType,lower ,upper)+";\n");
                        variables.add(name);
                        break;
                    case "bool":
                        globalDeclarations +=(name + ":"+strType+";\n");
                        variables.add(name);
                        break;
                    case "enum":

                        int i = 0;
                        for(String item : ((EnumType)(newtype)).getItems()){
                            globalDeclarations +=(item+String.format(" : int init %d;", i)+"\n");
                            i++;
                            variables.add(item);
                        }

                        globalDeclarations +=(name +" : int;"+"\n");
                        variables.add(name);
                        break;
                    case "list":
                        Type basetype = ((ListType)(newtype)).getBaseType();
                        Term key = ((ListType)(newtype)).getCapacity();
                        if((!(basetype instanceof IntType))||(!(basetype instanceof BoolType))||(!((key instanceof IntValue)))){
                            java.lang.System.err.println("The type of the list is not supported.");
                        }
                        if((((IntValue)((IntValue) key)).getValue()==0)){
                            java.lang.System.err.println("The length of the list must greater than 0.");
                        }
                        for(int j=0 ; j < (((IntValue)((IntValue) key)).getValue()); j++){
                            globalDeclarations +=(String.format("%s%d",name,j)+" : int;\n");
                            variables.add(String.format("%s%d",name,j));
                        }
                        break;

                }

            }
        }

        // we assume that the automaton has been canonicalized already
        assert a.getTransitions().size() == 1;
        assert a.getTransitions().get(0) instanceof TransitionGroup;

        String transitionExecution = "";

        for (Transition t : ((TransitionGroup) a.getTransitions().get(0)).getTransitions()) {
            assert t instanceof TransitionSingle;
            globalDeclarations += String.format("transitionmark%d :int init 0;\n", transitionmark);
            String guard  = termGenerate(((TransitionSingle) t).getGuard(), 0,b);
            List<Statement> statements = ((TransitionSingle) t).getStatements();
            transitionExecution += transitionGenerate(guard, "", "",statements,0,a,b,"",1, false);

            ++transitionmark;
        }


        String endmodule = "endmodule";

        prog = module + globalDeclarations + transitionExecution + endmodule;

        return prog;
    }


    //oldguard: the previous level's guard
    // newguard:the condition of the IteStatement which written in virtual variables
    //markguard: the label guard of the IteStatement in the previous level
    //statements: the statements of the IteStatement
    //previousmark: the label of the IteStatement in the previous level
    //layer: the level of the current transition
    //Else: if the transition is based on Elsestmt
    private String transitionGenerate( String oldguard,String newguard,String markguard,List<Statement> statements, int previousmark,Automaton a,Map<String, Type> b,String pre ,int layer, boolean Else) throws PrismGeneratorException, ValidationException {
        String transition = "";
        //The lable of the current statement
        int statlable =0;
        //The lable of the current transition
        int layermark = transitionmark;
        String guard = oldguard;
        if(!newguard.isEmpty()){
            guard += (" & " + newguard);
            if(!markguard.isEmpty()){
                guard += ( " & " + markguard);
            }
        }

        String newpre = "v"+pre;
        //The define of the virtual variables used in this level
        String transitionend = pre+"transitionend";
        if(layer > maxlayer) {

            globalDeclarations += pre+"transitionend : bool init true;\n";
            maxlayer = layer;
            for (Declaration var : a.getLocalVars().getDeclarationList()) {
                assert var instanceof VariableDeclaration;
                Type newtype = null;

                if(!(b==null)){

                    if (b.containsKey(((VariableDeclaration) var).getType().toString())){
                        newtype = b.get(((VariableDeclaration) var).getType().toString());
                    }else{
                        newtype = ((VariableDeclaration) var).getType();
                    }
                }else{
                    newtype = ((VariableDeclaration) var).getType();
                }
                String strType = typeGenerate(newtype);

                for (String name : var.getIdentifiers()) {

                    switch (strType){
                        case "init":
                            Type inibasetype = ((InitType) newtype).getBaseType();
                            String basestrtype = typeGenerate(inibasetype);
                            switch (basestrtype){
                                case "int":

                                    globalDeclarations += (newpre + name + " : "+typeGenerate(inibasetype)+
                                            String.format(" init %s",((InitType)(newtype)).getInitValue()) +";\n");
                                    break;
                                case "[%s..%s]":
                                    BoundedIntType boundedtype =  (BoundedIntType) inibasetype;
                                    String upper = boundedtype.getUpperBound().toString();
                                    String lower = boundedtype.getLowerBound().toString();
                                    globalDeclarations +=(newpre + name + ":"+String.format(basestrtype,lower,upper )+
                                            String.format(" init %s",((InitType)(newtype)).getInitValue()) +";\n");
                                    break;
                                case "bool":
                                    globalDeclarations +=(newpre + name + ":"+strType+
                                            String.format(" init %s",((InitType)(newtype)).getInitValue())+";\n");
                                    break;
                            }
                            break;
                        case "int":

                            globalDeclarations += (newpre + name + ":"+strType+";\n");
                            break;
                        case "[%s..%s]":
                            BoundedIntType boundedtype =  (BoundedIntType) newtype;
                            String upper = boundedtype.getUpperBound().toString();
                            String lower = boundedtype.getLowerBound().toString();
                            globalDeclarations +=(newpre + name + ":"+String.format(strType,lower, upper)+";\n");

                            break;
                        case "bool":
                            globalDeclarations +=(newpre + name + ":"+strType+";\n");

                            break;
                        case "enum":
                            int i = 0;
                            for(String item : ((EnumType)(newtype)).getItems()){
                                globalDeclarations +=(newpre + item+String.format(" : int init %d;", i)+"\n");
                                i++;

                            }
                            globalDeclarations +=(newpre + name+" : int;"+"\n");

                            break;
                        case "list":
                            Type basetype = ((ListType)(newtype)).getBaseType();
                            Term key = ((ListType)(newtype)).getCapacity();
                            if((!(basetype instanceof IntType))||(!(basetype instanceof BoolType))||(!((key instanceof IntValue)))){
                                java.lang.System.err.println("The type of the list is not supported.");
                            }
                            if((((IntValue)((IntValue) key)).getValue()==0)){
                                java.lang.System.err.println("The length of the list must greater than 0.");
                            }
                            for(int j=0 ; j < (((IntValue)((IntValue) key)).getValue()); j++){
                                globalDeclarations +=(newpre + String.format("%s%d",name,j)+" : int;\n");

                            }
                            break;
                    }
                }
            }
        }

        // assign the virtual variables
        String rel = "";
        for (String var: variables){
            rel += (  String.format("(%s' = %s)&", newpre + var, pre + var ) );
        }
        rel = (rel.substring(0, rel.length()-1));
        transition += ("[] " +guard +" & "+ String.format("transitionmark%d=%d", layermark,statlable)+ " & " + transitionend +" = true"+ " -> " + rel  +
                "&"+ String.format("(transitionmark%d' =  transitionmark%d + 1)", layermark,layermark)+
                "&"+"("+ transitionend + "' = false);"+ "\n");
        ++statlable;

        for (Statement s : statements) {
            if (s instanceof SynchronizingStatement) {
                java.lang.System.err.println("A sync statement is not supposed to show up when generating codes.");
            } else if (s instanceof AssignmentStatement) {
                    rel = "";

                    rel += virtualtermGenerate(((AssignmentStatement) s).getTarget(), 0,newpre,b) +"'"+
                        " = " + virtualtermGenerate(((AssignmentStatement) s).getExpr(), 0,newpre,b);
                    transition += ("[] " +guard +" & "+ String.format("transitionmark%d=%d", layermark,statlable) + " -> " + "("+ rel +")" +
                                "&"+ String.format("(transitionmark%d' =  transitionmark%d + 1);", layermark,layermark)+ "\n");
                    ++statlable;

            }else if(s instanceof IteStatement){
                    ++transitionmark;
                    globalDeclarations += String.format("transitionmark%d :int init 0;\n", transitionmark);
                    String newguard_ = virtualtermGenerate(((IteStatement) s).getCondition(), 0,newpre,b);
                    String markguard_ = String.format("transitionmark%d=%d", layermark,statlable);
                    List newstatements = ((IteStatement) s).getThenStmts();
                    List elstatements = ((IteStatement) s).getElseStmts();
                    if(elstatements != null){
                        transition += transitionGenerate(guard,newguard_,markguard_,newstatements,layermark,a,b,newpre,layer+1,true);
                        ++transitionmark;
                        globalDeclarations += String.format("transitionmark%d :int init 0;\n", transitionmark);
                        transition += transitionGenerate(guard,"!("+newguard_+")",markguard_,elstatements,layermark,a,b,newpre,layer+1,true);
                        ++statlable;
                    }else{
                        transition += transitionGenerate(guard,newguard_,markguard_,newstatements,layermark,a,b,newpre,layer+1,false);
                        ++statlable;
                    }
            }
             else {
                throw PrismGeneratorException.UnhandledStatement(s);
            }
        }


        int max = 0;
        for(Statement s : statements){
            ++max;
        }
        ++max;


        if(layer > 1){
            //if the IteStatement is not implemented then change the transitionmark to the max
            if(Else == false) {
                transition += ("[] " + oldguard + " & " + "!(" + newguard + ")" + " & " + markguard + " & " + String.format("transitionmark%d=%d", layermark, 0) + " -> " +
                        String.format("(transitionmark%d' =  %d);", layermark, max) + "\n");
            }
            // assign the variables
            rel = "";
            for (String var: variables){
                rel += (  String.format("(%s' = %s)&", pre + var, newpre + var ) );
            }
            rel = (rel.substring(0, rel.length()-1));
            transition += ("[] " +guard +" & "+ String.format("transitionmark%d=%d",layermark, max) + " -> " +  rel  +
                    "&"+ String.format("(transitionmark%d' =  transitionmark%d + 1);", layermark,layermark)+ "\n");
            ++statlable;

            //Prevent label duplication
            transition += ("[] " +markguard+ String.format(" & transitionmark%d=%d", layermark,max+1) + " -> " +
                    String.format("(transitionmark%d' =  transitionmark%d + 1)&", previousmark,previousmark)+
                    String.format("(transitionmark%d' =  transitionmark%d + 1)", layermark,layermark)+
                    "&"+"("+transitionend+"' = true)"+";\n");
        }else{

            // assign the variables
            rel = "";
            for (String var: variables){
                rel += (  String.format("(%s' = %s)&", pre + var, newpre + var ) );
            }
            rel = (rel.substring(0, rel.length()-1));
            transition += ("[] " +guard +" & "+ String.format("transitionmark%d=%d",layermark, max) + " -> " + rel  +
                    "&"+ String.format("(transitionmark%d' =  transitionmark%d + 1)", layermark,layermark)+
                    ";\n");
            ++statlable;

            //initialize all the transitionmarks we had used
            rel = "";
            for(int i = 0 ; i<=transitionmark;i++){
                rel += String.format("(transitionmark%d' =  0)", i)+"&";
            }
            rel = (rel.substring(0,rel.length()-1));
            transition += ("[] " + String.format("transitionmark%d=%d", layermark,max+1)+ " -> " + rel +
                    "&"+"("+transitionend+"' = true)"+";\n");
        }

        return transition;
    }

    private String typeGenerate(Type t) throws PrismGeneratorException {
        if ( t instanceof IntType) {
            return "int";
        }
        if (t instanceof BoundedIntType){
            return "[%s..%s]";
        }
        if (t instanceof InitType) return "init";

        if (t instanceof BoolType) return "bool";

        if (t instanceof EnumType) return "enum";

        if (t instanceof NullType) return "null";

        if (t instanceof ListType) return "list";

        throw PrismGeneratorException.UnhandledType(t);
    }

    private String select(String s) throws PrismGeneratorException{
        switch (s){
            case "==":
                return "=";
            case "&&":
                return "&";
            case "||":
                return "|";
            default:
                return s;
        }
    }

    private String virtualtermGenerate(Term t, int parentPrecedence,String pre, Map<String,Type> b) throws PrismGeneratorException, ValidationException {
        if (t instanceof IntValue) return String.valueOf(((IntValue) t).getValue());
        if (t instanceof NullValue) return "NULL";
        if (t instanceof BoolValue) return ((BoolValue) t).getValue() ? "true" : "false";
        if (t instanceof IdValue) {
            return pre+((IdValue) t).getIdentifier();
        }
        if (t instanceof DoubleValue) {
            return String.valueOf(((DoubleValue) t).getValue());
        }


        if (t instanceof BinaryOperatorTerm) {
            // TODO : brackets
            String binary = "";
            String s = ((BinaryOperatorTerm) t).getOpr().toString();
            String left = virtualtermGenerate(((BinaryOperatorTerm) t).getLeft(), t.getPrecedence(), pre,b);
            String right = virtualtermGenerate(((BinaryOperatorTerm) t).getRight(), t.getPrecedence(), pre,b);
            if((s == "^")||(s == "^^")){
                binary += ("("+left+" & "+"!"+right+")"+"|"+"("+"!"+left+"&"+right+")");
                return binary;
            }else{
                return String.format("%s %s %s", left, select(s), right);
            }
        }
        if (t instanceof FieldTerm) {
            return String.format(
                    "%s.%s",
                    termGenerate(((FieldTerm) t).getOwner(), t.getPrecedence(),b), ((FieldTerm) t).getField()
            );
        }
        if (t instanceof CallTerm) {
            List<String> args = new ArrayList<>();
            for (Term arg : ((CallTerm) t).getArgs()) {
                args.add(termGenerate(arg, 0,b));
            }

            // todo check whether it is a pwm port

            String calleeName = ((CallTerm) t).getCallee().toString();
            Integer pin = null;
            PrismPinDirection pinDirection = null;

            if (calleeName.equals("digitalWrite") || calleeName.equals("analogWrite")) {
                pin = Integer.parseInt(((CallTerm) t).getArg(0).toString());
                pinDirection = PrismPinDirection.OUT;
            } else if (calleeName.equals("digitalRead") || calleeName.equals("analogRead")) {
                pin = Integer.parseInt(((CallTerm) t).getArg(0).toString());
                pinDirection = PrismPinDirection.IN;
            }

            if (pin != null) {
                if (pinStatus.containsKey(pin) && !pinStatus.get(pin).equals(pinDirection)) {
                    throw PrismGeneratorException.InconsistentPinType(pin);
                }

                pinStatus.put(pin, pinDirection);
            }

            return String.format(
                    "%s(%s)",
                    ((CallTerm) t).getCallee().toString(),
                    String.join(", ", args)
            );
        }

        if (t instanceof SingleOperatorTerm) {
            return ((SingleOperatorTerm) t).getOpr() + virtualtermGenerate(((SingleOperatorTerm) t).getTerm(), t.getPrecedence(),pre,b);
        }
        if (t instanceof ElementTerm) {
            return String.format(
                    "%s%s",
                    virtualtermGenerate(((ElementTerm) t).getContainer(), t.getPrecedence(),pre,b),
                    virtualtermGenerate(((ElementTerm) t).getKey(), 0,pre,b)
            );
        }

        if (t instanceof EnumValue) return pre+t.toString();



        if (b.containsKey(t.getType().toString())){
            Type rawType = b.get(t.getType().toString());
            if (rawType instanceof IntValue) return String.valueOf(((IntValue) t).getValue());
            if (rawType instanceof NullValue) return "NULL";
            if (rawType instanceof BoolValue) return ((BoolValue) t).getValue() ? "true" : "false";
            if (rawType instanceof IdValue) {
                return pre+((IdValue) t).getIdentifier();
            }
            if (rawType instanceof DoubleValue) {
                return String.valueOf(((DoubleValue) t).getValue());
            }
            if (rawType instanceof ElementTerm) {
                return String.format(
                        "%s%s",
                        virtualtermGenerate(((ElementTerm) t).getContainer(), t.getPrecedence(),pre,b),
                        virtualtermGenerate(((ElementTerm) t).getKey(), 0,pre,b)
                );
            }

            if (rawType instanceof EnumValue) return "v"+t.toString();
        }



        throw PrismGeneratorException.UnhandledTerm(t);
    }

    private String termGenerate(Term t, int parentPrecedence,Map<String,Type> b) throws PrismGeneratorException, ValidationException {


        if (t instanceof IntValue) return String.valueOf(((IntValue) t).getValue());
        if (t instanceof NullValue) return "NULL";
        if (t instanceof BoolValue) return ((BoolValue) t).getValue() ? "true" : "false";
        if (t instanceof IdValue) {
            return ((IdValue) t).getIdentifier();
        }
        if (t instanceof DoubleValue) {
            return String.valueOf(((DoubleValue) t).getValue());
        }


        if (t instanceof BinaryOperatorTerm) {
            // TODO : brackets
            String binary = "";
            String s = ((BinaryOperatorTerm) t).getOpr().toString();
            String left = termGenerate(((BinaryOperatorTerm) t).getLeft(), t.getPrecedence(),b);
            String right = termGenerate(((BinaryOperatorTerm) t).getRight(), t.getPrecedence(),b);
            if((s == "^")||(s == "^^")){
                binary += ("("+left+" & "+"!"+right+")"+"|"+"("+"!"+left+"&"+right+")");
                return binary;
            }else{
                return String.format("%s %s %s", left, select(s), right);
            }
        }
        if (t instanceof FieldTerm) {
            return String.format(
                    "%s.%s",
                    termGenerate(((FieldTerm) t).getOwner(), t.getPrecedence(),b), ((FieldTerm) t).getField()
            );
        }
        if (t instanceof CallTerm) {
            List<String> args = new ArrayList<>();
            for (Term arg : ((CallTerm) t).getArgs()) {
                args.add(termGenerate(arg, 0,b));
            }

            // todo check whether it is a pwm port

            String calleeName = ((CallTerm) t).getCallee().toString();
            Integer pin = null;
            PrismPinDirection pinDirection = null;

            if (calleeName.equals("digitalWrite") || calleeName.equals("analogWrite")) {
                pin = Integer.parseInt(((CallTerm) t).getArg(0).toString());
                pinDirection = PrismPinDirection.OUT;
            } else if (calleeName.equals("digitalRead") || calleeName.equals("analogRead")) {
                pin = Integer.parseInt(((CallTerm) t).getArg(0).toString());
                pinDirection = PrismPinDirection.IN;
            }

            if (pin != null) {
                if (pinStatus.containsKey(pin) && !pinStatus.get(pin).equals(pinDirection)) {
                    throw PrismGeneratorException.InconsistentPinType(pin);
                }

                pinStatus.put(pin, pinDirection);
            }

            return String.format(
                    "%s(%s)",
                    ((CallTerm) t).getCallee().toString(),
                    String.join(", ", args)
            );
        }

        if (t instanceof SingleOperatorTerm) {
            return ((SingleOperatorTerm) t).getOpr() + "("+termGenerate(((SingleOperatorTerm) t).getTerm(), t.getPrecedence(),b)+")";
        }
        if (t instanceof ElementTerm) {
            return String.format(
                    "%s%s",
                    termGenerate(((ElementTerm) t).getContainer(), t.getPrecedence(),b),
                    termGenerate(((ElementTerm) t).getKey(), 0,b)
            );
        }

        if (t instanceof EnumValue) return t.toString();




        if (b.containsKey(t.getType().toString())){
            Type rawType = b.get(t.getType().toString());
            if (rawType instanceof IntValue) return String.valueOf(((IntValue) t).getValue());
            if (rawType instanceof NullValue) return "NULL";
            if (rawType instanceof BoolValue) return ((BoolValue) t).getValue() ? "true" : "false";
            if (rawType instanceof IdValue) {
                return ((IdValue) t).getIdentifier();
            }
            if (rawType instanceof DoubleValue) {
                return String.valueOf(((DoubleValue) t).getValue());
            }
            if (rawType instanceof ElementTerm) {
                return String.format(
                        "%s%s",
                        termGenerate(((ElementTerm) t).getContainer(), t.getPrecedence(),b),
                        termGenerate(((ElementTerm) t).getKey(), 0,b)
                );
            }

            if (rawType instanceof EnumValue) return t.toString();
        }
        throw PrismGeneratorException.UnhandledTerm(t);
    }



    @Override
    public String getName() {
        return "Prism code generator";
    }

    @Override
    public String getVersion() {
        return "0.0.1";
    }

    @Override
    public String getDescription() {
        return "providing support for Prism code generation";
    }

    @Override
    public List<String> requiredLibraries() {
        List<String> libs = new ArrayList<>();
        libs.add("prism");
        return libs;
    }
}
