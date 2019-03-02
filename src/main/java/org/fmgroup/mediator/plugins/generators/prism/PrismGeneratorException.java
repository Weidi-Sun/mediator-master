package org.fmgroup.mediator.plugins.generators.prism;

import org.fmgroup.mediator.language.entity.Entity;
import org.fmgroup.mediator.language.statement.Statement;
import org.fmgroup.mediator.language.term.Term;
import org.fmgroup.mediator.language.type.Type;

public class PrismGeneratorException extends Exception{

    public PrismGeneratorException(String msg) {
        super(msg);
    }

    public static PrismGeneratorException UnhandledType(Type t) {
        PrismGeneratorException ex = new PrismGeneratorException(
                t.toString()
        );
        return ex;
    }

    public static PrismGeneratorException UnhandledTerm(Term t) {
        PrismGeneratorException ex = new PrismGeneratorException(
                t.toString() + " : " + t.getClass().toString()
        );
        return ex;
    }

    public static PrismGeneratorException UnhandledStatement(Statement t) {
        PrismGeneratorException ex = new PrismGeneratorException(
                t.toString() + " : " + t.getClass().toString()
        );
        return ex;
    }

    public static PrismGeneratorException UnclosedEntity(Entity entity) {
        PrismGeneratorException ex = new PrismGeneratorException(
                String.format(
                        "Entity %s is not closed.",
                        entity.getName()
                )
        );
        return ex;
    }

    public static PrismGeneratorException InconsistentPinType(int pinIndex) {
        PrismGeneratorException ex = new PrismGeneratorException(
                String.format(
                        "directions of pin %d are inconsistent.",
                        pinIndex
                )
        );
        return ex;
    }
}
