/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.csa.rstb.classification.rcp.wizards.PolarimetryWizard;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.engine_utilities.gpf.InputProductValidator;
import org.esa.snap.graphbuilder.rcp.wizards.AbstractInputPanel;
import org.esa.snap.graphbuilder.rcp.wizards.WizardPanel;

/**
 * Input Panel
 */
public class PolSARWizardInputPanel extends AbstractInputPanel {

    public PolSARWizardInputPanel() {
    }

    public WizardPanel getNextPanel() {

        return new PolSARWizardProcessPanel(sourcePanel.getSelectedSourceProduct());
    }

    public boolean validateInput() {
        if (!super.validateInput()) return false;

        try {
            final Product product = sourcePanel.getSelectedSourceProduct();
            final InputProductValidator validator = new InputProductValidator(product);
            validator.checkIfQuadPolSLC();
        } catch (Exception e){
            showErrorMsg("Invalid input product: "+e.getMessage());
            return false;
        }
        return true;
    }

    protected String getInstructions() {
        return "Select a RADARSAT-2, TerraSAR-X, or ALOS Quad Pol SLC product";
    }
}
