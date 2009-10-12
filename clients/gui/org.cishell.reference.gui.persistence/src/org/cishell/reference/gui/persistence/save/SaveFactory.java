package org.cishell.reference.gui.persistence.save;

import java.util.Dictionary;

import org.cishell.framework.CIShellContext;
import org.cishell.framework.LocalCIShellContext;
import org.cishell.framework.algorithm.Algorithm;
import org.cishell.framework.algorithm.AlgorithmFactory;
import org.cishell.framework.data.Data;
import org.osgi.service.component.ComponentContext;

/**
 * Create a Save object
 * 
 * TODO: Should also support if we can convert to file, but have
 * no final file:X->file-ext:* converter.
 *
 */
public class SaveFactory implements AlgorithmFactory {
    private CIShellContext ciShellContext;	

    /**
     * Create a local CIShell context
     * @param componentContext The current CIShell context
     */
    protected void activate(ComponentContext componentContext) {
        ciShellContext =
        	new LocalCIShellContext(componentContext.getBundleContext());
    }

    /**
     * Create a Save algorithm
     * @param data The data objects to save
     * @param parameters The parameters for the algorithm
     * @param ciShellContext Reference to services provided by CIShell
     * @return An instance of the Save algorithm
     */
    public Algorithm createAlgorithm(Data[] data,
    								 Dictionary parameters,
    								 CIShellContext ciShellContext) {
        this.ciShellContext = ciShellContext;
        return new Save(data, parameters, ciShellContext);
    }
}