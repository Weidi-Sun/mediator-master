package org.fmgroup.mediator.plugin.generator;

import org.fmgroup.mediator.language.RawElement;
import org.fmgroup.mediator.plugin.Plugin;
import org.fmgroup.mediator.plugins.generators.arduino.ArduinoGeneratorException;
import org.fmgroup.mediator.plugins.generators.prism.PrismGeneratorException;

public interface Generator extends Plugin {
    FileSet generate(RawElement elem) throws ArduinoGeneratorException, PrismGeneratorException;

    /**
     * check whether a mediator model can be generated into target code. for example,
     * if a non-arduino model contains pinMode function it cannot be generated
     * @param elem
     * @return
     * @throws ArduinoGeneratorException
     */
    default boolean available(RawElement elem) throws ArduinoGeneratorException {
        return true;
    }

    String getSupportedPlatform();
}
